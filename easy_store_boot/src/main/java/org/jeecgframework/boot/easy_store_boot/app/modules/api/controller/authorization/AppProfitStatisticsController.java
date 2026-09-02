package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.CustomerSupplierKeywordResolver;
import org.jeecgframework.boot.easy_store_boot.app.common.DatabaseDialect;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/user/appProfitStatistics")
public class AppProfitStatisticsController {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DISCOUNTED_AMOUNT_SQL =
            "COALESCE(i.total_amount, 0) * " +
                    "(CASE WHEN COALESCE(o.discount_rate, 100) <= 0 THEN 100 " +
                    "ELSE COALESCE(o.discount_rate, 100) END) / 100.0";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IAppUserService userService;
    @Autowired
    private DatabaseDialect databaseDialect;
    @Autowired
    private CustomerSupplierKeywordResolver customerSupplierKeywordResolver;

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

        int current = Math.max(param.getIntValue("current"), 1);
        int pageSize = param.getIntValue("pageSize");
        if (pageSize < 1) {
            pageSize = 50;
        }

        QueryCondition condition = buildCondition(param, user, startTime, endTime);
        String groupBySql = " GROUP BY o.customer_id, COALESCE(c.name, '')";
        String aggregateSql = "SELECT o.customer_id AS customerId, COALESCE(c.name, '') AS customerName, " +
                "COALESCE(SUM(" + DISCOUNTED_AMOUNT_SQL + "), 0) AS discountedAmount, " +
                "COALESCE(SUM(COALESCE(i.gross_profit, 0)), 0) AS profitAmount " +
                baseSql() + condition.whereSql + groupBySql;
        String pageSql = aggregateSql + " ORDER BY customerName, customerId LIMIT ? OFFSET ?";
        List<Object> pageParams = new ArrayList<>(condition.params);
        pageParams.add(pageSize);
        pageParams.add((current - 1) * pageSize);

        JSONArray records = new JSONArray();
        int rowNo = (current - 1) * pageSize + 1;
        for (Map<String, Object> row : jdbcTemplate.queryForList(pageSql, pageParams.toArray())) {
            double discountedAmount = numberValue(row.get("discountedAmount"));
            double profitAmount = numberValue(row.get("profitAmount"));
            JSONObject record = new JSONObject();
            record.put("rowNo", rowNo++);
            record.put("customerId", row.get("customerId"));
            record.put("customerName", stringValue(row.get("customerName")));
            record.put("discountedAmount", discountedAmount);
            record.put("costAmount", discountedAmount - profitAmount);
            record.put("profitAmount", profitAmount);
            record.put("profitRate", calculateProfitRate(profitAmount, discountedAmount));
            records.add(record);
        }

        String countSql = "SELECT COUNT(*) FROM (SELECT 1 " + baseSql() + condition.whereSql +
                groupBySql + ") t";
        Integer total = jdbcTemplate.queryForObject(countSql, condition.params.toArray(), Integer.class);
        String totalSql = "SELECT COALESCE(SUM(" + DISCOUNTED_AMOUNT_SQL + "), 0) AS discountedAmount, " +
                "COALESCE(SUM(COALESCE(i.gross_profit, 0)), 0) AS profitAmount " +
                baseSql() + condition.whereSql;
        Map<String, Object> totalRow = jdbcTemplate.queryForMap(totalSql, condition.params.toArray());
        double discountedTotal = numberValue(totalRow.get("discountedAmount"));
        double profitTotal = numberValue(totalRow.get("profitAmount"));

        JSONObject result = new JSONObject();
        result.put("current", current);
        result.put("pageSize", pageSize);
        result.put("total", total == null ? 0 : total);
        result.put("discountedTotal", discountedTotal);
        result.put("costTotal", discountedTotal - profitTotal);
        result.put("profitTotal", profitTotal);
        result.put("profitRateTotal", calculateProfitRate(profitTotal, discountedTotal));
        result.put("records", records);
        return Result.ok(result);
    }

    @PostMapping("detail")
    public Result<?> detail(@RequestBody JSONObject param) {
        AppUser user = userService.getById(param.getString("userId"));
        if (user == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }
        String customerId = param.getString("customerId");
        if (StringUtils.isEmpty(customerId)) {
            throw new AppRunTimeException("请选择往来单位");
        }

        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        if (startTime != null && endTime != null && startTime > endTime) {
            throw new AppRunTimeException("开始日期不能晚于结束日期");
        }

        QueryCondition condition = buildCondition(param, user, startTime, endTime);
        String sql = "SELECT " + saleBusinessTimeSql() + " AS businessTime, " +
                "COALESCE(g.title, '') AS goodsName, COALESCE(i.unit, g.unit, '') AS unit, " +
                "COALESCE(i.quantity, 0) AS quantity, COALESCE(i.unit_price, 0) AS unitPrice, " +
                DISCOUNTED_AMOUNT_SQL + " AS discountedAmount, " +
                "COALESCE(i.gross_profit, 0) AS profitAmount " +
                baseSql() + "LEFT JOIN app_goods g ON g.id = i.goods_id " +
                condition.whereSql + " ORDER BY businessTime ASC, i.id ASC";

        JSONArray records = new JSONArray();
        int rowNo = 1;
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, condition.params.toArray())) {
            double discountedAmount = numberValue(row.get("discountedAmount"));
            double profitAmount = numberValue(row.get("profitAmount"));
            double unitPrice = numberValue(row.get("unitPrice"));
            double discountRate = calculateDiscountRate(discountedAmount, unitPrice, numberValue(row.get("quantity")));
            JSONObject record = new JSONObject();
            record.put("rowNo", rowNo++);
            record.put("businessDate", formatDate(numberValue(row.get("businessTime"))));
            record.put("goodsName", stringValue(row.get("goodsName")));
            record.put("unit", stringValue(row.get("unit")));
            record.put("quantity", numberValue(row.get("quantity")));
            record.put("discountedUnitPrice", unitPrice * discountRate / 100D);
            record.put("discountedAmount", discountedAmount);
            record.put("costAmount", discountedAmount - profitAmount);
            record.put("profitAmount", profitAmount);
            record.put("profitRate", calculateProfitRate(profitAmount, discountedAmount));
            records.add(record);
        }
        return Result.ok(records);
    }

    private QueryCondition buildCondition(JSONObject param, AppUser user, Long startTime, Long endTime) {
        StringBuilder whereSql = new StringBuilder(" WHERE o.status = 1 " +
                "AND COALESCE(o.is_del, 0) = 0 AND COALESCE(i.is_del, 0) = 0 " +
                "AND COALESCE(c.is_del, 0) = 0 AND trim(COALESCE(c.name, '')) <> ''");
        List<Object> params = new ArrayList<>();

        String cashierId = isRoot(user) ? null : String.valueOf(user.getId());
        appendEquals(whereSql, params, "o.cashier_id", cashierId);
        appendCustomerFilter(whereSql, params, param.getString("customerId"));
        if (startTime != null) {
            whereSql.append(" AND ").append(saleBusinessTimeSql()).append(" >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            whereSql.append(" AND ").append(saleBusinessTimeSql()).append(" <= ?");
            params.add(endTime);
        }
        return new QueryCondition(whereSql.toString(), params);
    }

    private String baseSql() {
        return " FROM app_sale_order_item i " +
                "INNER JOIN app_sale_order o ON o.id = i.order_id " +
                "INNER JOIN app_customer c ON c.id = o.customer_id ";
    }

    private void appendEquals(StringBuilder whereSql, List<Object> params, String column, String value) {
        if (StringUtils.isEmpty(value)) {
            return;
        }
        whereSql.append(" AND ").append(column).append(" = ?");
        params.add(value);
    }

    private void appendCustomerFilter(StringBuilder whereSql, List<Object> params, String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return;
        }
        List<String> customerIds = customerSupplierKeywordResolver.resolveCustomerIds(rawValue);
        if (customerIds.isEmpty()) {
            whereSql.append(" AND 1 = 0");
            return;
        }
        whereSql.append(" AND o.customer_id IN (");
        for (int i = 0; i < customerIds.size(); i++) {
            if (i > 0) {
                whereSql.append(", ");
            }
            whereSql.append("?");
            params.add(customerIds.get(i));
        }
        whereSql.append(")");
    }

    private boolean isRoot(AppUser user) {
        return user.getIsRoot() != null && user.getIsRoot() == 1;
    }

    private Long parseStartTime(String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER).atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        } catch (Exception e) {
            throw new AppRunTimeException("开始日期格式错误");
        }
    }

    private Long parseEndTime(String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER).plusDays(1)
                    .atStartOfDay(ZONE_ID).toInstant().toEpochMilli() - 1;
        } catch (Exception e) {
            throw new AppRunTimeException("结束日期格式错误");
        }
    }

    private double calculateProfitRate(double profitAmount, double discountedAmount) {
        if (Math.abs(discountedAmount) < 0.000001D) {
            return 0D;
        }
        return profitAmount * 100D / discountedAmount;
    }

    private double calculateDiscountRate(double discountedAmount, double unitPrice, double quantity) {
        double amount = unitPrice * quantity;
        if (Math.abs(amount) < 0.000001D) {
            return 100D;
        }
        return discountedAmount * 100D / amount;
    }

    private double numberValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String formatDate(double value) {
        if (value <= 0) {
            return "";
        }
        return java.time.Instant.ofEpochMilli((long) value)
                .atZone(ZONE_ID)
                .format(DATE_FORMATTER);
    }

    private String saleBusinessTimeSql() {
        return databaseDialect.epochMillis("o.create_time");
    }

    private static class QueryCondition {
        private final String whereSql;
        private final List<Object> params;

        private QueryCondition(String whereSql, List<Object> params) {
            this.whereSql = whereSql;
            this.params = params;
        }
    }
}
