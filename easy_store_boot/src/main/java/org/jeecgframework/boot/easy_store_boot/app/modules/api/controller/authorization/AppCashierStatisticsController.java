package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;
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
@RequestMapping("api/user/appCashierStatistics")
public class AppCashierStatisticsController {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SALE_BUSINESS_TIME_SQL =
            "CASE WHEN typeof(o.create_time) IN ('integer', 'real') " +
                    "THEN CAST(o.create_time AS INTEGER) " +
                    "ELSE COALESCE(CAST(strftime('%s', o.create_time) AS INTEGER) * 1000, 0) END";
    private static final String DISCOUNTED_AMOUNT_SQL =
            "COALESCE(i.total_amount, 0) * " +
                    "(CASE WHEN COALESCE(o.discount_rate, 100) <= 0 THEN 100 " +
                    "ELSE COALESCE(o.discount_rate, 100) END) / 100.0";
    private static final String CASHIER_NAME_SQL =
            "COALESCE(NULLIF(o.cashier_name, ''), NULLIF(u.real_name, ''), COALESCE(u.user_name, ''))";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IAppUserService userService;

    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param) {
        AppUser user = currentUser(param);
        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        validateRange(startTime, endTime);

        int current = Math.max(param.getIntValue("current"), 1);
        int pageSize = param.getIntValue("pageSize");
        if (pageSize < 1) pageSize = 50;

        QueryCondition condition = buildCondition(param, user, startTime, endTime);
        String groupBySql = " GROUP BY o.cashier_id, " + CASHIER_NAME_SQL + ", COALESCE(u.commission_rate, 0)";
        String aggregateSql = "SELECT o.cashier_id AS cashierId, " + CASHIER_NAME_SQL + " AS cashierName, " +
                "COALESCE(u.commission_rate, 0) AS commissionRate, COALESCE(SUM(i.quantity), 0) AS quantity, " +
                "COALESCE(SUM(" + DISCOUNTED_AMOUNT_SQL + "), 0) AS salesAmount, " +
                "COALESCE(SUM(COALESCE(i.gross_profit, 0)), 0) AS profitAmount " +
                baseSql() + condition.whereSql + groupBySql;

        List<Object> pageParams = new ArrayList<>(condition.params);
        pageParams.add(pageSize);
        pageParams.add((current - 1) * pageSize);
        JSONArray records = new JSONArray();
        int rowNo = (current - 1) * pageSize + 1;
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                aggregateSql + " ORDER BY cashierName, cashierId LIMIT ? OFFSET ?", pageParams.toArray())) {
            records.add(buildRecord(rowNo++, row));
        }

        String countSql = "SELECT COUNT(*) FROM (SELECT 1 " + baseSql() + condition.whereSql + groupBySql + ") t";
        Integer total = jdbcTemplate.queryForObject(countSql, condition.params.toArray(), Integer.class);
        String totalSql = "SELECT COALESCE(SUM(i.quantity), 0) AS quantityTotal, " +
                "COALESCE(SUM(" + DISCOUNTED_AMOUNT_SQL + "), 0) AS salesTotal, " +
                "COALESCE(SUM(COALESCE(i.gross_profit, 0)), 0) AS profitTotal " +
                baseSql() + condition.whereSql;
        Map<String, Object> totalRow = jdbcTemplate.queryForMap(totalSql, condition.params.toArray());
        double salesTotal = numberValue(totalRow.get("salesTotal"));
        double profitTotal = numberValue(totalRow.get("profitTotal"));
        double commissionTotal = 0D;
        for (Map<String, Object> row : jdbcTemplate.queryForList(aggregateSql, condition.params.toArray())) {
            commissionTotal += numberValue(row.get("profitAmount"))
                    * normalizeRate(numberValue(row.get("commissionRate"))) / 100D;
        }

        JSONObject result = new JSONObject();
        result.put("current", current);
        result.put("pageSize", pageSize);
        result.put("total", total == null ? 0 : total);
        result.put("quantityTotal", numberValue(totalRow.get("quantityTotal")));
        result.put("salesTotal", salesTotal);
        result.put("costTotal", salesTotal - profitTotal);
        result.put("profitTotal", profitTotal);
        result.put("commissionTotal", commissionTotal);
        result.put("profitRateTotal", calculateRate(profitTotal, salesTotal));
        result.put("records", records);
        return Result.ok(result);
    }

    @PostMapping("detail")
    public Result<?> detail(@RequestBody JSONObject param) {
        AppUser currentUser = currentUser(param);
        String cashierId = param.getString("cashierId");
        if (StringUtils.isEmpty(cashierId)) {
            throw new AppRunTimeException("请选择营业员");
        }
        if (!isRoot(currentUser) && !cashierId.equals(String.valueOf(currentUser.getId()))) {
            throw new AppRunTimeException("无权查看其他营业员统计");
        }
        AppUser cashier = userService.getById(cashierId);
        if (cashier == null) {
            throw new AppRunTimeException("营业员不存在或已删除");
        }

        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        validateRange(startTime, endTime);
        QueryCondition condition = buildCondition(param, currentUser, startTime, endTime);
        boolean groupByGoods = "goods".equals(param.getString("groupBy"));

        String selectSql;
        String joinSql;
        String groupBySql;
        if (groupByGoods) {
            selectSql = "SELECT i.goods_id AS groupId, " +
                    "COALESCE(NULLIF(g.title, ''), NULLIF(i.goods_id, ''), '未设置货品') AS groupName, " +
                    "COALESCE(i.unit, g.unit, '') AS unit, ";
            joinSql = "LEFT JOIN app_goods g ON g.id = i.goods_id ";
            groupBySql = " GROUP BY i.goods_id, COALESCE(NULLIF(g.title, ''), NULLIF(i.goods_id, ''), '未设置货品'), " +
                    "COALESCE(i.unit, g.unit, '')";
        } else {
            selectSql = "SELECT o.customer_id AS groupId, " +
                    "c.name AS groupName, '' AS unit, ";
            joinSql = "";
            groupBySql = " GROUP BY o.customer_id, c.name";
        }
        String sql = selectSql + "COALESCE(SUM(i.quantity), 0) AS quantity, " +
                "COALESCE(SUM(" + DISCOUNTED_AMOUNT_SQL + "), 0) AS salesAmount, " +
                "COALESCE(SUM(COALESCE(i.gross_profit, 0)), 0) AS profitAmount " +
                baseSql() + joinSql + condition.whereSql + groupBySql + " ORDER BY groupName, groupId";

        JSONArray records = new JSONArray();
        int rowNo = 1;
        double commissionRate = normalizeRate(cashier.getCommissionRate());
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, condition.params.toArray())) {
            double salesAmount = numberValue(row.get("salesAmount"));
            double profitAmount = numberValue(row.get("profitAmount"));
            JSONObject record = new JSONObject();
            record.put("rowNo", rowNo++);
            record.put("groupId", row.get("groupId"));
            record.put("groupName", stringValue(row.get("groupName")));
            record.put("unit", stringValue(row.get("unit")));
            record.put("quantity", numberValue(row.get("quantity")));
            record.put("salesAmount", salesAmount);
            record.put("costAmount", salesAmount - profitAmount);
            record.put("profitAmount", profitAmount);
            record.put("commissionAmount", profitAmount * commissionRate / 100D);
            record.put("profitRate", calculateRate(profitAmount, salesAmount));
            records.add(record);
        }
        return Result.ok(records);
    }

    private JSONObject buildRecord(int rowNo, Map<String, Object> row) {
        double salesAmount = numberValue(row.get("salesAmount"));
        double profitAmount = numberValue(row.get("profitAmount"));
        double commissionRate = normalizeRate(numberValue(row.get("commissionRate")));
        JSONObject record = new JSONObject();
        record.put("rowNo", rowNo);
        record.put("cashierId", row.get("cashierId"));
        record.put("cashierName", stringValue(row.get("cashierName")));
        record.put("commissionRate", commissionRate);
        record.put("quantity", numberValue(row.get("quantity")));
        record.put("salesAmount", salesAmount);
        record.put("costAmount", salesAmount - profitAmount);
        record.put("profitAmount", profitAmount);
        record.put("commissionAmount", profitAmount * commissionRate / 100D);
        record.put("profitRate", calculateRate(profitAmount, salesAmount));
        return record;
    }

    private AppUser currentUser(JSONObject param) {
        AppUser user = userService.getById(param.getString("userId"));
        if (user == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }
        return user;
    }

    private QueryCondition buildCondition(JSONObject param, AppUser user, Long startTime, Long endTime) {
        StringBuilder whereSql = new StringBuilder(" WHERE o.status = 1 " +
                "AND COALESCE(o.is_del, 0) = 0 AND COALESCE(i.is_del, 0) = 0 " +
                "AND COALESCE(u.is_del, 0) = 0 AND COALESCE(c.is_del, 0) = 0 " +
                "AND trim(COALESCE(c.name, '')) <> ''");
        List<Object> params = new ArrayList<>();
        String cashierId = isRoot(user) ? param.getString("cashierId") : String.valueOf(user.getId());
        appendEquals(whereSql, params, "o.cashier_id", cashierId);
        if (startTime != null) {
            whereSql.append(" AND ").append(SALE_BUSINESS_TIME_SQL).append(" >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            whereSql.append(" AND ").append(SALE_BUSINESS_TIME_SQL).append(" <= ?");
            params.add(endTime);
        }
        return new QueryCondition(whereSql.toString(), params);
    }

    private String baseSql() {
        return " FROM app_sale_order_item i " +
                "INNER JOIN app_sale_order o ON o.id = i.order_id " +
                "INNER JOIN app_user u ON u.id = o.cashier_id " +
                "INNER JOIN app_customer c ON c.id = o.customer_id ";
    }

    private void appendEquals(StringBuilder whereSql, List<Object> params, String column, String value) {
        if (StringUtils.isEmpty(value)) return;
        whereSql.append(" AND ").append(column).append(" = ?");
        params.add(value);
    }

    private boolean isRoot(AppUser user) {
        return user.getIsRoot() != null && user.getIsRoot() == 1;
    }

    private void validateRange(Long startTime, Long endTime) {
        if (startTime != null && endTime != null && startTime > endTime) {
            throw new AppRunTimeException("开始日期不能晚于结束日期");
        }
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

    private double calculateRate(double numerator, double denominator) {
        return Math.abs(denominator) < 0.000001D ? 0D : numerator * 100D / denominator;
    }

    private double normalizeRate(Double value) {
        return value == null || value < 0D ? 0D : value;
    }

    private double numberValue(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value == null) return 0D;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
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
