package org.jeecgframework.boot.easy_store_boot.app.common.excel;

import org.apache.poi.ss.usermodel.*;
import org.jeecgframework.poi.excel.export.styler.ExcelExportStylerDefaultImpl;
import org.jeecgframework.poi.excel.entity.params.ExcelExportEntity;

public class ExcelExportStylerBorder extends ExcelExportStylerDefaultImpl {

    public ExcelExportStylerBorder(Workbook workbook) {
        super(workbook);
    }

    @Override
    public CellStyle getHeaderStyle(short color) {
        CellStyle style = super.getHeaderStyle(color);
        setBorder(style);
        return style;
    }

    @Override
    public CellStyle getTitleStyle(short color) {
        CellStyle style = super.getTitleStyle(color);
        setBorder(style);
        return style;
    }

    @Override
    public CellStyle getStyles(boolean parity, ExcelExportEntity entity) {
        CellStyle style = super.getStyles(parity, entity);
        setBorder(style);
        return style;
    }

    private void setBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
