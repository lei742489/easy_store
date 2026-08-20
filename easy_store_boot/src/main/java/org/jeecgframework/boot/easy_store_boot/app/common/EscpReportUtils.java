package org.jeecgframework.boot.easy_store_boot.app.common;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class EscpReportUtils {
    private static final Charset PRINTER_CHARSET = Charset.forName("GB18030");
    private static final int WIDE_WIDTH = 100;
    private static final int NARROW_WIDTH = 72;

    private EscpReportUtils() {
    }

    public static byte[] saleOrder(AppSaleOrder order, AppCustomer customer, AppUser user) {
        EscpWriter writer = new EscpWriter(WIDE_WIDTH);
        writer.init();
        writer.condensed(true);
        writer.title(companyName(user) + " 销售单");
        writer.blank();
        writer.spread3("客户名称：" + name(customer), "单号：" + value(order.getOrderNo()), "日期：" + date(order.getCreateTime()));
        int[] widths = new int[]{5, 36, 8, 8, 13, 13, 9};
        writer.tableBorder(widths);
        writer.tableRow(widths, "行号", "品名规格", "单位", "数量", "单价", "金额", "备注");
        writer.tableBorder(widths);
        List<AppSaleOrderItem> items = order.getItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                AppSaleOrderItem item = items.get(i);
                writer.tableRow(widths,
                        String.valueOf(i + 1),
                        value(item.getGoodsId()),
                        value(item.getUnit()),
                        value(item.getQuantity()),
                        money(item.getUnitPrice()),
                        money(item.getTotalAmount()),
                        value(item.getNote()));
                if (i < items.size() - 1) {
                    writer.tableBorder(widths);
                }
            }
        }
        writer.tableBorder(widths);
        writer.tableSummary(widths, "合计  金额大写 " + value(order.getTotalAmountChinese()), "￥" + money(order.getTotalAmount()), "");
        writer.tableBorder(widths);
        writer.blank();
        writer.spread4("运费：￥" + money(order.getFreightAmount()), "本单应付：￥" + money(order.getPayableAmount()),
                "实 付：￥" + money(order.getPaidAmount()), "签收人：");
        writer.blank();
        writer.line("备注说明：" + value(order.getNote()));
        writer.finish();
        return writer.toBytes();
    }

    public static byte[] purchaseOrder(AppPurchaseOrder order, AppSupplier supplier, AppUser user) {
        EscpWriter writer = new EscpWriter(WIDE_WIDTH);
        writer.init();
        writer.condensed(true);
        writer.title(companyName(user) + " 进货单");
        writer.blank();
        writer.spread3("供应商：" + name(supplier), "单号：" + value(order.getOrderNo()), "日期：" + date(order.getCreateTime()));
        int[] widths = new int[]{5, 36, 8, 8, 13, 13, 9};
        writer.tableBorder(widths);
        writer.tableRow(widths, "行号", "品名规格", "单位", "数量", "单价", "金额", "备注");
        writer.tableBorder(widths);
        List<AppPurchaseOrderItem> items = order.getItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                AppPurchaseOrderItem item = items.get(i);
                writer.tableRow(widths,
                        String.valueOf(i + 1),
                        value(item.getGoodsId()),
                        value(item.getUnit()),
                        value(item.getQuantity()),
                        money(item.getUnitPrice()),
                        money(item.getTotalAmount()),
                        value(item.getNote()));
                if (i < items.size() - 1) {
                    writer.tableBorder(widths);
                }
            }
        }
        writer.tableBorder(widths);
        writer.tableSummary(widths, "合计  金额大写 " + value(order.getTotalAmountChinese()), "￥" + money(order.getTotalAmount()), "");
        writer.tableBorder(widths);
        writer.blank();
        writer.spread4("运费：￥" + money(order.getFreightAmount()), "本单应付：￥" + money(order.getPayableAmount()),
                "实 付：￥" + money(order.getPaidAmount()), "签收人：");
        writer.blank();
        writer.line("备注说明：" + value(order.getNote()));
        writer.finish();
        return writer.toBytes();
    }

    public static byte[] receivePaymentVoucher(AppReceivePaymentVoucher voucher, AppCustomer customer, AppUser user, Double total) {
        EscpWriter writer = new EscpWriter(NARROW_WIDTH);
        writer.init();
        writer.condensed(true);
        writer.center(companyName(user));
        writer.center("收款单");
        writer.blank();
        writer.line("客户名称：" + name(customer));
        writer.line("日期：" + date(voucher.getCreateTime()) + "    单号：" + value(voucher.getOrderNo()));
        writer.rule();
        writer.row(new int[]{4, 28, 12, 28}, "行号", "账户名称", "金额", "备注");
        writer.rule();
        List<AppReceivePaymentSettleItem> items = voucher.getSettleItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                AppReceivePaymentSettleItem item = items.get(i);
                writer.row(new int[]{4, 28, 12, 28}, String.valueOf(i + 1), value(item.getSettleId()), money(item.getAmount()), value(item.getNote()));
                if (i < items.size() - 1) {
                    writer.rule();
                }
            }
        }
        writer.rule();
        writer.line("合计：" + value(voucher.getTotalAmountChinese()) + "    ￥" + money(total));
        writer.line("剩余欠款：￥" + money(customer == null ? null : customer.getPayable()));
        writer.line("备注：" + value(voucher.getNote()));
        writer.line("签收人：");
        writer.finish();
        return writer.toBytes();
    }

    public static byte[] paymentVoucher(AppPaymentVoucher voucher, AppSupplier supplier, AppUser user, Double total) {
        EscpWriter writer = new EscpWriter(NARROW_WIDTH);
        writer.init();
        writer.condensed(true);
        writer.center(companyName(user));
        writer.center("付款单");
        writer.blank();
        writer.line("供应商：" + name(supplier));
        writer.line("日期：" + date(voucher.getCreateTime()) + "    单号：" + value(voucher.getOrderNo()));
        writer.rule();
        writer.row(new int[]{4, 28, 12, 28}, "行号", "账户名称", "金额", "备注");
        writer.rule();
        List<AppPaymentSettleItem> items = voucher.getSettleItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                AppPaymentSettleItem item = items.get(i);
                writer.row(new int[]{4, 28, 12, 28}, String.valueOf(i + 1), value(item.getSettleId()), money(item.getAmount()), value(item.getNote()));
                if (i < items.size() - 1) {
                    writer.rule();
                }
            }
        }
        writer.rule();
        writer.line("合计：" + value(voucher.getTotalAmountChinese()) + "    ￥" + money(total));
        writer.line("剩余应付款：￥" + money(supplier == null ? null : supplier.getPayable()));
        writer.line("备注：" + value(voucher.getNote()));
        writer.line("签收人：");
        writer.finish();
        return writer.toBytes();
    }

    private static String companyName(AppUser user) {
        return user == null ? "" : value(user.getCompanyName());
    }

    private static String name(AppCustomer customer) {
        return customer == null ? "" : value(customer.getName());
    }

    private static String name(AppSupplier supplier) {
        return supplier == null ? "" : value(supplier.getName());
    }

    private static String date(Date date) {
        Date value = date == null ? new Date() : date;
        return new SimpleDateFormat("yyyy/MM/dd").format(value);
    }

    private static String money(Double amount) {
        return amount == null ? "0.00" : String.format("%.2f", amount);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static class EscpWriter {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final int width;

        EscpWriter(int width) {
            this.width = width;
        }

        void init() {
            write(0x1B, 0x40);
            write(0x1B, 0x78, 0);
            write(0x1B, 0x30);
        }

        void condensed(boolean enabled) {
            write(enabled ? 0x0F : 0x12);
        }

        void center(String text) {
            String value = trim(text, width);
            int padding = Math.max((width - size(value)) / 2, 0);
            line(repeat(" ", padding) + value);
        }

        void title(String text) {
            write(0x1B, 0x21, 0x10);
            center(text);
            write(0x1B, 0x21, 0x00);
            condensed(true);
        }

        void spread3(String left, String center, String right) {
            int cellWidth = width / 3;
            line(pad(left, cellWidth) + pad(center, cellWidth) + pad(right, width - cellWidth * 2));
        }

        void spread4(String first, String second, String third, String fourth) {
            int cellWidth = width / 4;
            line(pad(first, cellWidth) + pad(second, cellWidth) + pad(third, cellWidth) + pad(fourth, width - cellWidth * 3));
        }

        void line(String text) {
            writeText(text);
            write(0x0D, 0x0A);
        }

        void blank() {
            line("");
        }

        void rule() {
            line(repeat("-", width));
        }

        void row(int[] widths, String... cells) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < widths.length; i++) {
                String cell = i < cells.length ? cells[i] : "";
                builder.append(pad(cell, widths[i]));
            }
            line(builder.toString());
        }

        void tableBorder(int[] widths) {
            condensed(true);
            StringBuilder builder = new StringBuilder("+");
            for (int cellWidth : widths) {
                builder.append(repeat("-", cellWidth)).append("+");
            }
            line(builder.toString());
        }

        void tableRow(int[] widths, String... cells) {
            condensed(true);
            StringBuilder builder = new StringBuilder("|");
            for (int i = 0; i < widths.length; i++) {
                String cell = i < cells.length ? cells[i] : "";
                builder.append(centerPad(cell, widths[i])).append("|");
            }
            line(builder.toString());
        }

        void tableSummary(int[] widths, String text, String amount, String note) {
            condensed(true);
            int summaryWidth = widths[0] + widths[1] + widths[2] + widths[3] + widths[4] + 4;
            StringBuilder builder = new StringBuilder("|");
            builder.append(pad(text, summaryWidth)).append("|");
            builder.append(centerPad(amount, widths[5])).append("|");
            builder.append(centerPad(note, widths[6])).append("|");
            line(builder.toString());
        }

        void finish() {
            write(0x0C);
            condensed(false);
        }

        byte[] toBytes() {
            return output.toByteArray();
        }

        private void writeText(String text) {
            byte[] bytes = value(text).getBytes(PRINTER_CHARSET);
            output.write(bytes, 0, bytes.length);
        }

        private void write(int... values) {
            for (int value : values) {
                output.write(value);
            }
        }

        private String pad(String text, int width) {
            String value = trim(text, width);
            return value + repeat(" ", Math.max(width - size(value), 0));
        }

        private String centerPad(String text, int width) {
            String value = trim(text, width);
            int padding = Math.max(width - size(value), 0);
            int left = padding / 2;
            int right = padding - left;
            return repeat(" ", left) + value + repeat(" ", right);
        }

        private String trim(String text, int width) {
            String value = value(text);
            StringBuilder builder = new StringBuilder();
            int length = 0;
            for (int i = 0; i < value.length(); i++) {
                String ch = value.substring(i, i + 1);
                int charSize = size(ch);
                if (length + charSize > width) {
                    break;
                }
                builder.append(ch);
                length += charSize;
            }
            return builder.toString();
        }

        private int size(String text) {
            return value(text).getBytes(PRINTER_CHARSET).length;
        }

        private String repeat(String value, int count) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < count; i++) {
                builder.append(value);
            }
            return builder.toString();
        }
    }
}
