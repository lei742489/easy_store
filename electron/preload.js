const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
    resizeWindow: (w, h) => ipcRenderer.send('resize-window', w, h),
    windowMinimize: ()=>ipcRenderer.send('window-minimize'),
    windowClose: ()=>ipcRenderer.send('window-close'),
    windowMaximize : ()=>ipcRenderer.send('window-maximize'),
    openPdf : (filePath)=>ipcRenderer.send('open-pdf',filePath),
    openExternal : (url)=>ipcRenderer.send('open-external',url),
    onMainReply: (callback) => ipcRenderer.on('main-to-vue', (event, data) => callback(data)),
});
