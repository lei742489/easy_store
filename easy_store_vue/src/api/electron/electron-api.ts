export interface ElectronPrinterInfo {
  name: string;
  isDefault: boolean;
  isDotMatrix: boolean;
}

export interface ElectronPrinterSettings {
  printers: ElectronPrinterInfo[];
  defaultPrinterName: string;
  selectedPrinterName: string;
  effectivePrinterName: string;
  effectivePrinterSource: string;
  dotMatrixPrinterNames: string[];
  cancelled?: boolean;
}

export function isElectronRuntime() {
  return Boolean(window.electronAPI);
}

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
export async function printPdf(filePath: string, landscape: boolean) {
  if (window.electronAPI?.printPdf) {
    await window.electronAPI.printPdf(filePath, landscape);
    return;
  }

  openPdf(filePath);
}
export async function getPrinterSettings(): Promise<ElectronPrinterSettings> {
  if (!window.electronAPI?.getPrinterSettings) {
    throw new Error('当前环境不支持打印机设置');
  }
  return window.electronAPI.getPrinterSettings();
}
export async function selectRawPrinter(): Promise<ElectronPrinterSettings> {
  if (!window.electronAPI?.selectRawPrinter) {
    throw new Error('当前环境不支持打印机设置');
  }
  return window.electronAPI.selectRawPrinter();
}
export async function rawPrintEscp(
  base64Data: string,
  jobName?: string,
  printerName?: string
) {
  if (!window.electronAPI?.rawPrintEscp) {
    throw new Error('当前环境不支持针式打印');
  }
  await window.electronAPI.rawPrintEscp(base64Data, jobName, printerName);
}
export function openExternal(url: string) {
  if (window.electronAPI) window.electronAPI.openExternal(url);
}
