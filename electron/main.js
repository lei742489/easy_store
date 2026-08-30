const { app, BrowserWindow, Menu, dialog, screen } = require('electron');
const exec = require('child_process').exec;
const { spawn } = require('child_process');
const fs = require('fs');
const http = require('http');
const https = require('https');
const path = require('path');
const { registerWindowEvents } = require('./ipcHandlers/windowHandlers');

let win;
let isQuitting = false;
let backendProcess = null;
const MIN_WIDTH = 1100;
const MIN_HEIGHT = 700;
const DEFAULT_WIDTH = 1600;
const DEFAULT_HEIGHT = 1024;
const APP_CONFIG_PATH = path.join(__dirname, 'config', 'app-config.json');
const WEB_VERSION_CACHE_FILE = 'web-version.json';

function getRuntimeRoot() {
    return app.isPackaged ? path.dirname(process.execPath) : __dirname;
}

function readAppConfig() {
    const defaultConfig = {
        appMode: 'single',
        backupOnExit: true,
        sqlitePath: '',
        backendRunDir: app.isPackaged ? 'boot' : '../easy_store_boot',
        backendConfigPath: app.isPackaged ? 'boot/application-online.yml' : '../easy_store_boot/src/main/resources/application-dev.yml',
        backendJarName: 'easy_store_boot.jar',
        backendStartupTimeout: 30000,
        webUrl: 'http://es.njhy6920.cn/',
        webVersionUrl: '',
        webDir: app.isPackaged ? 'web' : '',
        backupDir: app.isPackaged ? 'backup/sqlite' : '../backup/sqlite',
        backupKeepLatest: 10,
    };

    try {
        const runtimeConfigPath = path.join(getRuntimeRoot(), 'config', 'app-config.json');
        const configPath = fs.existsSync(runtimeConfigPath) ? runtimeConfigPath : APP_CONFIG_PATH;
        if (!fs.existsSync(configPath)) {
            return defaultConfig;
        }
        return {
            ...defaultConfig,
            ...JSON.parse(fs.readFileSync(configPath, 'utf8').replace(/^\uFEFF/, '')),
        };
    } catch (error) {
        return defaultConfig;
    }
}

function resolveAppPath(value) {
    if (!value) {
        return '';
    }
    return path.isAbsolute(value) ? value : path.resolve(getRuntimeRoot(), value);
}

function getBundledAppPath(...parts) {
    const unpackedPath = app.isPackaged ? path.join(process.resourcesPath, 'app', ...parts) : '';
    if (unpackedPath && fs.existsSync(unpackedPath)) {
        return unpackedPath;
    }
    return path.join(__dirname, ...parts);
}

function shouldBackupOnExit(config) {
    return config.backupOnExit !== false && String(config.appMode || '').toLowerCase() === 'single';
}

function shouldStartLocalBackend(config) {
    return app.isPackaged && String(config.appMode || '').toLowerCase() === 'single';
}

function getBackendJarPath(config) {
    return path.join(resolveAppPath(config.backendRunDir), config.backendJarName || 'easy_store_boot.jar');
}

function startBackendIfNeeded() {
    const config = readAppConfig();
    if (!shouldStartLocalBackend(config)) {
        return;
    }

    const backendRunDir = resolveAppPath(config.backendRunDir);
    const backendJarPath = getBackendJarPath(config);
    const backendConfigPath = resolveAppPath(config.backendConfigPath);
    if (!fs.existsSync(backendJarPath)) {
        dialog.showErrorBox('启动失败', `未找到后台程序：${backendJarPath}`);
        return;
    }

    backendProcess = spawn('java', [
        '-jar',
        backendJarPath,
        '--spring.profiles.active=online',
        `--spring.config.additional-location=file:${backendConfigPath}`,
    ], {
        cwd: backendRunDir,
        windowsHide: true,
        stdio: 'ignore',
    });

    backendProcess.on('exit', () => {
        backendProcess = null;
    });
}

function stopBackendIfNeeded() {
    return new Promise((resolve) => {
        if (!backendProcess) {
            resolve();
            return;
        }

        const child = backendProcess;
        backendProcess = null;
        const timeout = setTimeout(resolve, 3000);
        child.once('exit', () => {
            clearTimeout(timeout);
            resolve();
        });
        child.kill();
    });
}

function getWebEntry(config) {
    if (!app.isPackaged) {
        return '';
    }
    return path.join(resolveAppPath(config.webDir || 'web'), 'index.html');
}

function getRemoteWebUrl(config) {
   return config.webUrl || 'http://es.njhy6920.cn/';
   // return config.webUrl || 'http://localhost:8185/';
}

function getRemoteWebVersionUrl(config) {
    if (config.webVersionUrl) {
        return config.webVersionUrl;
    }
    return new URL('version.json', getRemoteWebUrl(config)).toString();
}

function getWebVersionCachePath() {
    return path.join(app.getPath('userData'), WEB_VERSION_CACHE_FILE);
}

function readCachedWebVersion() {
    try {
        const filePath = getWebVersionCachePath();
        if (!fs.existsSync(filePath)) {
            return '';
        }
        const data = JSON.parse(fs.readFileSync(filePath, 'utf8'));
        return String(data.version || '');
    } catch {
        return '';
    }
}

function writeCachedWebVersion(version) {
    const filePath = getWebVersionCachePath();
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    fs.writeFileSync(
        filePath,
        JSON.stringify({ version, checkedAt: new Date().toISOString() }, null, 2),
        'utf8'
    );
}

function requestText(url) {
    return new Promise((resolve, reject) => {
        const client = url.startsWith('https:') ? https : http;
        const request = client.get(
            url,
            {
                headers: {
                    'Cache-Control': 'no-cache',
                    Pragma: 'no-cache',
                },
            },
            (response) => {
                if (
                    response.statusCode >= 300 &&
                    response.statusCode < 400 &&
                    response.headers.location
                ) {
                    response.resume();
                    resolve(requestText(new URL(response.headers.location, url).toString()));
                    return;
                }
                if (response.statusCode < 200 || response.statusCode >= 300) {
                    response.resume();
                    reject(new Error(`Version request failed: ${response.statusCode}`));
                    return;
                }
                let body = '';
                response.setEncoding('utf8');
                response.on('data', (chunk) => {
                    body += chunk;
                });
                response.on('end', () => resolve(body));
            }
        );
        request.setTimeout(5000, () => {
            request.destroy(new Error('Version request timeout'));
        });
        request.on('error', reject);
    });
}

function parseRemoteWebVersion(content) {
    const text = String(content || '').trim().replace(/^\uFEFF/, '');
    if (!text) {
        return '';
    }
    try {
        const data = JSON.parse(text);
        return String(data.version || data.appVersion || data.VITE_APP_VERSION || '').trim();
    } catch {
        return text;
    }
}

function appendUrlQuery(url, key, value) {
    if (!value) {
        return url;
    }
    const targetUrl = new URL(url);
    targetUrl.searchParams.set(key, value);
    return targetUrl.toString();
}

async function clearWebCacheIfVersionChanged(win, config) {
    try {
        const versionContent = await requestText(getRemoteWebVersionUrl(config));
        const remoteVersion = parseRemoteWebVersion(versionContent);
        if (!remoteVersion) {
            return '';
        }
        const cachedVersion = readCachedWebVersion();
        if (!cachedVersion || cachedVersion !== remoteVersion) {
            await win.webContents.session.clearCache();
        }
        if (cachedVersion !== remoteVersion) {
            writeCachedWebVersion(remoteVersion);
        }
        return remoteVersion;
    } catch {
        // 版本检测失败不应影响主页面启动。
        return '';
    }
}

function backupSqliteOnExit() {
    return new Promise((resolve) => {
        const config = readAppConfig();
        if (!shouldBackupOnExit(config)) {
            resolve({ skipped: true });
            return;
        }

        const scriptPath = getBundledAppPath('scripts', 'backup-sqlite.ps1');
        const child = spawn('powershell.exe', [
            '-NoProfile',
            '-ExecutionPolicy',
            'Bypass',
            '-File',
            scriptPath,
            '-SqlitePath',
            resolveAppPath(config.sqlitePath),
            '-RunDir',
            resolveAppPath(config.backendRunDir),
            '-BackendConfigPath',
            resolveAppPath(config.backendConfigPath),
            '-BackupDir',
            resolveAppPath(config.backupDir),
            '-KeepLatest',
            String(Number(config.backupKeepLatest) || 10),
        ], {
            windowsHide: true,
        });

        let stdout = '';
        let stderr = '';
        child.stdout.on('data', (data) => {
            stdout += data.toString('utf8');
        });
        child.stderr.on('data', (data) => {
            stderr += data.toString('utf8');
        });
        child.on('error', () => resolve({ skipped: true, failed: true }));
        child.on('close', (code) => {
            if (code === 0) {
                resolve({ skipped: false, backupPath: stdout.trim() });
                return;
            }
            resolve({ skipped: true, failed: true });
        });
    });
}

function getWindowSize(preferredWidth, preferredHeight) {
    const { width: workWidth, height: workHeight } = screen.getPrimaryDisplay().workAreaSize;
    const minWidth = Math.min(MIN_WIDTH, workWidth);
    const minHeight = Math.min(MIN_HEIGHT, workHeight);
    const width = Math.min(Math.max(preferredWidth, minWidth), workWidth);
    const height = Math.min(Math.max(preferredHeight, minHeight), workHeight);
    return {
        width,
        height,
        minWidth,
        minHeight,
        maximize: preferredWidth >= workWidth || preferredHeight >= workHeight,
    };
}

async function createWindow() {
    const config = readAppConfig();
    const windowSize = getWindowSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    const electronSessionId = `${process.pid}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    win = new BrowserWindow({
        width: windowSize.width,
        height: windowSize.height,
        minWidth: windowSize.minWidth,
        minHeight: windowSize.minHeight,
        icon: getBundledAppPath('assets', 'icon.png'),
        resizable: true,
        frame: false,
        maximizable: true,
        show: false,
        webPreferences: {
            preload: getBundledAppPath('preload.js'),
            nodeIntegration: false,
            contextIsolation: true,
            additionalArguments: [`--easy-store-session-id=${electronSessionId}`],
        }
    });

    const remoteWebVersion = await clearWebCacheIfVersionChanged(win, config);

    if (app.isPackaged) {
        //win.loadFile(getWebEntry(config));
        win.loadURL(appendUrlQuery(getRemoteWebUrl(config), 'v', remoteWebVersion));
    } else {
        win.loadURL('http://localhost:8185/');
       // win.loadURL('http://es.njhy6920.cn/');
    }

    Menu.setApplicationMenu(null);

    win.once('ready-to-show', () => {
        if (windowSize.maximize) {
            win.maximize();
        }
        win.show();
    });

    if (!app.isPackaged) {
      // win.webContents.openDevTools();
    }

    win.on('close', (e) => {
        const choice = dialog.showMessageBoxSync(win, {
            type: 'question',
            buttons: ['取消', '退出'],
            defaultId: 1,
            cancelId: 0,
            title: '确认',
            message: '你确定要退出应用吗？',
        });

        if (choice === 0) {
            e.preventDefault();
        }
    });

    win.removeAllListeners('close');
    win.on('close', async (e) => {
        if (isQuitting) {
            return;
        }

        const choice = dialog.showMessageBoxSync(win, {
            type: 'question',
            buttons: ['取消', '退出'],
            defaultId: 1,
            cancelId: 0,
            title: '确认',
            message: '你确定要退出应用吗？',
        });

        if (choice === 0) {
            e.preventDefault();
            return;
        }

        e.preventDefault();

        try {
            await stopBackendIfNeeded();
            await backupSqliteOnExit();
            isQuitting = true;
            win.close();
        } catch {
            isQuitting = true;
            win.close();
        }
    });

    registerWindowEvents(win);
}


app.whenReady().then(() => {
    // 启动 Spring Boot 后台（假设已经编译成 myapp.jar）
    /*exec('java -jar myapp.jar', (error, stdout, stderr) => {
        if (error) {
            console.error(`Spring Boot 启动失败: ${error}`);
        }
    });*/

    startBackendIfNeeded();
    createWindow();
});
