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
      openExternal: (url: string) => void;
    };
  }
}
