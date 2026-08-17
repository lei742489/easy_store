export {};

declare global {
  interface Window {
    electronAPI: {
      windowMinimize: () => void;
      resizeWindow: (width: number, height: number) => void;
      windowClose: () => void;
      windowMaximize: () => void;
      onMainReply: (data: any) => void;
      openPdf: (filePath: string) => void;
      printPdf: (filePath: string, landscape: boolean) => Promise<void>;
      getPrinterSettings: () => Promise<{
        printers: Array<{
          name: string;
          isDefault: boolean;
          isDotMatrix: boolean;
        }>;
        defaultPrinterName: string;
        selectedPrinterName: string;
        effectivePrinterName: string;
        effectivePrinterSource: string;
        dotMatrixPrinterNames: string[];
        cancelled?: boolean;
      }>;
      selectRawPrinter: () => Promise<{
        printers: Array<{
          name: string;
          isDefault: boolean;
          isDotMatrix: boolean;
        }>;
        defaultPrinterName: string;
        selectedPrinterName: string;
        effectivePrinterName: string;
        effectivePrinterSource: string;
        dotMatrixPrinterNames: string[];
        cancelled?: boolean;
      }>;
      rawPrintEscp: (
        base64Data: string,
        jobName?: string,
        printerName?: string
      ) => Promise<void>;
      openExternal: (url: string) => void;
    };
  }
}
