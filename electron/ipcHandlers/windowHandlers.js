const { app, ipcMain, shell } = require('electron');
const PDFWindow = require('electron-pdf-window')
const path = require('path');

function registerWindowEvents(win) {
    // 最小化窗口
    ipcMain.on('window-minimize', (event) => {
        if (win) win.minimize();
        event.reply('main-to-vue', '主进程已收到你的消息');
    });

    // 最大化窗口
    ipcMain.on('window-maximize', () => {
        if (win) {
            if (win.isMaximized()) {
                win.unmaximize();
            } else {
                win.maximize();
            }
        }
    });

    // 关闭窗口
    ipcMain.on('window-close', () => {
        if (win) win.close();
    });

    // 修改窗口大小
    ipcMain.on('resize-window', (event, width, height) => {
        if (win) {
            win.setSize(width, height);
            win.setResizable(true);
            win.setMaximizable(true);
            win.center();
            win.setMinimumSize(width, height);
        }
    });

    ipcMain.on('open-pdf', (event, filePath) => {
        const appPath = app.getAppPath();

        const win = new PDFWindow({
            width: 1280,
            height: 910,
            icon: path.join(appPath, 'assets', 'icon.png'), // 图标路径
        })
        win.loadURL(filePath);

    })

    ipcMain.on('open-external', (event, url) => {
        if (url) {
            shell.openExternal(url);
        }
    })

    // 可根据需要添加更多事件
}

module.exports = {
    registerWindowEvents,
};
