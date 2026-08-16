// eslint-disable-next-line import/prefer-default-export
export function windowMinimize() {
  if (window.electronAPI) window.electronAPI.windowMinimize(); // 窗口最小化
}
export function windowMaximize() {
  if (window.electronAPI) window.electronAPI.windowMaximize(); // 窗口最大化
}
export function windowClose() {
  if (window.electronAPI) window.electronAPI.windowClose(); // 关闭窗口
}
export function resizeWindow(width: number, height: number) {
  if (window.electronAPI) window.electronAPI.resizeWindow(width, height); // 调整窗口大小
}
export function openPdf(filePath: string) {
  if (window.electronAPI?.openPdf) {
    window.electronAPI.openPdf(filePath);
    return;
  }

  const link = document.createElement('a');
  link.href = filePath;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
export function openExternal(url: string) {
  if (window.electronAPI) window.electronAPI.openExternal(url);
}
