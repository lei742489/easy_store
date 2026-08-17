const { app, BrowserWindow, ipcMain, shell, screen } = require('electron');
const PDFWindow = require('electron-pdf-window')
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const MIN_WIDTH = 1440;
const MIN_HEIGHT = 900;
const PRINTER_SETTINGS_FILE = 'printer-settings.json';
const DOT_MATRIX_PRINTER_PATTERNS = [
    /lq[-\s]?\d+/i,
    /lq/i,
    /fx[-\s]?\d+/i,
    /lx[-\s]?\d+/i,
    /dfx/i,
    /plq/i,
    /esc\/?p/i,
    /dot[-\s]?matrix/i,
    /dascom/i,
    /得实/,
    /针式/,
];

function getPrinterSettingsPath() {
    return path.join(app.getPath('userData'), PRINTER_SETTINGS_FILE);
}

function readPrinterSettings() {
    try {
        const filePath = getPrinterSettingsPath();
        if (!fs.existsSync(filePath)) {
            return {};
        }
        return JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } catch (error) {
        return {};
    }
}

function writePrinterSettings(settings) {
    const filePath = getPrinterSettingsPath();
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    fs.writeFileSync(filePath, JSON.stringify(settings, null, 2), 'utf8');
}

function isDotMatrixPrinterName(name) {
    return DOT_MATRIX_PRINTER_PATTERNS.some((pattern) => pattern.test(name || ''));
}

function findPrinterByName(printers, printerName) {
    return printers.find((printer) => printer.name === printerName);
}

function getDefaultPrinter(printers) {
    return printers.find((printer) => printer.isDefault);
}

function findKnownDotMatrixPrinter(printers) {
    return printers.find((printer) => isDotMatrixPrinterName(printer.name));
}

function resolveRawPrinter(printers, selectedPrinterName) {
    if (selectedPrinterName) {
        const selectedPrinter = findPrinterByName(printers, selectedPrinterName);
        if (selectedPrinter) {
            return { printerName: selectedPrinter.name, source: 'selected' };
        }
    }

    const defaultPrinter = getDefaultPrinter(printers);
    if (defaultPrinter && isDotMatrixPrinterName(defaultPrinter.name)) {
        return { printerName: defaultPrinter.name, source: 'default-dot-matrix' };
    }

    const knownDotMatrixPrinter = findKnownDotMatrixPrinter(printers);
    if (knownDotMatrixPrinter) {
        return { printerName: knownDotMatrixPrinter.name, source: 'matched-dot-matrix' };
    }

    if (defaultPrinter) {
        return { printerName: defaultPrinter.name, source: 'default' };
    }

    return { printerName: '', source: 'missing' };
}

async function getPrinterSettings(win) {
    const printers = await win.webContents.getPrintersAsync();
    const settings = readPrinterSettings();
    const selectedPrinterName = settings.selectedRawPrinterName || '';
    const resolvedPrinter = resolveRawPrinter(printers, selectedPrinterName);
    const defaultPrinter = getDefaultPrinter(printers);

    return {
        printers: printers.map((printer) => ({
            name: printer.name,
            isDefault: Boolean(printer.isDefault),
            isDotMatrix: isDotMatrixPrinterName(printer.name),
        })),
        defaultPrinterName: defaultPrinter ? defaultPrinter.name : '',
        selectedPrinterName,
        effectivePrinterName: resolvedPrinter.printerName,
        effectivePrinterSource: resolvedPrinter.source,
        dotMatrixPrinterNames: DOT_MATRIX_PRINTER_PATTERNS.map((pattern) => pattern.source),
    };
}

function fitWindowToWorkArea(win, preferredWidth, preferredHeight) {
    const display = screen.getDisplayMatching(win.getBounds());
    const { width: workWidth, height: workHeight } = display.workAreaSize;
    const minWidth = Math.min(MIN_WIDTH, workWidth);
    const minHeight = Math.min(MIN_HEIGHT, workHeight);
    const width = Math.min(Math.max(Number(preferredWidth) || minWidth, minWidth), workWidth);
    const height = Math.min(Math.max(Number(preferredHeight) || minHeight, minHeight), workHeight);

    win.setMinimumSize(minWidth, minHeight);
    win.setResizable(true);
    win.setMaximizable(true);
    if (preferredWidth >= workWidth || preferredHeight >= workHeight) {
        win.maximize();
        return;
    }
    if (win.isMaximized()) {
        win.unmaximize();
    }
    win.setSize(width, height);
    win.center();
}

function printPdf(filePath, landscape) {
    return new Promise((resolve) => {
        const printWindow = new BrowserWindow({
            show: false,
            webPreferences: {
                contextIsolation: true,
                sandbox: true,
            },
        });
        const closePrintWindow = () => {
            if (!printWindow.isDestroyed()) {
                printWindow.close();
            }
        };

        printWindow.webContents.once('did-fail-load', (event, errorCode, errorDescription) => {
            closePrintWindow();
            resolve({
                success: false,
                failureReason: `加载打印文件失败(${errorCode}): ${errorDescription}`,
            });
        });
        printWindow.webContents.once('did-finish-load', () => {
            // Chromium 内置 PDF 查看器需要短暂时间完成文档渲染后才可稳定发起打印。
            setTimeout(() => {
                if (printWindow.isDestroyed()) {
                    return;
                }
                printWindow.webContents.print(
                    {
                        silent: false,
                        printBackground: false,
                        landscape: Boolean(landscape),
                        pageSize: 'A4',
                    },
                    (success, failureReason) => {
                        closePrintWindow();
                        if (success) {
                            resolve({ success: true });
                            return;
                        }
                        resolve({
                            success: false,
                            failureReason: failureReason || '打印任务未完成',
                        });
                    }
                );
            }, 500);
        });
        printWindow.loadURL(filePath).catch((error) => {
            closePrintWindow();
            resolve({
                success: false,
                failureReason: error.message || '加载打印文件失败',
            });
        });
    });
}

async function findRawPrinter(win, printerName) {
    if (printerName) {
        return printerName;
    }
    const printers = await win.webContents.getPrintersAsync();
    const settings = readPrinterSettings();
    const resolvedPrinter = resolveRawPrinter(printers, settings.selectedRawPrinterName);
    if (resolvedPrinter.printerName) {
        return resolvedPrinter.printerName;
    }
    throw new Error('No available printer was found. Please install a printer in Windows first.');
}

function selectRawPrinter(currentPrinterName) {
    return new Promise((resolve, reject) => {
        const child = spawn('powershell.exe', [
            '-NoProfile',
            '-STA',
            '-ExecutionPolicy',
            'Bypass',
            '-Command',
            `
Add-Type -AssemblyName System.Windows.Forms
$dialog = New-Object System.Windows.Forms.PrintDialog
$dialog.UseEXDialog = $true
$dialog.AllowPrintToFile = $false
if ($args[0]) {
  $dialog.PrinterSettings.PrinterName = $args[0]
}
$result = $dialog.ShowDialog()
if ($result -eq [System.Windows.Forms.DialogResult]::OK) {
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($dialog.PrinterSettings.PrinterName)
  Write-Output ([Convert]::ToBase64String($bytes))
  exit 0
}
exit 2
            `,
            currentPrinterName || '',
        ], {
            windowsHide: true,
        });
        let stdout = '';
        let stderr = '';
        child.stdout.on('data', (data) => {
            stdout += data.toString('utf8');
        });
        child.stderr.on('data', (data) => {
            stderr += data.toString('utf8');
        });
        child.on('error', (error) => {
            reject(error);
        });
        child.on('close', (code) => {
            if (code === 0) {
                const selectedPrinterName = Buffer.from(stdout.trim(), 'base64').toString('utf8');
                if (!selectedPrinterName) {
                    reject(new Error('No printer was selected.'));
                    return;
                }
                const settings = readPrinterSettings();
                writePrinterSettings({
                    ...settings,
                    selectedRawPrinterName: selectedPrinterName,
                });
                resolve({ success: true, printerName: selectedPrinterName });
                return;
            }
            if (code === 2) {
                resolve({ success: false, cancelled: true });
                return;
            }
            reject(new Error(stderr || `Printer selection failed. Exit code: ${code}`));
        });
    });
}

function rawPrintEscp(printerName, base64Data, jobName) {
    return new Promise((resolve, reject) => {
        const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'easy-store-print-'));
        const dataPath = path.join(tempDir, 'print.bin');
        const scriptPath = path.join(tempDir, 'raw-print.ps1');
        fs.writeFileSync(dataPath, Buffer.from(base64Data, 'base64'));
        fs.writeFileSync(scriptPath, `
param(
  [Parameter(Mandatory=$true)][string]$PrinterName,
  [Parameter(Mandatory=$true)][string]$FilePath,
  [string]$JobName = 'Easy Store ESC/P'
)
Add-Type -TypeDefinition @"
using System;
using System.IO;
using System.Runtime.InteropServices;
public class RawPrinterHelper {
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Ansi)]
  public class DOCINFOA {
    [MarshalAs(UnmanagedType.LPStr)] public string pDocName;
    [MarshalAs(UnmanagedType.LPStr)] public string pOutputFile;
    [MarshalAs(UnmanagedType.LPStr)] public string pDataType;
  }
  [DllImport("winspool.Drv", EntryPoint="OpenPrinterA", SetLastError=true, CharSet=CharSet.Ansi, ExactSpelling=true, CallingConvention=CallingConvention.StdCall)]
  public static extern bool OpenPrinter(string szPrinter, out IntPtr hPrinter, IntPtr pd);
  [DllImport("winspool.Drv", EntryPoint="ClosePrinter", SetLastError=true, ExactSpelling=true, CallingConvention=CallingConvention.StdCall)]
  public static extern bool ClosePrinter(IntPtr hPrinter);
  [DllImport("winspool.Drv", EntryPoint="StartDocPrinterA", SetLastError=true, CharSet=CharSet.Ansi, ExactSpelling=true, CallingConvention=CallingConvention.StdCall)]
  public static extern bool StartDocPrinter(IntPtr hPrinter, int level, [In, MarshalAs(UnmanagedType.LPStruct)] DOCINFOA di);
  [DllImport("winspool.Drv", EntryPoint="EndDocPrinter", SetLastError=true, ExactSpelling=true, CallingConvention=CallingConvention.StdCall)]
  public static extern bool EndDocPrinter(IntPtr hPrinter);
  [DllImport("winspool.Drv", EntryPoint="StartPagePrinter", SetLastError=true, ExactSpelling=true, CallingConvention=CallingConvention.StdCall)]
  public static extern bool StartPagePrinter(IntPtr hPrinter);
  [DllImport("winspool.Drv", EntryPoint="EndPagePrinter", SetLastError=true, ExactSpelling=true, CallingConvention=CallingConvention.StdCall)]
  public static extern bool EndPagePrinter(IntPtr hPrinter);
  [DllImport("winspool.Drv", EntryPoint="WritePrinter", SetLastError=true, ExactSpelling=true, CallingConvention=CallingConvention.StdCall)]
  public static extern bool WritePrinter(IntPtr hPrinter, IntPtr pBytes, int dwCount, out int dwWritten);
  public static void SendFileToPrinter(string printerName, string filePath, string jobName) {
    IntPtr hPrinter;
    if (!OpenPrinter(printerName, out hPrinter, IntPtr.Zero)) throw new Exception("OpenPrinter failed");
    try {
      DOCINFOA di = new DOCINFOA();
      di.pDocName = jobName;
      di.pDataType = "RAW";
      if (!StartDocPrinter(hPrinter, 1, di)) throw new Exception("StartDocPrinter failed");
      try {
        if (!StartPagePrinter(hPrinter)) throw new Exception("StartPagePrinter failed");
        byte[] bytes = File.ReadAllBytes(filePath);
        IntPtr unmanagedBytes = Marshal.AllocCoTaskMem(bytes.Length);
        try {
          Marshal.Copy(bytes, 0, unmanagedBytes, bytes.Length);
          int written;
          if (!WritePrinter(hPrinter, unmanagedBytes, bytes.Length, out written) || written != bytes.Length) throw new Exception("WritePrinter failed");
        } finally {
          Marshal.FreeCoTaskMem(unmanagedBytes);
        }
        EndPagePrinter(hPrinter);
      } finally {
        EndDocPrinter(hPrinter);
      }
    } finally {
      ClosePrinter(hPrinter);
    }
  }
}
"@
[RawPrinterHelper]::SendFileToPrinter($PrinterName, $FilePath, $JobName)
`, 'utf8');

        const child = spawn('powershell.exe', [
            '-NoProfile',
            '-ExecutionPolicy',
            'Bypass',
            '-File',
            scriptPath,
            '-PrinterName',
            printerName,
            '-FilePath',
            dataPath,
            '-JobName',
            jobName || 'Easy Store ESC/P',
        ], {
            windowsHide: true,
        });
        let stderr = '';
        child.stderr.on('data', (data) => {
            stderr += data.toString();
        });
        child.on('error', (error) => {
            reject(error);
        });
        child.on('close', (code) => {
            fs.rm(tempDir, { recursive: true, force: true }, () => {});
            if (code === 0) {
                resolve({ success: true });
                return;
            }
            reject(new Error(stderr || `RAW 打印失败，退出码：${code}`));
        });
    });
}

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
            fitWindowToWorkArea(win, width, height);
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

    ipcMain.handle('print-pdf', (event, filePath, landscape) => {
        if (!filePath) {
            throw new Error('打印文件地址不能为空');
        }
        return printPdf(filePath, landscape);
    });

    ipcMain.handle('get-printer-settings', async () => {
        return getPrinterSettings(win);
    });

    ipcMain.handle('select-raw-printer', async () => {
        if (process.platform !== 'win32') {
            throw new Error('Printer selection is only supported on Windows.');
        }
        const settings = await getPrinterSettings(win);
        const result = await selectRawPrinter(settings.effectivePrinterName);
        if (result.success) {
            return getPrinterSettings(win);
        }
        return {
            ...settings,
            cancelled: true,
        };
    });

    ipcMain.handle('raw-print-escp', async (event, base64Data, jobName, printerName) => {
        if (process.platform !== 'win32') {
            throw new Error('ESC/P RAW 打印当前仅支持 Windows');
        }
        if (!base64Data) {
            throw new Error('打印数据不能为空');
        }
        const resolvedPrinterName = await findRawPrinter(win, printerName);
        return rawPrintEscp(resolvedPrinterName, base64Data, jobName);
    });

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
