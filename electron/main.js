const { app, BrowserWindow, Menu ,dialog,ipcMain  } = require('electron');
const exec = require('child_process').exec;
const path = require('path');
const { registerWindowEvents } = require('./ipcHandlers/windowHandlers');

let win;
//最小宽高
let minWidth = 600;
let minHeight = 400;

function createWindow() {
     win = new BrowserWindow({
        width: 1080,
        height: 740,
         icon: path.join(__dirname, 'assets', 'icon.png'), // 图标路径
         resizable: false,
         frame: false,
         maximizable: false,
         webPreferences: {
             preload: __dirname + '/preload.js', // 用 preload 脚本
             nodeIntegration: false,
             contextIsolation: true
         }
    });

    win.setMinimumSize(minWidth,minHeight);
    win.loadURL('http://localhost:8185/'); // 或者：win.loadFile('dist/index.html')
    Menu.setApplicationMenu(null);

    //开发环境开启调试
    const isDev = require('electron-is-dev');
    if (isDev) {
      // win.webContents.openDevTools();
    }

    // 拦截关闭事件
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
            // 用户点击“取消”
            e.preventDefault(); // 阻止窗口关闭

        }
        // 若用户点击“退出”，则不拦截，窗口会关闭
    });

    // 注册所有窗口相关的事件监听器
    registerWindowEvents(win);

}


app.whenReady().then(() => {
    // 启动 Spring Boot 后台（假设已经编译成 myapp.jar）
    /*exec('java -jar myapp.jar', (error, stdout, stderr) => {
        if (error) {
            console.error(`Spring Boot 启动失败: ${error}`);
        }
    });*/

    createWindow();
});
