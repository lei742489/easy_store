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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/user/appPurchaseStatistics")
public class AppPurchaseStatisticsController {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String BUSINESS_TIME_SQL =
            "CASE WHEN typeof(o.create_time) IN ('integer', 'real') " +
                    "THEN CAST(o.create_time AS INTEGER) " +
                    "ELSE COALESCE(CAST(strftime('%s', o.create_time) AS INTEGER) * 1000, 0) END";

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
        String groupBySql = " GROUP BY i.goods_id, COALESCE(g.title, ''), COALESCE(i.unit, g.unit, '')";
        String aggregateSql = "SELECT i.goods_id AS goodsId, COALESCE(g.title, '') AS goodsName, " +
                "COALESCE(i.unit, g.unit, '') AS unit, COALESCE(SUM(i.quantity), 0) AS quantity, " +
                "COALESCE(SUM(i.total_amount), 0) AS amount " +
                baseSql() + condition.whereSql + groupBySql;

        List<Object> pageParams = new ArrayList<>(condition.params);
        pageParams.add(pageSize);
        pageParams.add((current - 1) * pageSize);
        JSONArray records = new JSONArray();
        int rowNo = (current - 1) * pageSize + 1;
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                aggregateSql + " ORDER BY goodsName, goodsId LIMIT ? OFFSET ?", pageParams.toArray())) {
            JSONObject record = new JSONObject();
            record.put("rowNo", rowNo++);
            record.put("goodsId", row.get("goodsId"));
            record.put("goodsName", stringValue(row.get("goodsName")));
            record.put("unit", stringValue(row.get("unit")));
            record.put("quantity", numberValue(row.get("quantity")));
            record.put("amount", numberValue(row.get("amount")));
            records.add(record);
        }

        String countSql = "SELECT COUNT(*) FROM (SELECT 1 " + baseSql() + condition.whereSql +
                groupBySql + ") t";
        Integer total = jdbcTemplate.queryForObject(countSql, condition.params.toArray(), Integer.class);
        String totalSql = "SELECT COALESCE(SUM(i.quantity), 0) AS quantityTotal, " +
                "COALESCE(SUM(i.total_amount), 0) AS amountTotal " + baseSql() + condition.whereSql;
        Map<String, Object> totalRow = jdbcTemplate.queryForMap(totalSql, condition.params.toArray());

        JSONObject result = new JSONObject();
        result.put("current", current);
        result.put("pageSize", pageSize);
        result.put("total", total == null ? 0 : total);
        result.put("quantityTotal", numberValue(totalRow.get("quantityTotal")));
        result.put("amountTotal", numberValue(totalRow.get("amountTotal")));
        result.put("records", records);
        return Result.ok(result);
    }

    @PostMapping("detail")
    public Result<?> detail(@RequestBody JSONObject param) {
        AppUser user = currentUser(param);
        String goodsId = param.getString("goodsId");
        if (StringUtils.isEmpty(goodsId)) {
            throw new AppRunTimeException("请选择货品");
        }
        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        validateRange(startTime, endTime);

        QueryCondition condition = buildCondition(param, user, startTime, endTime);
        String sql = "SELECT o.order_no AS orderNo, " + BUSINESS_TIME_SQL + " AS businessTime, " +
                "COALESCE(s.name, '') AS supplierName, COALESCE(i.quantity, 0) AS quantity, " +
                "COALESCE(i.unit_price, 0) AS unitPrice, COALESCE(i.total_amount, 0) AS amount, " +
                "COALESCE(o.discount_rate, 100) AS discountRate, " +
                "COALESCE(NULLIF(i.note, ''), o.note, '') AS note " +
                detailBaseSql() + condition.whereSql + " AND i.goods_id = ? " +
                " ORDER BY businessTime DESC, i.id DESC LIMIT 1000";
        List<Object> params = new ArrayList<>(condition.params);
        params.add(goodsId);

        JSONArray records = new JSONArray();
        int rowNo = 1;
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, params.toArray())) {
            double amount = numberValue(row.get("amount"));
            double discountRate = numberValue(row.get("discountRate"));
            if (discountRate <= 0) discountRate = 100D;
            double payableAmount = amount * discountRate / 100D;
            JSONObject record = new JSONObject();
            record.put("rowNo", rowNo++);
            record.put("orderNo", stringValue(row.get("orderNo")));
            record.put("businessDate", formatDate(numberValue(row.get("businessTime"))));
            record.put("supplierName", stringValue(row.get("supplierName")));
            record.put("quantity", numberValue(row.get("quantity")));
            record.put("unitPrice", numberValue(row.get("unitPrice")));
            record.put("amount", amount);
            record.put("discountAmount", amount - payableAmount);
            record.put("payableAmount", payableAmount);
            record.put("note", stringValue(row.get("note")));
            records.add(record);
        }
        return Result.ok(records);
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
                "AND COALESCE(o.is_del, 0) = 0 AND COALESCE(i.is_del, 0) = 0");
        List<Object> params = new ArrayList<>();
        String cashierId = isRoot(user) ? param.getString("cashierId") : String.valueOf(user.getId());
        appendEquals(whereSql, params, "o.cashier_id", cashierId);
        appendEquals(whereSql, params, "o.supplier_id", param.getString("supplierId"));
        appendEquals(whereSql, params, "i.category_id", param.getString("categoryId"));

        String goodsKey = param.getString("goodsKey");
        if (StringUtils.isNotEmpty(goodsKey)) {
            String likeKey = "%" + goodsKey.trim() + "%";
            whereSql.append(" AND (g.title LIKE ? OR g.goods_code LIKE ? OR g.py_code LIKE ?)");
            params.add(likeKey);
            params.add(likeKey);
            params.add(likeKey);
        }
        if (startTime != null) {
            whereSql.append(" AND ").append(BUSINESS_TIME_SQL).append(" >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            whereSql.append(" AND ").append(BUSINESS_TIME_SQL).append(" <= ?");
            params.add(endTime);
        }
        return new QueryCondition(whereSql.toString(), params);
    }

    private String baseSql() {
        return " FROM app_purchase_order_item i " +
                "INNER JOIN app_purchase_order o ON o.id = i.order_id " +
                "LEFT JOIN app_goods g ON g.id = i.goods_id ";
    }

    private String detailBaseSql() {
        return baseSql() + "LEFT JOIN app_supplier s ON s.id = o.supplier_id ";
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

    private String formatDate(double value) {
        if (value <= 0) return "";
        return Instant.ofEpochMilli((long) value).atZone(ZONE_ID).format(DATE_FORMATTER);
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
