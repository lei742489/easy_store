export interface CLodop {
  PRINT_INIT: (name?: string) => void;
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
  PRINTA: (
    printBackground?: boolean | number,
    showPrinterSelector?: boolean | string
  ) => number | boolean | null;
}

type CLodopGetter = () => CLodop | null | undefined;

declare global {
  interface Window {
    getCLodop?: CLodopGetter;
    getLodop?: CLodopGetter;
  }
}

const CLODOP_SCRIPT_URLS = {
  http: [
    'http://localhost:8000/CLodopfuncs.js?priority=1',
    'http://localhost:18000/CLodopfuncs.js?priority=1',
    'https://localhost:8443/CLodopfuncs.js?priority=1',
  ],
  https: [
    'https://localhost:8443/CLodopfuncs.js?priority=1',
    'http://localhost:8000/CLodopfuncs.js?priority=1',
    'http://localhost:18000/CLodopfuncs.js?priority=1',
  ],
} as const;

let loadPromise: Promise<CLodop> | null = null;

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
        reject(
          new Error(
            '\u672a\u68c0\u6d4b\u5230 C-Lodop\uff0c\u8bf7\u5148\u5b89\u88c5\u5e76\u542f\u52a8 C-Lodop \u670d\u52a1'
          )
        );
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
  if (loadPromise) return loadPromise;

  const urls =
    window.location.protocol === 'https:'
      ? CLODOP_SCRIPT_URLS.https
      : CLODOP_SCRIPT_URLS.http;

  const tryLoad = async (index = 0): Promise<CLodop> => {
    const url = urls[index];
    if (!url) {
      throw new Error('C-Lodop failed to load');
    }

    try {
      return await loadScript(url);
    } catch (error) {
      if (index + 1 >= urls.length) {
        throw error;
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

export async function printPdfWithCLodop(
  pdfUrl: string,
  jobName: string
): Promise<void> {
  const lodop = await getCLodop();
  lodop.PRINT_INIT(jobName);
  lodop.SET_PRINT_PAGESIZE(0, 0, 0, 'A4');

  // URL printing works with the PDF path returned by the backend. Older
  // clients may not expose ADD_PRINT_URL, so keep the PDF-data fallback.
  if (lodop.ADD_PRINT_URL) {
    lodop.ADD_PRINT_URL(0, 0, '100%', '100%', pdfUrl);
  } else if (lodop.ADD_PRINT_PDF) {
    lodop.ADD_PRINT_PDF(0, 0, '100%', '100%', pdfUrl);
  } else {
    throw new Error('The installed C-Lodop version cannot print PDF files');
  }

  const result = lodop.PRINTA(false, true);
  if (result === 0) {
    throw new Error('C-Lodop print was cancelled');
  }
}

export {};
