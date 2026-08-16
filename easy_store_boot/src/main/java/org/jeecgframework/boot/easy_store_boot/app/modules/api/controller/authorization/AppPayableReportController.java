package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSupplier;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSupplierService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/user/appPayableReport")
public class AppPayableReportController {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String PURCHASE_BUSINESS_TIME_SQL =
            "CASE WHEN typeof(app_purchase_order.create_time) IN ('integer', 'real') " +
                    "THEN CAST(app_purchase_order.create_time AS INTEGER) " +
                    "ELSE COALESCE(CAST(strftime('%s', app_purchase_order.create_time) AS INTEGER) * 1000, 0) END";
    private static final String PAYMENT_BUSINESS_TIME_SQL =
            "CASE WHEN typeof(app_payment_voucher.create_time) IN ('integer', 'real') " +
                    "THEN CAST(app_payment_voucher.create_time AS INTEGER) " +
                    "ELSE COALESCE(CAST(strftime('%s', app_payment_voucher.create_time) AS INTEGER) * 1000, 0) END";
    private static final String PURCHASE_LINKED_AMOUNT_SQL = "COALESCE(payment_item.linked_amount, 0)";
    private static final String PURCHASE_UNPAID_AMOUNT_SQL =
            "COALESCE(app_purchase_order.unpaid_amount, COALESCE(app_purchase_order.payable_amount, 0) " +
                    "- COALESCE(app_purchase_order.paid_amount, 0))";
    private static final String PURCHASE_DEBT_AMOUNT_SQL =
            "CASE WHEN " + PURCHASE_LINKED_AMOUNT_SQL + " > 0 THEN COALESCE(app_purchase_order.payable_amount, 0) " +
                    "ELSE " + PURCHASE_UNPAID_AMOUNT_SQL + " END";
    private static final String PURCHASE_DEBT_FILTER_SQL =
            "(" + PURCHASE_LINKED_AMOUNT_SQL + " > 0 " +
                    "OR ABS(COALESCE(app_purchase_order.payable_amount, 0) " +
                    "- COALESCE(app_purchase_order.paid_amount, 0)) >= 0.005)";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IAppSupplierService supplierService;
    @Autowired
    private IAppUserService userService;

    @PostMapping("detail")
    public Result<?> detail(@RequestBody JSONObject param) {
        ReportContext context = buildContext(param, true);
        double openingBalance = calculateOpeningBalance(context.supplier, context.startTime, context.cashierId);
        List<Map<String, Object>> rows = queryDetailRows(
                context.supplier.getId().toString(), context.startTime, context.endTime, context.cashierId);

        JSONArray records = new JSONArray();
        double payableTotal = 0D;
        double paidTotal = 0D;
        double balance = openingBalance;
        int rowNo = 1;

        if (Math.abs(openingBalance) >= 0.000001D) {
            records.add(buildDetailRow(rowNo++, context.startTime == null ? "" : formatDate(context.startTime),
                    "", "期初结存", 0D, 0D, balance));
        }

        for (Map<String, Object> row : rows) {
            double payableAmount = numberValue(row.get("payableAmount")).doubleValue();
            double paidAmount = numberValue(row.get("paidAmount")).doubleValue();
            payableTotal += payableAmount;
            paidTotal += paidAmount;
            balance += payableAmount - paidAmount;
            records.add(buildDetailRow(rowNo++, formatDate(numberValue(row.get("businessTime")).longValue()),
                    stringValue(row.get("orderNo")), stringValue(row.get("summary")),
                    payableAmount, paidAmount, balance));
        }

        return Result.ok(buildResult(context.supplier, openingBalance, payableTotal, paidTotal, balance, records));
    }

    @PostMapping("statement")
    public Result<?> statement(@RequestBody JSONObject param) {
        ReportContext context = buildContext(param, true);
        double openingBalance = calculateOpeningBalance(context.supplier, context.startTime, context.cashierId);
        List<Map<String, Object>> documents = queryStatementDocuments(
                context.supplier.getId().toString(), context.startTime, context.endTime, context.cashierId);

        JSONArray records = new JSONArray();
        double payableTotal = 0D;
        double paidTotal = 0D;
        double balance = openingBalance;
        int rowNo = 1;

        if (Math.abs(openingBalance) >= 0.000001D) {
            records.add(buildStatementRow(rowNo++, "opening",
                    context.startTime == null ? "期初" : formatDate(context.startTime) + " 期初",
                    "", null, null, null, null, null, "期初", 0D, 0D, balance));
        }

        for (Map<String, Object> document : documents) {
            String recordType = stringValue(document.get("recordType"));
            double payableAmount = numberValue(document.get("payableAmount")).doubleValue();
            double paidAmount = numberValue(document.get("paidAmount")).doubleValue();
            balance += payableAmount - paidAmount;
            payableTotal += payableAmount;
            paidTotal += paidAmount;

            if ("purchase".equals(recordType)) {
                Integer orderType = integerValue(document.get("orderType"));
                String orderNo = stringValue(document.get("orderNo"));
                String note = stringValue(document.get("note"));
                String summary = StringUtils.isNotEmpty(note)
                        ? note
                        : (orderType != null && orderType == 2 ? "进货退货" : "采购入库");
                String title = formatDate(numberValue(document.get("businessTime")).longValue()) +
                        " " + (orderType != null && orderType == 2 ? "进货退货单" : "进货单") +
                        " " + orderNo;
                double totalAmount = numberValue(document.get("totalAmount")).doubleValue();
                double discountedAmount = numberValue(document.get("discountedAmount")).doubleValue();
                records.add(buildStatementRow(rowNo++, "purchase", title, "", null, null,
                        numberValue(document.get("freightAmount")).doubleValue(), totalAmount,
                        totalAmount - discountedAmount, summary, payableAmount, 0D, balance));

                Integer orderId = integerValue(document.get("id"));
                if (orderId != null) {
                    for (Map<String, Object> item : queryPurchaseItems(orderId)) {
                        records.add(buildStatementRow(rowNo++, "item", stringValue(item.get("goodsName")),
                                stringValue(item.get("unit")), integerValue(item.get("quantity")),
                                numberValue(item.get("unitPrice")).doubleValue(), null,
                                numberValue(item.get("totalAmount")).doubleValue(), null,
                                stringValue(item.get("note")), 0D, 0D, balance));
                    }
                }
            } else {
                String orderNo = stringValue(document.get("orderNo"));
                String note = stringValue(document.get("note"));
                String title = formatDate(numberValue(document.get("businessTime")).longValue()) +
                        " 付款单 " + orderNo;
                records.add(buildStatementRow(rowNo++, "payment", title, "", null, null, null,
                        null, null, StringUtils.isNotEmpty(note) ? note : "付款",
                        0D, paidAmount, balance));
            }
        }

        return Result.ok(buildResult(context.supplier, openingBalance, payableTotal, paidTotal, balance, records));
    }

    @PostMapping("statistics")
    public Result<?> statistics(@RequestBody JSONObject param) {
        ReportContext context = buildContext(param, false);
        List<AppSupplier> suppliers = querySuppliers(param.getString("supplierId"));
        Map<String, Double> openingPurchaseAmounts = context.startTime == null
                ? new HashMap<>()
                : sumPurchaseDebtBySupplier(param.getString("supplierId"), null, context.startTime, true, context.cashierId);
        Map<String, Double> openingPaidAmounts = context.startTime == null
                ? new HashMap<>()
                : sumPaidBySupplier(param.getString("supplierId"), null, context.startTime, true, context.cashierId);
        Map<String, Double> payableAmounts =
                sumPurchaseDebtBySupplier(param.getString("supplierId"), context.startTime, context.endTime, false, context.cashierId);
        Map<String, Double> paidAmounts =
                sumPaidBySupplier(param.getString("supplierId"), context.startTime, context.endTime, false, context.cashierId);

        JSONArray records = new JSONArray();
        double openingTotal = 0D;
        double payableTotal = 0D;
        double paidTotal = 0D;
        double endingTotal = 0D;
        int rowNo = 1;

        for (AppSupplier supplier : suppliers) {
            String supplierId = supplier.getId().toString();
            double defaultPayable = supplier.getDefPayable() == null ? 0D : supplier.getDefPayable();
            double openingBalance = defaultPayable + amountOf(openingPurchaseAmounts, supplierId)
                    - amountOf(openingPaidAmounts, supplierId);
            double payableAmount = amountOf(payableAmounts, supplierId);
            double paidAmount = amountOf(paidAmounts, supplierId);
            double endingBalance = openingBalance + payableAmount - paidAmount;
            if (Math.abs(endingBalance) < 0.005D) {
                continue;
            }

            JSONObject row = new JSONObject();
            row.put("rowNo", rowNo++);
            row.put("supplierId", supplier.getId());
            row.put("supplierName", supplier.getName());
            row.put("openingBalance", openingBalance);
            row.put("payableAmount", payableAmount);
            row.put("paidAmount", paidAmount);
            row.put("endingBalance", endingBalance);
            records.add(row);

            openingTotal += openingBalance;
            payableTotal += payableAmount;
            paidTotal += paidAmount;
            endingTotal += endingBalance;
        }

        JSONObject result = new JSONObject();
        result.put("openingTotal", openingTotal);
        result.put("payableTotal", payableTotal);
        result.put("paidTotal", paidTotal);
        result.put("endingTotal", endingTotal);
        result.put("records", records);
        return Result.ok(result);
    }

    private ReportContext buildContext(JSONObject param, boolean requireSupplier) {
        String supplierId = param.getString("supplierId");
        AppSupplier supplier = null;
        if (StringUtils.isNotEmpty(supplierId)) {
            supplier = supplierService.getById(supplierId);
            if (supplier == null) {
                throw new AppRunTimeException("供应商不存在或已删除");
            }
        } else if (requireSupplier) {
            throw new AppRunTimeException("请选择要查询的供应商");
        }

        AppUser user = userService.getById(param.getString("userId"));
        if (user == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }

        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        if (startTime != null && endTime != null && startTime > endTime) {
            throw new AppRunTimeException("开始日期不能晚于结束日期");
        }

        ReportContext context = new ReportContext();
        context.supplier = supplier;
        context.startTime = startTime;
        context.endTime = endTime;
        context.cashierId = user.getIsRoot() != null && user.getIsRoot() == 1 ? null : String.valueOf(user.getId());
        return context;
    }

    private List<AppSupplier> querySuppliers(String supplierId) {
        QueryWrapper<AppSupplier> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("name");
        if (StringUtils.isNotEmpty(supplierId)) {
            wrapper.eq("id", supplierId);
        }
        return supplierService.list(wrapper);
    }

    private List<Map<String, Object>> queryDetailRows(String supplierId, Long startTime,
                                                      Long endTime, String cashierId) {
        String purchaseOwnerCondition = cashierId == null ? "" : " AND app_purchase_order.cashier_id = ?";
        String paymentOwnerCondition = cashierId == null ? "" : " AND app_payment_voucher.cashier_id = ?";
        String sql = "SELECT business_time AS businessTime, order_no AS orderNo, summary, " +
                "payable_amount AS payableAmount, paid_amount AS paidAmount FROM (" +
                "SELECT " + PURCHASE_BUSINESS_TIME_SQL + " AS business_time, app_purchase_order.order_no AS order_no, " +
                "CASE WHEN app_purchase_order.note IS NOT NULL AND trim(app_purchase_order.note) <> '' THEN app_purchase_order.note " +
                "WHEN app_purchase_order.order_type = 2 THEN '进货退货' ELSE '采购入库' END AS summary, " +
                PURCHASE_DEBT_AMOUNT_SQL + " AS payable_amount, 0 AS paid_amount " +
                "FROM app_purchase_order " +
                "LEFT JOIN (SELECT order_no, SUM(COALESCE(amount, 0)) AS linked_amount " +
                "FROM app_payment_amount_item GROUP BY order_no) payment_item " +
                "ON app_purchase_order.order_no = payment_item.order_no " +
                "WHERE app_purchase_order.supplier_id = ? AND app_purchase_order.status = 1 " +
                "AND COALESCE(app_purchase_order.is_del, 0) = 0 " +
                "AND " + PURCHASE_DEBT_FILTER_SQL + " AND ABS(" + PURCHASE_DEBT_AMOUNT_SQL + ") >= 0.005" +
                purchaseOwnerCondition +
                " UNION ALL " +
                "SELECT " + PAYMENT_BUSINESS_TIME_SQL + " AS business_time, app_payment_voucher.order_no, " +
                "CASE WHEN app_payment_voucher.note IS NULL OR trim(app_payment_voucher.note) = '' THEN '付款' " +
                "ELSE app_payment_voucher.note END AS summary, " +
                "0 AS payable_amount, COALESCE(app_payment_voucher.amount, 0) AS paid_amount " +
                "FROM app_payment_voucher WHERE app_payment_voucher.supplier_id = ? " +
                "AND app_payment_voucher.status = 1 AND COALESCE(app_payment_voucher.is_del, 0) = 0" +
                paymentOwnerCondition +
                ") detail WHERE 1 = 1";
        List<Object> params = new ArrayList<>();
        params.add(supplierId);
        if (cashierId != null) params.add(cashierId);
        params.add(supplierId);
        if (cashierId != null) params.add(cashierId);
        if (startTime != null) {
            sql += " AND business_time >= ?";
            params.add(startTime);
        }
        if (endTime != null) {
            sql += " AND business_time <= ?";
            params.add(endTime);
        }
        sql += " ORDER BY business_time ASC, order_no ASC";
        return jdbcTemplate.queryForList(sql, params.toArray());
    }

    private List<Map<String, Object>> queryStatementDocuments(String supplierId, Long startTime,
                                                              Long endTime, String cashierId) {
        String purchaseOwnerCondition = cashierId == null ? "" : " AND app_purchase_order.cashier_id = ?";
        String paymentOwnerCondition = cashierId == null ? "" : " AND app_payment_voucher.cashier_id = ?";
        String sql = "SELECT record_type AS recordType, business_time AS businessTime, id, order_no AS orderNo, " +
                "order_type AS orderType, note, freight_amount AS freightAmount, total_amount AS totalAmount, " +
                "discounted_amount AS discountedAmount, payable_amount AS payableAmount, paid_amount AS paidAmount FROM (" +
                "SELECT 'purchase' AS record_type, " + PURCHASE_BUSINESS_TIME_SQL +
                " AS business_time, app_purchase_order.id, app_purchase_order.order_no, " +
                "app_purchase_order.order_type, app_purchase_order.note, " +
                "COALESCE(app_purchase_order.freight_amount, 0) AS freight_amount, " +
                "COALESCE(app_purchase_order.total_amount, 0) AS total_amount, " +
                "COALESCE(app_purchase_order.discounted_amount, app_purchase_order.total_amount, 0) AS discounted_amount, " +
                PURCHASE_DEBT_AMOUNT_SQL + " AS payable_amount, 0 AS paid_amount " +
                "FROM app_purchase_order " +
                "LEFT JOIN (SELECT order_no, SUM(COALESCE(amount, 0)) AS linked_amount " +
                "FROM app_payment_amount_item GROUP BY order_no) payment_item " +
                "ON app_purchase_order.order_no = payment_item.order_no " +
                "WHERE app_purchase_order.supplier_id = ? AND app_purchase_order.status = 1 " +
                "AND COALESCE(app_purchase_order.is_del, 0) = 0 " +
                "AND " + PURCHASE_DEBT_FILTER_SQL + " AND ABS(" + PURCHASE_DEBT_AMOUNT_SQL + ") >= 0.005" +
                purchaseOwnerCondition +
                " UNION ALL " +
                "SELECT 'payment' AS record_type, " + PAYMENT_BUSINESS_TIME_SQL +
                " AS business_time, app_payment_voucher.id, app_payment_voucher.order_no, " +
                "NULL AS order_type, app_payment_voucher.note, 0 AS freight_amount, 0 AS total_amount, " +
                "0 AS discounted_amount, 0 AS payable_amount, COALESCE(app_payment_voucher.amount, 0) AS paid_amount " +
                "FROM app_payment_voucher WHERE app_payment_voucher.supplier_id = ? " +
                "AND app_payment_voucher.status = 1 AND COALESCE(app_payment_voucher.is_del, 0) = 0" +
                paymentOwnerCondition +
                ") statement WHERE 1 = 1";
        List<Object> params = new ArrayList<>();
        params.add(supplierId);
        if (cashierId != null) params.add(cashierId);
        params.add(supplierId);
        if (cashierId != null) params.add(cashierId);
        if (startTime != null) {
            sql += " AND business_time >= ?";
            params.add(startTime);
        }
        if (endTime != null) {
            sql += " AND business_time <= ?";
            params.add(endTime);
        }
        sql += " ORDER BY business_time ASC, record_type ASC, order_no ASC";
        return jdbcTemplate.queryForList(sql, params.toArray());
    }

    private List<Map<String, Object>> queryPurchaseItems(Integer orderId) {
        String sql = "SELECT COALESCE(goods.title, item.goods_id) AS goodsName, item.unit, item.quantity, " +
                "COALESCE(item.unit_price, 0) AS unitPrice, COALESCE(item.total_amount, 0) AS totalAmount, item.note " +
                "FROM app_purchase_order_item item LEFT JOIN app_goods goods ON item.goods_id = goods.id " +
                "WHERE item.order_id = ? AND COALESCE(item.is_del, 0) = 0 ORDER BY item.id ASC";
        return jdbcTemplate.queryForList(sql, orderId);
    }

    private double calculateOpeningBalance(AppSupplier supplier, Long startTime, String cashierId) {
        double defaultPayable = supplier.getDefPayable() == null ? 0D : supplier.getDefPayable();
        if (startTime == null) return defaultPayable;
        return defaultPayable + sumPurchaseDebtBeforeDate(supplier.getId().toString(), startTime, cashierId)
                - sumPaidBeforeDate(supplier.getId().toString(), startTime, cashierId);
    }

    private double sumPurchaseDebtBeforeDate(String supplierId, Long startTime, String cashierId) {
        Map<String, Double> map = sumPurchaseDebtBySupplier(supplierId, null, startTime, true, cashierId);
        return amountOf(map, supplierId);
    }

    private double sumPaidBeforeDate(String supplierId, Long startTime, String cashierId) {
        Map<String, Double> map = sumPaidBySupplier(supplierId, null, startTime, true, cashierId);
        return amountOf(map, supplierId);
    }

    private Map<String, Double> sumPurchaseDebtBySupplier(String supplierId, Long startTime, Long endTime,
                                                          boolean beforeStart, String cashierId) {
        String sql = basePurchaseDebtSql();
        List<Object> params = new ArrayList<>();
        if (StringUtils.isNotEmpty(supplierId)) {
            sql += " AND app_purchase_order.supplier_id = ?";
            params.add(supplierId);
        }
        if (cashierId != null) {
            sql += " AND app_purchase_order.cashier_id = ?";
            params.add(cashierId);
        }
        if (beforeStart && endTime != null) {
            sql += " AND " + PURCHASE_BUSINESS_TIME_SQL + " < ?";
            params.add(endTime);
        } else if (startTime != null) {
            sql += " AND " + PURCHASE_BUSINESS_TIME_SQL + " >= ?";
            params.add(startTime);
        }
        if (!beforeStart && endTime != null) {
            sql += " AND " + PURCHASE_BUSINESS_TIME_SQL + " <= ?";
            params.add(endTime);
        }
        sql += " GROUP BY app_purchase_order.supplier_id";
        return queryGroupedSum(sql, params);
    }

    private String basePurchaseDebtSql() {
        return "SELECT app_purchase_order.supplier_id AS ownerId, " +
                "COALESCE(SUM(" + PURCHASE_DEBT_AMOUNT_SQL + "), 0) AS amount FROM app_purchase_order " +
                "LEFT JOIN (SELECT order_no, SUM(COALESCE(amount, 0)) AS linked_amount " +
                "FROM app_payment_amount_item GROUP BY order_no) payment_item " +
                "ON app_purchase_order.order_no = payment_item.order_no " +
                "WHERE app_purchase_order.status = 1 AND COALESCE(app_purchase_order.is_del, 0) = 0 " +
                "AND " + PURCHASE_DEBT_FILTER_SQL + " AND ABS(" + PURCHASE_DEBT_AMOUNT_SQL + ") >= 0.005";
    }

    private Map<String, Double> sumPaidBySupplier(String supplierId, Long startTime, Long endTime,
                                                  boolean beforeStart, String cashierId) {
        String sql = basePaidSql();
        List<Object> params = new ArrayList<>();
        if (StringUtils.isNotEmpty(supplierId)) {
            sql += " AND app_payment_voucher.supplier_id = ?";
            params.add(supplierId);
        }
        if (cashierId != null) {
            sql += " AND app_payment_voucher.cashier_id = ?";
            params.add(cashierId);
        }
        if (beforeStart && endTime != null) {
            sql += " AND " + PAYMENT_BUSINESS_TIME_SQL + " < ?";
            params.add(endTime);
        } else if (startTime != null) {
            sql += " AND " + PAYMENT_BUSINESS_TIME_SQL + " >= ?";
            params.add(startTime);
        }
        if (!beforeStart && endTime != null) {
            sql += " AND " + PAYMENT_BUSINESS_TIME_SQL + " <= ?";
            params.add(endTime);
        }
        sql += " GROUP BY app_payment_voucher.supplier_id";
        return queryGroupedSum(sql, params);
    }

    private String basePaidSql() {
        return "SELECT app_payment_voucher.supplier_id AS ownerId, " +
                "COALESCE(SUM(COALESCE(app_payment_voucher.amount, 0)), 0) AS amount " +
                "FROM app_payment_voucher WHERE app_payment_voucher.status = 1 " +
                "AND COALESCE(app_payment_voucher.is_del, 0) = 0";
    }

    private Map<String, Double> queryGroupedSum(String sql, List<Object> params) {
        Map<String, Double> amountMap = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, params.toArray())) {
            Object ownerId = row.get("ownerId");
            Object amount = row.get("amount");
            if (ownerId instanceof Number && amount instanceof Number) {
                amountMap.put(String.valueOf(((Number) ownerId).intValue()), ((Number) amount).doubleValue());
            } else if (ownerId != null && amount != null) {
                try {
                    amountMap.put(ownerId.toString(), Double.parseDouble(amount.toString()));
                } catch (NumberFormatException ignored) {
                    // Skip invalid aggregate values.
                }
            }
        }
        return amountMap;
    }

    private double amountOf(Map<String, Double> amountMap, String ownerId) {
        Double amount = amountMap.get(ownerId);
        return amount == null ? 0D : amount;
    }

    private JSONObject buildResult(AppSupplier supplier, double openingBalance, double payableTotal,
                                   double paidTotal, double endingBalance, JSONArray records) {
        JSONObject result = new JSONObject();
        result.put("supplierId", supplier.getId());
        result.put("supplierName", supplier.getName());
        result.put("openingBalance", openingBalance);
        result.put("payableTotal", payableTotal);
        result.put("paidTotal", paidTotal);
        result.put("endingBalance", endingBalance);
        result.put("records", records);
        return result;
    }

    private JSONObject buildDetailRow(int rowNo, String businessDate, String orderNo, String summary,
                                      double payableAmount, double paidAmount, double endingBalance) {
        JSONObject row = new JSONObject();
        row.put("rowNo", rowNo);
        row.put("businessDate", businessDate);
        row.put("orderNo", orderNo);
        row.put("summary", summary);
        row.put("payableAmount", payableAmount);
        row.put("paidAmount", paidAmount);
        row.put("endingBalance", endingBalance);
        return row;
    }

    private JSONObject buildStatementRow(int rowNo, String rowType, String goodsName, String unit, Integer quantity,
                                         Double unitPrice, Double freightAmount, Double totalAmount,
                                         Double discountAmount, String note, double payableAmount,
                                         double paidAmount, double endingBalance) {
        JSONObject row = new JSONObject();
        row.put("rowNo", rowNo);
        row.put("rowType", rowType);
        row.put("goodsName", goodsName);
        row.put("unit", unit);
        row.put("quantity", quantity);
        row.put("unitPrice", unitPrice);
        row.put("freightAmount", freightAmount);
        row.put("totalAmount", totalAmount);
        row.put("discountAmount", discountAmount);
        row.put("note", note);
        row.put("payableAmount", payableAmount);
        row.put("paidAmount", paidAmount);
        row.put("endingBalance", endingBalance);
        return row;
    }

    private Long parseStartTime(String value) {
        if (StringUtils.isEmpty(value)) return null;
        try {
            return LocalDate.parse(value, DATE_FORMATTER).atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        } catch (Exception e) {
            throw new AppRunTimeException("开始日期格式错误");
        }
    }

    private Long parseEndTime(String value) {
        if (StringUtils.isEmpty(value)) return null;
        try {
            return LocalDate.parse(value, DATE_FORMATTER).plusDays(1)
                    .atStartOfDay(ZONE_ID).toInstant().toEpochMilli() - 1;
        } catch (Exception e) {
            throw new AppRunTimeException("结束日期格式错误");
        }
    }

    private String formatDate(long time) {
        if (time <= 0) return "";
        return Instant.ofEpochMilli(time).atZone(ZONE_ID).format(DATE_FORMATTER);
    }

    private Number numberValue(Object value) {
        if (value instanceof Number) return (Number) value;
        if (value == null) return 0D;
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null) return null;
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static class ReportContext {
        private AppSupplier supplier;
        private Long startTime;
        private Long endTime;
        private String cashierId;
    }
}
