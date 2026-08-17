const { app, BrowserWindow, Menu, dialog, screen } = require('electron');
const exec = require('child_process').exec;
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const { registerWindowEvents } = require('./ipcHandlers/windowHandlers');

let win;
let isQuitting = false;
let backendProcess = null;
const MIN_WIDTH = 1280;
const MIN_HEIGHT = 800;
const DEFAULT_WIDTH = 1440;
const DEFAULT_HEIGHT = 900;
const APP_CONFIG_PATH = path.join(__dirname, 'config', 'app-config.json');

function getRuntimeRoot() {
    return app.isPackaged ? path.dirname(process.execPath) : __dirname;
}

function readAppConfig() {
    const defaultConfig = {
        appMode: 'single',
        backupOnExit: true,
        sqlitePath: '',
        backendRunDir: app.isPackaged ? 'boot' : '../easy_store_boot',
        backendConfigPath: app.isPackaged ? 'boot/application-dev.yml' : '../easy_store_boot/src/main/resources/application-dev.yml',
        backendJarName: 'easy_store_boot.jar',
        backendStartupTimeout: 30000,
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
            ...JSON.parse(fs.readFileSync(configPath, 'utf8')),
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
        '--spring.profiles.active=dev',
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

function backupSqliteOnExit() {
    return new Promise((resolve, reject) => {
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
        child.on('error', reject);
        child.on('close', (code) => {
            if (code === 0) {
                resolve({ skipped: false, backupPath: stdout.trim() });
                return;
            }
            reject(new Error(stderr || `SQLite backup failed. Exit code: ${code}`));
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

function createWindow() {
    const config = readAppConfig();
    const windowSize = getWindowSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
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
            contextIsolation: true
        }
    });

    if (app.isPackaged) {
        win.loadFile(getWebEntry(config));
    } else {
        win.loadURL('http://localhost:8185/');
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
        } catch (error) {
            const message = error instanceof Error ? error.message : String(error);
            const exitChoice = dialog.showMessageBoxSync(win, {
                type: 'warning',
                buttons: ['取消退出', '仍然退出'],
                defaultId: 0,
                cancelId: 0,
                title: '备份失败',
                message: `退出前备份失败：\n${message}\n\n是否仍然退出？`,
            });
            if (exitChoice === 1) {
                isQuitting = true;
                win.close();
            }
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
