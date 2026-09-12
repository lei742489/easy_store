export interface StyledXlsColumn {
  title: string;
  width?: number;
  align?: 'left' | 'center' | 'right';
}

export interface StyledXlsExportOptions {
  fileName: string;
  title: string;
  columns: StyledXlsColumn[];
  rows: Array<Array<unknown>>;
  amountColumnIndexes?: number[];
  summaryRowIndexes?: number[];
  rowStyles?: Record<number, string>;
  cellStyles?: Record<number, Record<number, string>>;
}

const escapeHtml = (value: unknown) =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

const normalizeFileName = (value: string) =>
  value.replace(/[\\/:*?"<>|]/g, '_');

export function exportStyledXls(options: StyledXlsExportOptions) {
  const amountColumns = new Set(options.amountColumnIndexes || []);
  const summaryRows = new Set(options.summaryRowIndexes || []);
  const defaultWidth = 120;

  const rows = options.rows
    .map((row, rowIndex) => {
      const summary = summaryRows.has(rowIndex);
      const rowStyle =
        options.rowStyles?.[rowIndex] ||
        (summary ? 'background:#f2f3f5;font-weight:600;' : '');
      return `<tr style="${rowStyle}">${options.columns
        .map((column, columnIndex) => {
          const align =
            column.align ||
            (amountColumns.has(columnIndex) ? 'right' : 'center');
          const style =
            summary && amountColumns.has(columnIndex) ? 'color:#165dff;' : '';
          const cellStyle = options.cellStyles?.[rowIndex]?.[columnIndex] || '';
          return `<td style="text-align:${align};${style}${cellStyle}">${escapeHtml(
            row[columnIndex]
          )}</td>`;
        })
        .join('')}</tr>`;
    })
    .join('');

  const html = `
    <!DOCTYPE html>
    <html lang="zh-CN">
      <head>
        <meta charset="UTF-8" />
        <style>
          * { box-sizing: border-box; }
          body { margin: 16px; color: #1d2129; font-family: "Microsoft YaHei", Arial, sans-serif; }
          table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 12px; }
          th, td { height: 28px; padding: 5px 7px; border: 1px solid #d9dfe7; vertical-align: middle; word-break: break-all; }
          th { background: #f2f3f5; text-align: center; font-weight: 600; white-space: nowrap; }
          .title { border: 0; padding: 0 0 12px; text-align: left; font-size: 15px; font-weight: 600; }
        </style>
      </head>
      <body>
        <table>
          <colgroup>${options.columns
            .map(
              (column) =>
                `<col style="width:${column.width || defaultWidth}px" />`
            )
            .join('')}</colgroup>
          <tr><td class="title" colspan="${options.columns.length}">${escapeHtml(
            options.title
          )}</td></tr>
          <tr>${options.columns
            .map((column) => `<th>${escapeHtml(column.title)}</th>`)
            .join('')}</tr>
          ${rows}
        </table>
      </body>
    </html>`;

  const blob = new Blob([`\uFEFF${html}`], {
    type: 'application/vnd.ms-excel;charset=utf-8;',
  });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `${normalizeFileName(options.fileName)}.xls`;
  link.click();
  URL.revokeObjectURL(link.href);
}
