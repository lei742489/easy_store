export interface CLodop {
  PRINT_INIT: (name?: string) => void;
  GET_PRINTER_COUNT?: () => number;
  GET_PRINTER_NAME?: (index: number) => string;
  SET_PRINTER_INDEX?: (indexOrName: number | string) => boolean;
  SET_PRINT_PAGESIZE: (
    orient?: number,
    pageWidth?: number | string,
    pageHeight?: number | string,
    pageName?: string
  ) => void;
  ADD_PRINT_URL?: (
    top: number | string,
    left: number | string,
    width: number | string,
    height: number | string,
    url: string
  ) => void;
  ADD_PRINT_PDF?: (
    top: number | string,
    left: number | string,
    width: number | string,
    height: number | string,
    data: string
  ) => void;
  ADD_PRINT_HTM?: (
    top: number | string,
    left: number | string,
    width: number | string,
    height: number | string,
    html: string
  ) => void;
  ADD_PRINT_TEXT?: (
    top: number | string,
    left: number | string,
    width: number | string,
    height: number | string,
    text: string
  ) => void;
  PRINTA: (
    printBackground?: boolean | number,
    showPrinterSelector?: boolean | string
  ) => number | boolean | null;
  PREVIEW?: () => number | boolean | null;
  PRINT?: () => number | boolean | null;
}

export interface CLodopPrinter {
  index: number;
  name: string;
}

export interface CLodopPaperSize {
  width: number;
  height: number;
  name?: string;
}

const toClodopPaperValue = (value: number) => Math.round(value * 10);
const PRINT_MARGIN_LEFT = '5mm';
const PRINT_CONTENT_WIDTH = 'RightMargin:5mm';
const CLODOP_DOWNLOAD_URL = 'https://www.lodop.net/download.html';

const createInstallError = () =>
  new Error(
    `未检测到 C-Lodop 打印服务，请先安装并启动 C-Lodop 插件。下载地址：${CLODOP_DOWNLOAD_URL}`
  );

const createPrivateNetworkError = () =>
  new Error(
    '浏览器访问 C-Lodop 需要 HTTPS 页面和本机 HTTPS 打印服务。请使用 https 地址访问系统，并确认 C-Lodop 的 https://localhost:8443 服务可用。'
  );

type CLodopGetter = () => CLodop | null | undefined;

declare global {
  interface Window {
    getCLodop?: CLodopGetter;
    getLodop?: CLodopGetter;
  }
}

const CLODOP_ELECTRON_SCRIPT_URLS = [
    'http://localhost:8000/CLodopfuncs.js?priority=1',
    'http://127.0.0.1:8000/CLodopfuncs.js?priority=1',
    'http://localhost:18000/CLodopfuncs.js?priority=1',
    'http://127.0.0.1:18000/CLodopfuncs.js?priority=1',
    'https://localhost:8443/CLodopfuncs.js?priority=1',
    'https://127.0.0.1:8443/CLodopfuncs.js?priority=1',
] as const;

const CLODOP_BROWSER_HTTPS_SCRIPT_URLS = [
    'https://localhost:8443/CLodopfuncs.js?priority=1',
    'https://127.0.0.1:8443/CLodopfuncs.js?priority=1',
] as const;

const CLODOP_BROWSER_LOCAL_SCRIPT_URLS = [
  ...CLODOP_BROWSER_HTTPS_SCRIPT_URLS,
  'http://localhost:8000/CLodopfuncs.js?priority=1',
  'http://127.0.0.1:8000/CLodopfuncs.js?priority=1',
  'http://localhost:18000/CLodopfuncs.js?priority=1',
  'http://127.0.0.1:18000/CLodopfuncs.js?priority=1',
] as const;

let loadPromise: Promise<CLodop> | null = null;

const isLocalHostname = (hostname: string) =>
  hostname === 'localhost' ||
  hostname === '127.0.0.1' ||
  hostname === '[::1]' ||
  hostname === '::1';

const isElectronRuntime = () => Boolean(window.electronAPI);

const canAccessLocalCLodopService = () =>
  window.isSecureContext || isLocalHostname(window.location.hostname);

const getCLodopScriptUrls = () => {
  if (isElectronRuntime()) {
    return CLODOP_ELECTRON_SCRIPT_URLS;
  }

  if (isLocalHostname(window.location.hostname)) {
    return CLODOP_BROWSER_LOCAL_SCRIPT_URLS;
  }

  return CLODOP_BROWSER_HTTPS_SCRIPT_URLS;
};

const getLoadedCLodop = (): CLodop | null => {
  const getter = window.getCLodop || window.getLodop;
  if (!getter) return null;

  try {
    return getter() || null;
  } catch {
    return null;
  }
};

const waitForCLodop = (timeout = 5000): Promise<CLodop> =>
  new Promise((resolve, reject) => {
    const startedAt = Date.now();

    const check = () => {
      const lodop = getLoadedCLodop();
      if (lodop) {
        resolve(lodop);
        return;
      }

      if (Date.now() - startedAt >= timeout) {
        reject(createInstallError());
        return;
      }

      window.setTimeout(check, 100);
    };

    check();
  });

const loadScript = (url: string): Promise<CLodop> =>
  new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(
      `script[data-clodop-url="${url}"]`
    );
    if (existing) {
      waitForCLodop().then(resolve).catch(reject);
      return;
    }

    const script = document.createElement('script');
    script.async = true;
    script.src = url;
    script.dataset.clodopUrl = url;
    script.onload = () => {
      waitForCLodop().then(resolve).catch(reject);
    };
    script.onerror = () => {
      script.remove();
      reject(new Error(`C-Lodop service unavailable: ${url}`));
    };
    document.head.appendChild(script);
  });

export function getCLodop(): Promise<CLodop> {
  const loaded = getLoadedCLodop();
  if (loaded) return Promise.resolve(loaded);
  if (!canAccessLocalCLodopService()) {
    return Promise.reject(createPrivateNetworkError());
  }
  if (loadPromise) return loadPromise;

  const urls = getCLodopScriptUrls();

  const tryLoad = async (index = 0): Promise<CLodop> => {
    const url = urls[index];
    if (!url) {
      throw createInstallError();
    }

    try {
      return await loadScript(url);
    } catch (error) {
      if (index + 1 >= urls.length) {
        throw createInstallError();
      }
      return tryLoad(index + 1);
    }
  };

  loadPromise = tryLoad();

  return loadPromise.catch((error) => {
    loadPromise = null;
    throw error;
  });
}

export async function getCLodopPrinters(): Promise<CLodopPrinter[]> {
  const lodop = await getCLodop();
  const count = lodop.GET_PRINTER_COUNT?.() || 0;
  const printers: CLodopPrinter[] = [];

  for (let index = 0; index < count; index += 1) {
    printers.push({
      index,
      name: lodop.GET_PRINTER_NAME?.(index) || `Printer ${index + 1}`,
    });
  }

  return printers;
}

function preparePdfPrintJob(
  lodop: CLodop,
  pdfUrl: string,
  jobName: string,
  printerName?: string,
  paperSize?: CLodopPaperSize
) {
  lodop.PRINT_INIT(jobName);
  if (paperSize) {
    lodop.SET_PRINT_PAGESIZE(
      0,
      toClodopPaperValue(paperSize.width),
      toClodopPaperValue(paperSize.height),
      paperSize.name || ''
    );
  } else {
    lodop.SET_PRINT_PAGESIZE(0, 0, 0, 'A4');
  }
  if (printerName && lodop.SET_PRINTER_INDEX) {
    const ok = lodop.SET_PRINTER_INDEX(printerName);
    if (!ok) {
      throw new Error(`打印机不可用：${printerName}`);
    }
  }

  if (lodop.ADD_PRINT_PDF) {
    lodop.ADD_PRINT_PDF(0, PRINT_MARGIN_LEFT, PRINT_CONTENT_WIDTH, '100%', pdfUrl);
  } else if (lodop.ADD_PRINT_URL) {
    lodop.ADD_PRINT_URL(0, PRINT_MARGIN_LEFT, PRINT_CONTENT_WIDTH, '100%', pdfUrl);
  } else {
    throw new Error('The installed C-Lodop version cannot print PDF files');
  }
}

function prepareTextPreviewJob(
  lodop: CLodop,
  text: string,
  jobName: string,
  printerName?: string,
  paperSize?: CLodopPaperSize
) {
  lodop.PRINT_INIT(jobName);
  if (paperSize) {
    lodop.SET_PRINT_PAGESIZE(
      0,
      toClodopPaperValue(paperSize.width),
      toClodopPaperValue(paperSize.height),
      paperSize.name || ''
    );
  } else {
    lodop.SET_PRINT_PAGESIZE(0, 0, 0, 'A4');
  }
  if (printerName && lodop.SET_PRINTER_INDEX) {
    const ok = lodop.SET_PRINTER_INDEX(printerName);
    if (!ok) {
      throw new Error(`打印机不可用：${printerName}`);
    }
  }

  if (lodop.ADD_PRINT_TEXT) {
    lodop.ADD_PRINT_TEXT(30, PRINT_MARGIN_LEFT, PRINT_CONTENT_WIDTH, 120, text);
  } else if (lodop.ADD_PRINT_URL) {
    lodop.ADD_PRINT_URL(
      30,
      PRINT_MARGIN_LEFT,
      PRINT_CONTENT_WIDTH,
      120,
      `data:text/plain;charset=utf-8,${encodeURIComponent(text)}`
    );
  } else {
    throw new Error('The installed C-Lodop version cannot preview text');
  }
}

function prepareHtmlPrintJob(
  lodop: CLodop,
  htmlContent: string,
  jobName: string,
  printerName?: string,
  paperSize?: CLodopPaperSize
) {
  lodop.PRINT_INIT(jobName);
  if (paperSize) {
    lodop.SET_PRINT_PAGESIZE(
      0,
      toClodopPaperValue(paperSize.width),
      toClodopPaperValue(paperSize.height),
      paperSize.name || ''
    );
  } else {
    lodop.SET_PRINT_PAGESIZE(0, 0, 0, 'A4');
  }
  if (printerName && lodop.SET_PRINTER_INDEX) {
    const ok = lodop.SET_PRINTER_INDEX(printerName);
    if (!ok) {
      throw new Error(`打印机不可用：${printerName}`);
    }
  }

  if (lodop.ADD_PRINT_HTM) {
    lodop.ADD_PRINT_HTM(0, PRINT_MARGIN_LEFT, PRINT_CONTENT_WIDTH, '100%', htmlContent);
  } else if (lodop.ADD_PRINT_URL) {
    const htmlUrl = `data:text/html;charset=utf-8,${encodeURIComponent(
      htmlContent
    )}`;
    lodop.ADD_PRINT_URL(0, PRINT_MARGIN_LEFT, PRINT_CONTENT_WIDTH, '100%', htmlUrl);
  } else {
    throw new Error('The installed C-Lodop version cannot preview HTML');
  }
}

export async function previewPdfWithCLodop(
  pdfUrl: string,
  jobName: string,
  printerName?: string,
  paperSize?: CLodopPaperSize
): Promise<void> {
  const lodop = await getCLodop();
  preparePdfPrintJob(lodop, pdfUrl, jobName, printerName, paperSize);

  const result = lodop.PREVIEW ? lodop.PREVIEW() : lodop.PRINTA(false, true);
  if (result === 0) {
    throw new Error('C-Lodop preview was cancelled');
  }
}

export async function previewHtmlWithCLodop(
  htmlContent: string,
  jobName: string,
  printerName?: string,
  paperSize?: CLodopPaperSize
): Promise<void> {
  const lodop = await getCLodop();
  prepareHtmlPrintJob(lodop, htmlContent, jobName, printerName, paperSize);

  const result = lodop.PREVIEW ? lodop.PREVIEW() : lodop.PRINTA(false, true);
  if (result === 0) {
    throw new Error('C-Lodop preview was cancelled');
  }
}

export async function previewTextWithCLodop(
  text: string,
  jobName: string,
  printerName?: string,
  paperSize?: CLodopPaperSize
): Promise<void> {
  const lodop = await getCLodop();
  prepareTextPreviewJob(lodop, text, jobName, printerName, paperSize);

  const result = lodop.PREVIEW ? lodop.PREVIEW() : lodop.PRINTA(false, true);
  if (result === 0) {
    throw new Error('C-Lodop preview was cancelled');
  }
}

export async function printPdfWithCLodop(
  pdfUrl: string,
  jobName: string,
  printerName?: string,
  paperSize?: CLodopPaperSize
): Promise<void> {
  const lodop = await getCLodop();
  preparePdfPrintJob(lodop, pdfUrl, jobName, printerName, paperSize);

  let result: number | boolean | null;
  if (printerName) {
    result = lodop.PRINT ? lodop.PRINT() : lodop.PRINTA(false, false);
  } else {
    result = lodop.PRINTA(false, true);
  }
  if (result === 0) {
    throw new Error('C-Lodop print was cancelled');
  }
}

export async function printHtmlWithCLodop(
  htmlContent: string,
  jobName: string,
  printerName?: string,
  paperSize?: CLodopPaperSize
): Promise<void> {
  const lodop = await getCLodop();
  prepareHtmlPrintJob(lodop, htmlContent, jobName, printerName, paperSize);

  let result: number | boolean | null;
  if (printerName) {
    result = lodop.PRINT ? lodop.PRINT() : lodop.PRINTA(false, false);
  } else {
    result = lodop.PRINTA(false, true);
  }
  if (result === 0) {
    throw new Error('C-Lodop print was cancelled');
  }
}

export {};
