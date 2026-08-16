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
@RequestMapping("api/user/appDebtDetail")
public class AppDebtDetailController {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String BUSINESS_TIME_SQL =
            "CASE WHEN typeof(create_time) IN ('integer', 'real') THEN CAST(create_time AS INTEGER) " +
                    "ELSE COALESCE(CAST(strftime('%s', create_time) AS INTEGER) * 1000, 0) END";
    private static final String SALE_LINKED_AMOUNT_SQL = "COALESCE(receive_item.linked_amount, 0)";
    private static final String SALE_UNPAID_AMOUNT_SQL =
            "COALESCE(unpaid_amount, COALESCE(payable_amount, 0) - COALESCE(paid_amount, 0))";
    private static final String SALE_DEBT_AMOUNT_SQL =
            "CASE WHEN " + SALE_LINKED_AMOUNT_SQL + " > 0 THEN COALESCE(payable_amount, 0) " +
                    "ELSE " + SALE_UNPAID_AMOUNT_SQL + " END";
    private static final String SALE_DEBT_FILTER_SQL =
            "(" + SALE_LINKED_AMOUNT_SQL + " > 0 " +
                    "OR ABS(COALESCE(payable_amount, 0) - COALESCE(paid_amount, 0)) >= 0.005)";

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
        List<Map<String, Object>> rows = queryDetailRows(customerId, startTime, endTime, cashierId);

        JSONArray records = new JSONArray();
        double receivableTotal = 0D;
        double receivedTotal = 0D;
        double balance = openingBalance;
        int rowNo = 1;

        if (Math.abs(openingBalance) >= 0.000001D) {
            records.add(buildRow(rowNo++, startTime == null ? "" : formatDate(startTime),
                    "", "期初结存", 0D, 0D, balance));
        }

        for (Map<String, Object> row : rows) {
            double receivableAmount = numberValue(row.get("receivableAmount")).doubleValue();
            double receivedAmount = numberValue(row.get("receivedAmount")).doubleValue();
            receivableTotal += receivableAmount;
            receivedTotal += receivedAmount;
            balance += receivableAmount - receivedAmount;
            records.add(buildRow(rowNo++, formatDate(numberValue(row.get("businessTime")).longValue()),
                    stringValue(row.get("orderNo")), stringValue(row.get("summary")),
                    receivableAmount, receivedAmount, balance));
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

    private List<Map<String, Object>> queryDetailRows(String customerId, Long startTime,
                                                       Long endTime, String cashierId) {
        String ownerCondition = cashierId == null ? "" : " AND cashier_id = ?";
        String sql = "SELECT business_time AS businessTime, order_no AS orderNo, summary, " +
                "receivable_amount AS receivableAmount, received_amount AS receivedAmount FROM (" +
                "SELECT " + BUSINESS_TIME_SQL + " AS business_time, app_sale_order.order_no AS order_no, " +
                "CASE WHEN note IS NOT NULL AND trim(note) <> '' THEN note " +
                "WHEN order_type = 2 THEN '销售退货' ELSE '销售出货' END AS summary, " +
                SALE_DEBT_AMOUNT_SQL + " AS receivable_amount, 0 AS received_amount " +
                "FROM app_sale_order " +
                "LEFT JOIN (SELECT order_no, SUM(COALESCE(amount, 0)) AS linked_amount " +
                "FROM app_receive_payment_amount_item GROUP BY order_no) receive_item " +
                "ON app_sale_order.order_no = receive_item.order_no " +
                "WHERE customer_id = ? AND status = 1 AND COALESCE(is_del, 0) = 0 " +
                "AND " + SALE_DEBT_FILTER_SQL + " AND ABS(" + SALE_DEBT_AMOUNT_SQL + ") >= 0.005" +
                ownerCondition +
                " UNION ALL " +
                "SELECT " + BUSINESS_TIME_SQL + " AS business_time, order_no, " +
                "CASE WHEN note IS NULL OR trim(note) = '' THEN '收款' ELSE note END AS summary, " +
                "0 AS receivable_amount, COALESCE(amount, 0) AS received_amount " +
                "FROM app_receive_payment_voucher WHERE customer_id = ? AND status = 1 AND COALESCE(is_del, 0) = 0" +
                ownerCondition +
                ") detail WHERE 1 = 1";
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
        sql += " ORDER BY business_time ASC, order_no ASC";
        return jdbcTemplate.queryForList(sql, params.toArray());
    }

    private double calculateOpeningBalance(AppCustomer customer, Long startTime, String cashierId) {
        double defaultPayable = customer.getDefPayable() == null ? 0D : customer.getDefPayable();
        if (startTime == null) {
            return defaultPayable;
        }
        double saleAmount = sumSaleDebtBeforeDate(customer.getId().toString(), startTime, cashierId);
        double receivedAmount = sumReceivedBeforeDate(customer.getId().toString(), startTime, cashierId);
        return defaultPayable + saleAmount - receivedAmount;
    }

    private double sumSaleDebtBeforeDate(String customerId, Long startTime, String cashierId) {
        String ownerCondition = cashierId == null ? "" : " AND cashier_id = ?";
        String sql = "SELECT COALESCE(SUM(" + SALE_DEBT_AMOUNT_SQL + "), 0) FROM app_sale_order " +
                "LEFT JOIN (SELECT order_no, SUM(COALESCE(amount, 0)) AS linked_amount " +
                "FROM app_receive_payment_amount_item GROUP BY order_no) receive_item " +
                "ON app_sale_order.order_no = receive_item.order_no " +
                "WHERE customer_id = ? AND status = 1 AND COALESCE(is_del, 0) = 0 " +
                "AND " + SALE_DEBT_FILTER_SQL + " AND ABS(" + SALE_DEBT_AMOUNT_SQL + ") >= 0.005" +
                ownerCondition + " AND " + BUSINESS_TIME_SQL + " < ?";
        return querySum(sql, customerId, startTime, cashierId);
    }

    private double sumReceivedBeforeDate(String customerId, Long startTime, String cashierId) {
        String ownerCondition = cashierId == null ? "" : " AND cashier_id = ?";
        String sql = "SELECT COALESCE(SUM(COALESCE(amount, 0)), 0) FROM app_receive_payment_voucher " +
                "WHERE customer_id = ? AND status = 1 AND COALESCE(is_del, 0) = 0" +
                ownerCondition + " AND " + BUSINESS_TIME_SQL + " < ?";
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

    private JSONObject buildRow(int rowNo, String businessDate, String orderNo, String summary,
                                double receivableAmount, double receivedAmount, double endingBalance) {
        JSONObject row = new JSONObject();
        row.put("rowNo", rowNo);
        row.put("businessDate", businessDate);
        row.put("orderNo", orderNo);
        row.put("summary", summary);
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

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
