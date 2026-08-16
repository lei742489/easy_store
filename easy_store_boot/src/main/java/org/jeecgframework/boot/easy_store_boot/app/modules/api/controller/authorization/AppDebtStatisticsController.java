package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/user/appDebtStatistics")
public class AppDebtStatisticsController {

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
        AppUser user = userService.getById(param.getString("userId"));
        if (user == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }

        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        if (startTime != null && endTime != null && startTime > endTime) {
            throw new AppRunTimeException("开始日期不能晚于结束日期");
        }

        String customerId = param.getString("customerId");
        List<AppCustomer> customers = queryCustomers(customerId);
        boolean rootUser = user.getIsRoot() != null && user.getIsRoot() == 1;
        String cashierId = rootUser ? null : String.valueOf(user.getId());
        Map<String, Double> openingSaleAmounts = startTime == null
                ? new HashMap<>()
                : sumSaleDebtByCustomer(customerId, null, startTime, true, cashierId);
        Map<String, Double> openingReceivedAmounts = startTime == null
                ? new HashMap<>()
                : sumReceivedByCustomer(customerId, null, startTime, true, cashierId);
        Map<String, Double> receivableAmounts =
                sumSaleDebtByCustomer(customerId, startTime, endTime, false, cashierId);
        Map<String, Double> receivedAmounts =
                sumReceivedByCustomer(customerId, startTime, endTime, false, cashierId);

        JSONArray records = new JSONArray();
        double openingTotal = 0D;
        double receivableTotal = 0D;
        double receivedTotal = 0D;
        double endingTotal = 0D;
        int rowNo = 1;

        for (AppCustomer customer : customers) {
            String currentCustomerId = customer.getId().toString();
            double defaultPayable = customer.getDefPayable() == null ? 0D : customer.getDefPayable();
            double openingBalance = defaultPayable
                    + amountOf(openingSaleAmounts, currentCustomerId)
                    - amountOf(openingReceivedAmounts, currentCustomerId);
            double receivableAmount = amountOf(receivableAmounts, currentCustomerId);
            double receivedAmount = amountOf(receivedAmounts, currentCustomerId);
            double endingBalance = openingBalance + receivableAmount - receivedAmount;
            if (Math.abs(endingBalance) < 0.005D) {
                continue;
            }

            JSONObject row = new JSONObject();
            row.put("rowNo", rowNo++);
            row.put("customerId", customer.getId());
            row.put("customerName", customer.getName());
            row.put("openingBalance", openingBalance);
            row.put("receivableAmount", receivableAmount);
            row.put("receivedAmount", receivedAmount);
            row.put("endingBalance", endingBalance);
            records.add(row);

            openingTotal += openingBalance;
            receivableTotal += receivableAmount;
            receivedTotal += receivedAmount;
            endingTotal += endingBalance;
        }

        JSONObject result = new JSONObject();
        result.put("openingTotal", openingTotal);
        result.put("receivableTotal", receivableTotal);
        result.put("receivedTotal", receivedTotal);
        result.put("endingTotal", endingTotal);
        result.put("records", records);
        return Result.ok(result);
    }

    private List<AppCustomer> queryCustomers(String customerId) {
        QueryWrapper<AppCustomer> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("name");
        if (StringUtils.isNotEmpty(customerId)) {
            wrapper.eq("id", customerId);
        }
        return customerService.list(wrapper);
    }

    private Map<String, Double> sumSaleDebtByCustomer(String customerId, Long startTime, Long endTime,
                                                       boolean beforeStart, String cashierId) {
        String sql = baseSaleDebtSql();
        List<Object> params = new ArrayList<>();
        if (StringUtils.isNotEmpty(customerId)) {
            sql += " AND app_sale_order.customer_id = ?";
            params.add(customerId);
        }
        if (cashierId != null) {
            sql += " AND app_sale_order.cashier_id = ?";
            params.add(cashierId);
        }
        if (beforeStart && endTime != null) {
            sql += " AND " + SALE_BUSINESS_TIME_SQL + " < ?";
            params.add(endTime);
        } else if (startTime != null) {
            sql += " AND " + SALE_BUSINESS_TIME_SQL + " >= ?";
            params.add(startTime);
        }
        if (!beforeStart && endTime != null) {
            sql += " AND " + SALE_BUSINESS_TIME_SQL + " <= ?";
            params.add(endTime);
        }
        sql += " GROUP BY app_sale_order.customer_id";
        return queryGroupedSum(sql, params);
    }

    private String baseSaleDebtSql() {
        return "SELECT app_sale_order.customer_id AS customerId, " +
                "COALESCE(SUM(" + SALE_DEBT_AMOUNT_SQL + "), 0) AS amount FROM app_sale_order " +
                "LEFT JOIN (SELECT order_no, SUM(COALESCE(amount, 0)) AS linked_amount " +
                "FROM app_receive_payment_amount_item GROUP BY order_no) receive_item " +
                "ON app_sale_order.order_no = receive_item.order_no " +
                "WHERE app_sale_order.status = 1 " +
                "AND COALESCE(app_sale_order.is_del, 0) = 0 " +
                "AND " + SALE_DEBT_FILTER_SQL + " AND ABS(" + SALE_DEBT_AMOUNT_SQL + ") >= 0.005";
    }

    private Map<String, Double> sumReceivedByCustomer(String customerId, Long startTime, Long endTime,
                                                       boolean beforeStart, String cashierId) {
        String sql = baseReceivedSql();
        List<Object> params = new ArrayList<>();
        if (StringUtils.isNotEmpty(customerId)) {
            sql += " AND app_receive_payment_voucher.customer_id = ?";
            params.add(customerId);
        }
        if (cashierId != null) {
            sql += " AND app_receive_payment_voucher.cashier_id = ?";
            params.add(cashierId);
        }
        if (beforeStart && endTime != null) {
            sql += " AND " + RECEIVE_BUSINESS_TIME_SQL + " < ?";
            params.add(endTime);
        } else if (startTime != null) {
            sql += " AND " + RECEIVE_BUSINESS_TIME_SQL + " >= ?";
            params.add(startTime);
        }
        if (!beforeStart && endTime != null) {
            sql += " AND " + RECEIVE_BUSINESS_TIME_SQL + " <= ?";
            params.add(endTime);
        }
        sql += " GROUP BY app_receive_payment_voucher.customer_id";
        return queryGroupedSum(sql, params);
    }

    private String baseReceivedSql() {
        return "SELECT app_receive_payment_voucher.customer_id AS customerId, " +
                "COALESCE(SUM(COALESCE(app_receive_payment_voucher.amount, 0)), 0) AS amount " +
                "FROM app_receive_payment_voucher " +
                "WHERE app_receive_payment_voucher.status = 1 " +
                "AND COALESCE(app_receive_payment_voucher.is_del, 0) = 0";
    }

    private Map<String, Double> queryGroupedSum(String sql, List<Object> params) {
        Map<String, Double> amountMap = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, params.toArray())) {
            Object customerId = row.get("customerId");
            Object amount = row.get("amount");
            if (customerId instanceof Number && amount instanceof Number) {
                amountMap.put(String.valueOf(((Number) customerId).intValue()),
                        ((Number) amount).doubleValue());
            } else if (customerId != null && amount != null) {
                try {
                    amountMap.put(customerId.toString(), Double.parseDouble(amount.toString()));
                } catch (NumberFormatException ignored) {
                    // Skip invalid aggregate values.
                }
            }
        }
        return amountMap;
    }

    private double amountOf(Map<String, Double> amountMap, String customerId) {
        Double amount = amountMap.get(customerId);
        return amount == null ? 0D : amount;
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
}
