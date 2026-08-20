const { contextBridge, ipcRenderer } = require('electron');

function clearAuthForNewElectronSession() {
    const sessionArgument = process.argv.find((argument) =>
        argument.startsWith('--easy-store-session-id=')
    );
    if (!sessionArgument) {
        return;
    }

    const sessionId = sessionArgument.substring('--easy-store-session-id='.length);
    const sessionKey = 'easy-store-electron-session-id';
    const storage = window.sessionStorage;
    if (storage.getItem(sessionKey) === sessionId) {
        return;
    }

    window.localStorage.removeItem('token');
    window.localStorage.removeItem('easy-store-user-info');
    window.localStorage.removeItem('userRole');
    storage.clear();
    storage.setItem(sessionKey, sessionId);
}

try {
    clearAuthForNewElectronSession();
} catch {
    // 页面加载不应因清理旧登录态失败而中断。
}

contextBridge.exposeInMainWorld('electronAPI', {
    resizeWindow: (w, h) => ipcRenderer.send('resize-window', w, h),
    windowMinimize: ()=>ipcRenderer.send('window-minimize'),
    windowClose: ()=>ipcRenderer.send('window-close'),
    windowMaximize : ()=>ipcRenderer.send('window-maximize'),
    openPdf : (filePath)=>ipcRenderer.send('open-pdf',filePath),
    printPdf: (filePath, landscape)=>ipcRenderer.invoke('print-pdf', filePath, landscape),
    getZoomFactor: () => ipcRenderer.invoke('get-zoom-factor'),
    setZoomFactor: (factor) => ipcRenderer.invoke('set-zoom-factor', factor),
    getPrinterSettings: () => ipcRenderer.invoke('get-printer-settings'),
    selectRawPrinter: () => ipcRenderer.invoke('select-raw-printer'),
    rawPrintEscp: (base64Data, jobName, printerName)=>ipcRenderer.invoke('raw-print-escp', base64Data, jobName, printerName),
    openExternal : (url)=>ipcRenderer.send('open-external',url),
    onMainReply: (callback) => ipcRenderer.on('main-to-vue', (event, data) => callback(data)),
});
