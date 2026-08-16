package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomer;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/user/appReceivableStatement")
public class AppReceivableStatementController {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SALE_BUSINESS_TIME_SQL =
            "CASE WHEN typeof(app_sale_order.create_time) IN ('integer', 'real') " +
                    "THEN CAST(app_sale_order.create_time AS INTEGER) " +
                    "ELSE COALESCE(CAST(strftime('%s', app_sale_order.create_time) AS INTEGER) * 1000, 0) END";
    private static final String RECEIVE_BUSINESS_TIME_SQL =
            "CASE WHEN typeof(app_receive_payment_voucher.create_time) IN ('integer', 'real') " +
                    "THEN CAST(app_receive_payment_voucher.create_time AS INTEGER) " +
                    "ELSE COALESCE(CAST(strftime('%s', app_receive_payment_voucher.create_time) AS INTEGER) * 1000, 0) END";
    private static final String SALE_LINKED_AMOUNT_SQL = "COALESCE(receive_item.linked_amount, 0)";
    private static final String SALE_UNPAID_AMOUNT_SQL =
            "COALESCE(app_sale_order.unpaid_amount, COALESCE(app_sale_order.payable_amount, 0) " +
                    "- COALESCE(app_sale_order.paid_amount, 0))";
    private static final String SALE_DEBT_AMOUNT_SQL =
            "CASE WHEN " + SALE_LINKED_AMOUNT_SQL + " > 0 THEN COALESCE(app_sale_order.payable_amount, 0) " +
                    "ELSE " + SALE_UNPAID_AMOUNT_SQL + " END";
    private static final String SALE_DEBT_FILTER_SQL =
            "(" + SALE_LINKED_AMOUNT_SQL + " > 0 " +
                    "OR ABS(COALESCE(app_sale_order.payable_amount, 0) " +
                    "- COALESCE(app_sale_order.paid_amount, 0)) >= 0.005)";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IAppCustomerService customerService;
    @Autowired
    private IAppUserService userService;

    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param) {
        String customerId = param.getString("customerId");
        if (StringUtils.isEmpty(customerId)) {
            throw new AppRunTimeException("请选择要查询的客户");
        }

        AppCustomer customer = customerService.getById(customerId);
        if (customer == null) {
            throw new AppRunTimeException("客户不存在或已删除");
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

        boolean rootUser = user.getIsRoot() != null && user.getIsRoot() == 1;
        String cashierId = rootUser ? null : String.valueOf(user.getId());
        double openingBalance = calculateOpeningBalance(customer, startTime, cashierId);
        List<Map<String, Object>> documents = queryDocuments(customerId, startTime, endTime, cashierId);

        JSONArray records = new JSONArray();
        double receivableTotal = 0D;
        double receivedTotal = 0D;
        double balance = openingBalance;
        int rowNo = 1;

        if (Math.abs(openingBalance) >= 0.000001D) {
            records.add(buildRow(rowNo++, "opening", startTime == null ? "期初" : formatDate(startTime) + " 期初",
                    "", null, null, null, null, null, "期初", 0D, 0D, balance));
        }

        for (Map<String, Object> document : documents) {
            String recordType = stringValue(document.get("recordType"));
            double receivableAmount = numberValue(document.get("receivableAmount")).doubleValue();
            double receivedAmount = numberValue(document.get("receivedAmount")).doubleValue();
            balance += receivableAmount - receivedAmount;
            receivableTotal += receivableAmount;
            receivedTotal += receivedAmount;

            if ("sale".equals(recordType)) {
                Integer orderType = integerValue(document.get("orderType"));
                String orderNo = stringValue(document.get("orderNo"));
                String note = stringValue(document.get("note"));
                String summary = StringUtils.isNotEmpty(note)
                        ? note
                        : (orderType != null && orderType == 2 ? "销售退货" : "销售出货");
                String title = formatDate(numberValue(document.get("businessTime")).longValue()) +
                        " " + (orderType != null && orderType == 2 ? "销售退货单" : "销售单") +
                        " " + orderNo;
                double totalAmount = numberValue(document.get("totalAmount")).doubleValue();
                double discountedAmount = numberValue(document.get("discountedAmount")).doubleValue();
                records.add(buildRow(rowNo++, "sale", title, "", null, null,
                        numberValue(document.get("freightAmount")).doubleValue(),
                        totalAmount, totalAmount - discountedAmount, summary,
                        receivableAmount, 0D, balance));

                Integer orderId = integerValue(document.get("id"));
                if (orderId != null) {
                    for (Map<String, Object> item : queryOrderItems(orderId)) {
                        records.add(buildRow(rowNo++, "item", stringValue(item.get("goodsName")),
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
                        " 收款单 " + orderNo;
                records.add(buildRow(rowNo++, "receipt", title, "", null, null, null,
                        null, null, StringUtils.isNotEmpty(note) ? note : "收款",
                        0D, receivedAmount, balance));
            }
        }

        JSONObject result = new JSONObject();
        result.put("customerId", customer.getId());
        result.put("customerName", customer.getName());
        result.put("openingBalance", openingBalance);
        result.put("receivableTotal", receivableTotal);
        result.put("receivedTotal", receivedTotal);
        result.put("endingBalance", balance);
        result.put("records", records);
        return Result.ok(result);
    }

    private List<Map<String, Object>> queryDocuments(String customerId, Long startTime,
                                                     Long endTime, String cashierId) {
        String saleOwnerCondition = cashierId == null ? "" : " AND app_sale_order.cashier_id = ?";
        String receiveOwnerCondition = cashierId == null ? "" : " AND app_receive_payment_voucher.cashier_id = ?";
        String sql = "SELECT record_type AS recordType, business_time AS businessTime, id, order_no AS orderNo, " +
                "order_type AS orderType, note, freight_amount AS freightAmount, total_amount AS totalAmount, " +
                "discounted_amount AS discountedAmount, receivable_amount AS receivableAmount, " +
                "received_amount AS receivedAmount FROM (" +
                "SELECT 'sale' AS record_type, " + SALE_BUSINESS_TIME_SQL + " AS business_time, app_sale_order.id, " +
                "app_sale_order.order_no, app_sale_order.order_type, app_sale_order.note, " +
                "COALESCE(app_sale_order.freight_amount, 0) AS freight_amount, " +
                "COALESCE(app_sale_order.total_amount, 0) AS total_amount, " +
                "COALESCE(app_sale_order.discounted_amount, app_sale_order.total_amount, 0) AS discounted_amount, " +
                SALE_DEBT_AMOUNT_SQL + " AS receivable_amount, 0 AS received_amount " +
                "FROM app_sale_order " +
                "LEFT JOIN (SELECT order_no, SUM(COALESCE(amount, 0)) AS linked_amount " +
                "FROM app_receive_payment_amount_item GROUP BY order_no) receive_item " +
                "ON app_sale_order.order_no = receive_item.order_no " +
                "WHERE app_sale_order.customer_id = ? AND app_sale_order.status = 1 " +
                "AND COALESCE(app_sale_order.is_del, 0) = 0 " +
                "AND " + SALE_DEBT_FILTER_SQL + " AND ABS(" + SALE_DEBT_AMOUNT_SQL + ") >= 0.005" +
                saleOwnerCondition +
                " UNION ALL " +
                "SELECT 'receipt' AS record_type, " + RECEIVE_BUSINESS_TIME_SQL + " AS business_time, " +
                "app_receive_payment_voucher.id, app_receive_payment_voucher.order_no, " +
                "NULL AS order_type, app_receive_payment_voucher.note, 0 AS freight_amount, " +
                "0 AS total_amount, 0 AS discounted_amount, " +
                "0 AS receivable_amount, COALESCE(app_receive_payment_voucher.amount, 0) AS received_amount " +
                "FROM app_receive_payment_voucher WHERE app_receive_payment_voucher.customer_id = ? " +
                "AND app_receive_payment_voucher.status = 1 " +
                "AND COALESCE(app_receive_payment_voucher.is_del, 0) = 0" + receiveOwnerCondition +
                ") statement WHERE 1 = 1";
        List<Object> params = new ArrayList<>();
        params.add(customerId);
        if (cashierId != null) params.add(cashierId);
        params.add(customerId);
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

    private List<Map<String, Object>> queryOrderItems(Integer orderId) {
        String sql = "SELECT COALESCE(goods.title, item.goods_id) AS goodsName, item.unit, item.quantity, " +
                "COALESCE(item.unit_price, 0) AS unitPrice, COALESCE(item.total_amount, 0) AS totalAmount, item.note " +
                "FROM app_sale_order_item item LEFT JOIN app_goods goods ON item.goods_id = goods.id " +
                "WHERE item.order_id = ? AND COALESCE(item.is_del, 0) = 0 ORDER BY item.id ASC";
        return jdbcTemplate.queryForList(sql, orderId);
    }

    private double calculateOpeningBalance(AppCustomer customer, Long startTime, String cashierId) {
        double defaultPayable = customer.getDefPayable() == null ? 0D : customer.getDefPayable();
        if (startTime == null) return defaultPayable;
        return defaultPayable + sumSaleDebtBeforeDate(customer.getId().toString(), startTime, cashierId)
                - sumReceivedBeforeDate(customer.getId().toString(), startTime, cashierId);
    }

    private double sumSaleDebtBeforeDate(String customerId, Long startTime, String cashierId) {
        String ownerCondition = cashierId == null ? "" : " AND app_sale_order.cashier_id = ?";
        String sql = "SELECT COALESCE(SUM(" + SALE_DEBT_AMOUNT_SQL + "), 0) FROM app_sale_order " +
                "LEFT JOIN (SELECT order_no, SUM(COALESCE(amount, 0)) AS linked_amount " +
                "FROM app_receive_payment_amount_item GROUP BY order_no) receive_item " +
                "ON app_sale_order.order_no = receive_item.order_no " +
                "WHERE app_sale_order.customer_id = ? AND app_sale_order.status = 1 " +
                "AND COALESCE(app_sale_order.is_del, 0) = 0 " +
                "AND " + SALE_DEBT_FILTER_SQL + " AND ABS(" + SALE_DEBT_AMOUNT_SQL + ") >= 0.005" +
                ownerCondition + " AND " + SALE_BUSINESS_TIME_SQL + " < ?";
        return querySum(sql, customerId, startTime, cashierId);
    }

    private double sumReceivedBeforeDate(String customerId, Long startTime, String cashierId) {
        String ownerCondition = cashierId == null ? "" : " AND app_receive_payment_voucher.cashier_id = ?";
        String sql = "SELECT COALESCE(SUM(COALESCE(amount, 0)), 0) FROM app_receive_payment_voucher " +
                "WHERE app_receive_payment_voucher.customer_id = ? AND app_receive_payment_voucher.status = 1 " +
                "AND COALESCE(app_receive_payment_voucher.is_del, 0) = 0" +
                ownerCondition + " AND " + RECEIVE_BUSINESS_TIME_SQL + " < ?";
        return querySum(sql, customerId, startTime, cashierId);
    }

    private double querySum(String sql, String customerId, Long startTime, String cashierId) {
        List<Object> params = new ArrayList<>();
        params.add(customerId);
        if (cashierId != null) params.add(cashierId);
        params.add(startTime);
        Number value = jdbcTemplate.queryForObject(sql, params.toArray(), Number.class);
        return value == null ? 0D : value.doubleValue();
    }

    private JSONObject buildRow(int rowNo, String rowType, String goodsName, String unit, Integer quantity,
                                Double unitPrice, Double freightAmount, Double totalAmount, Double discountAmount,
                                String note, double receivableAmount, double receivedAmount, double endingBalance) {
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
        row.put("receivableAmount", receivableAmount);
        row.put("receivedAmount", receivedAmount);
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
}
