package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;
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
@RequestMapping("api/user/appFundStatistics")
public class AppFundStatisticsController {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String ITEM_SALE_INCOME = "sale_income";
    private static final String ITEM_PURCHASE_EXPENSE = "purchase_expense";
    private static final String ITEM_RECEIVE_DEBT = "receive_debt";
    private static final String ITEM_PAYMENT_DEBT = "payment_debt";
    private static final String ITEM_FREIGHT = "freight";
    private static final FixedQueryItem[] FIXED_QUERY_ITEMS = {
            new FixedQueryItem(ITEM_SALE_INCOME, "销售收入", "income"),
            new FixedQueryItem(ITEM_PURCHASE_EXPENSE, "进货支出", "expense"),
            new FixedQueryItem(ITEM_RECEIVE_DEBT, "收定金/欠款", "income"),
            new FixedQueryItem(ITEM_PAYMENT_DEBT, "付定金/欠款", "expense"),
            new FixedQueryItem(ITEM_FREIGHT, "运费", "mixed")
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IAppUserService userService;
    @Autowired
    private DatabaseDialect databaseDialect;

    @PostMapping("items")
    public Result<?> items(@RequestBody JSONObject param) {
        currentUser(param);
        String itemType = normalizeItemType(param.getString("itemType"));
        JSONArray result = new JSONArray();
        for (FixedQueryItem item : FIXED_QUERY_ITEMS) {
            if (!matchesItemType(itemType, item.itemType)) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("itemKey", item.itemKey);
            row.put("name", item.name);
            row.put("itemType", item.itemType);
            row.put("fixed", true);
            result.add(row);
        }

        StringBuilder sql = new StringBuilder(
                "SELECT id, name, item_type AS itemType, " +
                        "participate_performance AS participatePerformance, disabled " +
                        "FROM app_income_expense_item " +
                        "WHERE COALESCE(is_del, 0) = 0 " +
                        "AND COALESCE(disabled, 0) = 0 " +
                        "AND COALESCE(participate_performance, 0) = 1");
        List<Object> params = new ArrayList<>();
        if (StringUtils.isNotEmpty(itemType)) {
            sql.append(" AND item_type = ?");
            params.add(itemType);
        }
        sql.append(" ORDER BY item_type ASC, id ASC");
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), params.toArray())) {
            if (isFixedItemName(stringValue(row.get("name")))) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("itemKey", "item_" + row.get("id"));
            item.put("name", row.get("name"));
            item.put("itemType", row.get("itemType"));
            item.put("participatePerformance", row.get("participatePerformance"));
            item.put("disabled", row.get("disabled"));
            item.put("fixed", false);
            result.add(item);
        }
        return Result.ok(result);
    }

    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param) {
        currentUser(param);
        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        validateRange(startTime, endTime);

        String itemType = normalizeItemType(param.getString("itemType"));
        String itemKey = StringUtils.trimToEmpty(param.getString("itemKey"));

        JSONArray records = new JSONArray();
        double incomeTotal = 0D;
        double expenseTotal = 0D;

        int rowNo = 1;
        for (FixedQueryItem item : FIXED_QUERY_ITEMS) {
            if (StringUtils.isNotEmpty(itemKey) && !item.itemKey.equals(itemKey)) {
                continue;
            }
            if (!matchesItemType(itemType, item.itemType)) {
                continue;
            }
            Map<String, Object> row = queryFixedRow(item.itemKey, startTime, endTime);
            if (hasAmount(row)) {
                JSONObject record = buildRecord(rowNo++, item.itemKey, item.name, item.itemType, row);
                records.add(record);
                incomeTotal += numberValue(record.get("income"));
                expenseTotal += numberValue(record.get("expense"));
            }
        }

        List<Map<String, Object>> itemRows = queryItemRows(startTime, endTime, itemType, itemKey);
        for (Map<String, Object> row : itemRows) {
            JSONObject record = buildRecord(rowNo++, "item_" + row.get("itemId"),
                    stringValue(row.get("itemName")), stringValue(row.get("itemType")), row);
            records.add(record);
            incomeTotal += numberValue(record.get("income"));
            expenseTotal += numberValue(record.get("expense"));
        }

        JSONObject result = new JSONObject();
        result.put("incomeTotal", incomeTotal);
        result.put("expenseTotal", expenseTotal);
        result.put("netTotal", incomeTotal - expenseTotal);
        result.put("records", records);
        return Result.ok(result);
    }

    @PostMapping("detail")
    public Result<?> detail(@RequestBody JSONObject param) {
        currentUser(param);
        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        validateRange(startTime, endTime);

        String itemKey = StringUtils.trimToEmpty(param.getString("itemKey"));
        if (StringUtils.isEmpty(itemKey)) {
            throw new AppRunTimeException("请选择收支项目");
        }

        DetailQuery detailQuery = buildFixedDetailQuery(itemKey, startTime, endTime);
        if (detailQuery == null && itemKey.startsWith("item_")) {
            detailQuery = buildItemDetailQuery(
                    parseItemId(itemKey.substring(5)), startTime, endTime);
        }
        if (detailQuery == null) {
            throw new AppRunTimeException("收支项目参数不正确");
        }

        int current = Math.max(param.getIntValue("current"), 1);
        int pageSize = param.getIntValue("pageSize");
        if (pageSize < 1) {
            pageSize = 50;
        }
        pageSize = Math.min(pageSize, 200);
        int offset = (current - 1) * pageSize;

        String detailSql = detailQuery.sql;
        Object[] detailParams = detailQuery.params.toArray();
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM (" + detailSql + ") detail_rows",
                detailParams,
                Long.class);
        Map<String, Object> totalRow = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(COALESCE(income, 0)), 0) AS incomeTotal, " +
                        "COALESCE(SUM(COALESCE(expense, 0)), 0) AS expenseTotal " +
                        "FROM (" + detailSql + ") detail_rows",
                detailParams);

        List<Object> pageParams = new ArrayList<>(detailQuery.params);
        pageParams.add(pageSize);
        pageParams.add(offset);
        List<Map<String, Object>> pageRows = jdbcTemplate.queryForList(
                "SELECT * FROM (" + detailSql + ") detail_rows " +
                        "ORDER BY businessTime ASC, orderNo ASC LIMIT ? OFFSET ?",
                pageParams.toArray());

        JSONArray records = new JSONArray();
        int rowNo = offset + 1;
        for (Map<String, Object> row : pageRows) {
            JSONObject record = new JSONObject();
            record.putAll(row);
            record.put("rowNo", rowNo++);
            records.add(record);
        }

        double incomeTotal = numberValue(totalRow.get("incomeTotal"));
        double expenseTotal = numberValue(totalRow.get("expenseTotal"));
        JSONObject result = new JSONObject();
        result.put("current", current);
        result.put("pageSize", pageSize);
        result.put("total", total == null ? 0L : total);
        result.put("incomeTotal", incomeTotal);
        result.put("expenseTotal", expenseTotal);
        result.put("netTotal", incomeTotal - expenseTotal);
        result.put("records", records);
        return Result.ok(result);
    }

    private DetailQuery buildFixedDetailQuery(String itemKey, Long startTime, Long endTime) {
        if (ITEM_SALE_INCOME.equals(itemKey)) {
            return buildSaleDetailQuery(startTime, endTime);
        }
        if (ITEM_PURCHASE_EXPENSE.equals(itemKey)) {
            return buildPurchaseDetailQuery(startTime, endTime);
        }
        if (ITEM_RECEIVE_DEBT.equals(itemKey)) {
            return buildReceiveDetailQuery(startTime, endTime);
        }
        if (ITEM_PAYMENT_DEBT.equals(itemKey)) {
            return buildPaymentDetailQuery(startTime, endTime);
        }
        if (ITEM_FREIGHT.equals(itemKey)) {
            return buildFreightDetailQuery(startTime, endTime);
        }
        return null;
    }

    private DetailQuery buildSaleDetailQuery(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("o")).append(" AS businessTime, ")
                .append("o.order_no AS orderNo, ")
                .append("CASE WHEN o.note IS NOT NULL AND trim(o.note) <> '' THEN o.note ELSE '销售收入' END AS summary, ")
                .append("COALESCE(c.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.paid_amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) < 0 THEN -o.paid_amount ELSE 0 END AS expense ")
                .append("FROM app_sale_order o ")
                .append("LEFT JOIN app_customer c ON c.id = o.customer_id ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND o.settle_id IS NOT NULL AND trim(o.settle_id) <> '' ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        return new DetailQuery(sql.toString(), params);
    }

    private DetailQuery buildPurchaseDetailQuery(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("o")).append(" AS businessTime, ")
                .append("o.order_no AS orderNo, ")
                .append("CASE WHEN o.note IS NOT NULL AND trim(o.note) <> '' THEN o.note ")
                .append("WHEN o.order_type = 2 THEN '进货退货' ELSE '采购进货' END AS summary, ")
                .append("COALESCE(s.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) < 0 THEN -o.paid_amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.paid_amount ELSE 0 END AS expense ")
                .append("FROM app_purchase_order o ")
                .append("LEFT JOIN app_supplier s ON s.id = o.supplier_id ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND o.settle_id IS NOT NULL AND trim(o.settle_id) <> '' ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        return new DetailQuery(sql.toString(), params);
    }

    private DetailQuery buildReceiveDetailQuery(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("r")).append(" AS businessTime, ")
                .append("r.order_no AS orderNo, ")
                .append("CASE WHEN r.note IS NOT NULL AND trim(r.note) <> '' THEN r.note ELSE '收定金/欠款' END AS summary, ")
                .append("COALESCE(c.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(si.amount, 0) > 0 THEN si.amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(si.amount, 0) < 0 THEN -si.amount ELSE 0 END AS expense ")
                .append("FROM app_receive_payment_voucher r ")
                .append("INNER JOIN (SELECT payment_id, SUM(COALESCE(amount, 0)) AS amount ")
                .append("FROM app_receive_payment_settle_item GROUP BY payment_id) si ON si.payment_id = r.id ")
                .append("LEFT JOIN app_customer c ON c.id = r.customer_id ")
                .append("WHERE r.status = 1 AND COALESCE(r.is_del, 0) = 0 ");
        appendDateRange(sql, params, businessTimeSql("r"), startTime, endTime);
        return new DetailQuery(sql.toString(), params);
    }

    private DetailQuery buildPaymentDetailQuery(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("p")).append(" AS businessTime, ")
                .append("p.order_no AS orderNo, ")
                .append("CASE WHEN p.note IS NOT NULL AND trim(p.note) <> '' THEN p.note ELSE '付定金/欠款' END AS summary, ")
                .append("COALESCE(s.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(si.amount, 0) < 0 THEN -si.amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(si.amount, 0) > 0 THEN si.amount ELSE 0 END AS expense ")
                .append("FROM app_payment_voucher p ")
                .append("INNER JOIN (SELECT payment_id, SUM(COALESCE(amount, 0)) AS amount ")
                .append("FROM app_payment_settle_item GROUP BY payment_id) si ON si.payment_id = p.id ")
                .append("LEFT JOIN app_supplier s ON s.id = p.supplier_id ")
                .append("WHERE p.status = 1 AND COALESCE(p.is_del, 0) = 0 ");
        appendDateRange(sql, params, businessTimeSql("p"), startTime, endTime);
        return new DetailQuery(sql.toString(), params);
    }

    private DetailQuery buildFreightDetailQuery(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("o")).append(" AS businessTime, ")
                .append("o.order_no AS orderNo, ")
                .append("CASE WHEN o.note IS NOT NULL AND trim(o.note) <> '' THEN o.note ELSE '销售运费' END AS summary, ")
                .append("COALESCE(c.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(o.freight_amount, 0) > 0 THEN o.freight_amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(o.freight_amount, 0) < 0 THEN -o.freight_amount ELSE 0 END AS expense ")
                .append("FROM app_sale_order o ")
                .append("LEFT JOIN app_customer c ON c.id = o.customer_id ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND ABS(COALESCE(o.freight_amount, 0)) >= 0.005 ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        sql.append(" UNION ALL ")
                .append("SELECT ").append(businessTimeSql("o")).append(" AS businessTime, ")
                .append("o.order_no AS orderNo, ")
                .append("CASE WHEN o.note IS NOT NULL AND trim(o.note) <> '' THEN o.note ELSE '进货运费' END AS summary, ")
                .append("COALESCE(s.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(o.freight_amount, 0) < 0 THEN -o.freight_amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(o.freight_amount, 0) > 0 THEN o.freight_amount ELSE 0 END AS expense ")
                .append("FROM app_purchase_order o ")
                .append("LEFT JOIN app_supplier s ON s.id = o.supplier_id ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND ABS(COALESCE(o.freight_amount, 0)) >= 0.005 ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        return new DetailQuery(sql.toString(), params);
    }

    private DetailQuery buildItemDetailQuery(Integer itemId, Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("r")).append(" AS businessTime, ")
                .append("r.order_no AS orderNo, ")
                .append("COALESCE(r.summary, i.name) AS summary, ")
                .append("COALESCE(r.counterparty, '') AS counterparty, ")
                .append("COALESCE(r.income, 0) AS income, ")
                .append("COALESCE(r.expense, 0) AS expense ")
                .append("FROM app_income_expense_record r ")
                .append("INNER JOIN app_income_expense_item i ON i.name = r.fund_item ")
                .append("WHERE COALESCE(r.is_del, 0) = 0 ")
                .append("AND COALESCE(i.is_del, 0) = 0 ")
                .append("AND COALESCE(i.disabled, 0) = 0 ")
                .append("AND COALESCE(i.participate_performance, 0) = 1 ")
                .append("AND i.id = ? ");
        params.add(itemId);
        appendDateRange(sql, params, businessTimeSql("r"), startTime, endTime);
        return new DetailQuery(sql.toString(), params);
    }

    private List<Map<String, Object>> queryItemRows(Long startTime, Long endTime,
                                                    String itemType, String itemKey) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT i.id AS itemId, i.name AS itemName, i.item_type AS itemType, ")
                .append("COALESCE(SUM(COALESCE(r.income, 0)), 0) AS income, ")
                .append("COALESCE(SUM(COALESCE(r.expense, 0)), 0) AS expense ")
                .append("FROM app_income_expense_item i ")
                .append("INNER JOIN app_income_expense_record r ON r.fund_item = i.name ")
                .append("WHERE COALESCE(i.is_del, 0) = 0 ")
                .append("AND COALESCE(i.disabled, 0) = 0 ")
                .append("AND COALESCE(i.participate_performance, 0) = 1 ")
                .append("AND COALESCE(r.is_del, 0) = 0 ");
        appendDateRange(sql, params, "r.create_time", startTime, endTime);
        sql.append(" AND i.name NOT IN (?, ?, ?, ?, ?)");
        for (FixedQueryItem item : FIXED_QUERY_ITEMS) {
            params.add(item.name);
        }
        if (StringUtils.isNotEmpty(itemType)) {
            sql.append(" AND i.item_type = ?");
            params.add(itemType);
        }
        if (StringUtils.isNotEmpty(itemKey)) {
            if (!itemKey.startsWith("item_")) {
                return new ArrayList<>();
            }
            sql.append(" AND i.id = ?");
            params.add(parseItemId(itemKey.substring(5)));
        }
        sql.append(" GROUP BY i.id, i.name, i.item_type ")
                .append(" ORDER BY i.item_type ASC, i.id ASC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private Map<String, Object> queryFixedRow(String itemKey, Long startTime, Long endTime) {
        if (ITEM_SALE_INCOME.equals(itemKey)) {
            return querySaleRow(startTime, endTime);
        }
        if (ITEM_PURCHASE_EXPENSE.equals(itemKey)) {
            return queryPurchaseRow(startTime, endTime);
        }
        if (ITEM_RECEIVE_DEBT.equals(itemKey)) {
            return queryReceiveRow(startTime, endTime);
        }
        if (ITEM_PAYMENT_DEBT.equals(itemKey)) {
            return queryPaymentRow(startTime, endTime);
        }
        if (ITEM_FREIGHT.equals(itemKey)) {
            return queryFreightRow(startTime, endTime);
        }
        return null;
    }

    private Map<String, Object> querySaleRow(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(SUM(CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.paid_amount ELSE 0 END), 0) AS income, ")
                .append("COALESCE(SUM(CASE WHEN COALESCE(o.paid_amount, 0) < 0 THEN -o.paid_amount ELSE 0 END), 0) AS expense ")
                .append("FROM app_sale_order o ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND o.settle_id IS NOT NULL AND trim(o.settle_id) <> '' ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        return jdbcTemplate.queryForMap(sql.toString(), params.toArray());
    }

    private Map<String, Object> queryPurchaseRow(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(SUM(CASE WHEN COALESCE(o.paid_amount, 0) < 0 THEN -o.paid_amount ELSE 0 END), 0) AS income, ")
                .append("COALESCE(SUM(CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.paid_amount ELSE 0 END), 0) AS expense ")
                .append("FROM app_purchase_order o ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND o.settle_id IS NOT NULL AND trim(o.settle_id) <> '' ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        return jdbcTemplate.queryForMap(sql.toString(), params.toArray());
    }

    private Map<String, Object> queryReceiveRow(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(SUM(CASE WHEN COALESCE(si.amount, 0) > 0 THEN si.amount ELSE 0 END), 0) AS income, ")
                .append("COALESCE(SUM(CASE WHEN COALESCE(si.amount, 0) < 0 THEN -si.amount ELSE 0 END), 0) AS expense ")
                .append("FROM app_receive_payment_voucher r ")
                .append("INNER JOIN (SELECT payment_id, SUM(COALESCE(amount, 0)) AS amount ")
                .append("FROM app_receive_payment_settle_item GROUP BY payment_id) si ON si.payment_id = r.id ")
                .append("WHERE r.status = 1 AND COALESCE(r.is_del, 0) = 0 ");
        appendDateRange(sql, params, businessTimeSql("r"), startTime, endTime);
        return jdbcTemplate.queryForMap(sql.toString(), params.toArray());
    }

    private Map<String, Object> queryPaymentRow(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(SUM(CASE WHEN COALESCE(si.amount, 0) < 0 THEN -si.amount ELSE 0 END), 0) AS income, ")
                .append("COALESCE(SUM(CASE WHEN COALESCE(si.amount, 0) > 0 THEN si.amount ELSE 0 END), 0) AS expense ")
                .append("FROM app_payment_voucher p ")
                .append("INNER JOIN (SELECT payment_id, SUM(COALESCE(amount, 0)) AS amount ")
                .append("FROM app_payment_settle_item GROUP BY payment_id) si ON si.payment_id = p.id ")
                .append("WHERE p.status = 1 AND COALESCE(p.is_del, 0) = 0 ");
        appendDateRange(sql, params, businessTimeSql("p"), startTime, endTime);
        return jdbcTemplate.queryForMap(sql.toString(), params.toArray());
    }

    private Map<String, Object> queryFreightRow(Long startTime, Long endTime) {
        Map<String, Object> saleRow = querySaleFreightRow(startTime, endTime);
        Map<String, Object> purchaseRow = queryPurchaseFreightRow(startTime, endTime);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("income", numberValue(saleRow.get("income")) + numberValue(purchaseRow.get("income")));
        result.put("expense", numberValue(saleRow.get("expense")) + numberValue(purchaseRow.get("expense")));
        return result;
    }

    private Map<String, Object> querySaleFreightRow(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(SUM(CASE WHEN COALESCE(o.freight_amount, 0) > 0 THEN o.freight_amount ELSE 0 END), 0) AS income, ")
                .append("COALESCE(SUM(CASE WHEN COALESCE(o.freight_amount, 0) < 0 THEN -o.freight_amount ELSE 0 END), 0) AS expense ")
                .append("FROM app_sale_order o ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        return jdbcTemplate.queryForMap(sql.toString(), params.toArray());
    }

    private Map<String, Object> queryPurchaseFreightRow(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(SUM(CASE WHEN COALESCE(o.freight_amount, 0) < 0 THEN -o.freight_amount ELSE 0 END), 0) AS income, ")
                .append("COALESCE(SUM(CASE WHEN COALESCE(o.freight_amount, 0) > 0 THEN o.freight_amount ELSE 0 END), 0) AS expense ")
                .append("FROM app_purchase_order o ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        return jdbcTemplate.queryForMap(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> querySaleDetail(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("o")).append(" AS businessTime, ")
                .append("o.order_no AS orderNo, ")
                .append("CASE WHEN o.note IS NOT NULL AND trim(o.note) <> '' THEN o.note ELSE '销售收入' END AS summary, ")
                .append("COALESCE(c.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.paid_amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) < 0 THEN -o.paid_amount ELSE 0 END AS expense ")
                .append("FROM app_sale_order o ")
                .append("LEFT JOIN app_customer c ON c.id = o.customer_id ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND o.settle_id IS NOT NULL AND trim(o.settle_id) <> '' ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        sql.append(" ORDER BY businessTime ASC, orderNo ASC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> queryFixedDetail(String itemKey, Long startTime, Long endTime) {
        if (ITEM_SALE_INCOME.equals(itemKey)) {
            return querySaleDetail(startTime, endTime);
        }
        if (ITEM_PURCHASE_EXPENSE.equals(itemKey)) {
            return queryPurchaseDetail(startTime, endTime);
        }
        if (ITEM_RECEIVE_DEBT.equals(itemKey)) {
            return queryReceiveDetail(startTime, endTime);
        }
        if (ITEM_PAYMENT_DEBT.equals(itemKey)) {
            return queryPaymentDetail(startTime, endTime);
        }
        if (ITEM_FREIGHT.equals(itemKey)) {
            return queryFreightDetail(startTime, endTime);
        }
        return new ArrayList<>();
    }

    private List<Map<String, Object>> queryPurchaseDetail(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("o")).append(" AS businessTime, ")
                .append("o.order_no AS orderNo, ")
                .append("CASE WHEN o.note IS NOT NULL AND trim(o.note) <> '' THEN o.note ")
                .append("WHEN o.order_type = 2 THEN '进货退货' ELSE '采购进货' END AS summary, ")
                .append("COALESCE(s.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) < 0 THEN -o.paid_amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.paid_amount ELSE 0 END AS expense ")
                .append("FROM app_purchase_order o ")
                .append("LEFT JOIN app_supplier s ON s.id = o.supplier_id ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND o.settle_id IS NOT NULL AND trim(o.settle_id) <> '' ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        sql.append(" ORDER BY businessTime ASC, orderNo ASC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> queryReceiveDetail(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("r")).append(" AS businessTime, ")
                .append("r.order_no AS orderNo, ")
                .append("CASE WHEN r.note IS NOT NULL AND trim(r.note) <> '' THEN r.note ELSE '收定金/欠款' END AS summary, ")
                .append("COALESCE(c.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(si.amount, 0) > 0 THEN si.amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(si.amount, 0) < 0 THEN -si.amount ELSE 0 END AS expense ")
                .append("FROM app_receive_payment_voucher r ")
                .append("INNER JOIN (SELECT payment_id, SUM(COALESCE(amount, 0)) AS amount ")
                .append("FROM app_receive_payment_settle_item GROUP BY payment_id) si ON si.payment_id = r.id ")
                .append("LEFT JOIN app_customer c ON c.id = r.customer_id ")
                .append("WHERE r.status = 1 AND COALESCE(r.is_del, 0) = 0 ");
        appendDateRange(sql, params, businessTimeSql("r"), startTime, endTime);
        sql.append(" ORDER BY businessTime ASC, orderNo ASC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> queryPaymentDetail(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("p")).append(" AS businessTime, ")
                .append("p.order_no AS orderNo, ")
                .append("CASE WHEN p.note IS NOT NULL AND trim(p.note) <> '' THEN p.note ELSE '付定金/欠款' END AS summary, ")
                .append("COALESCE(s.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(si.amount, 0) < 0 THEN -si.amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(si.amount, 0) > 0 THEN si.amount ELSE 0 END AS expense ")
                .append("FROM app_payment_voucher p ")
                .append("INNER JOIN (SELECT payment_id, SUM(COALESCE(amount, 0)) AS amount ")
                .append("FROM app_payment_settle_item GROUP BY payment_id) si ON si.payment_id = p.id ")
                .append("LEFT JOIN app_supplier s ON s.id = p.supplier_id ")
                .append("WHERE p.status = 1 AND COALESCE(p.is_del, 0) = 0 ");
        appendDateRange(sql, params, businessTimeSql("p"), startTime, endTime);
        sql.append(" ORDER BY businessTime ASC, orderNo ASC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> queryFreightDetail(Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("o")).append(" AS businessTime, ")
                .append("o.order_no AS orderNo, ")
                .append("CASE WHEN o.note IS NOT NULL AND trim(o.note) <> '' THEN o.note ELSE '销售运费' END AS summary, ")
                .append("COALESCE(c.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(o.freight_amount, 0) > 0 THEN o.freight_amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(o.freight_amount, 0) < 0 THEN -o.freight_amount ELSE 0 END AS expense ")
                .append("FROM app_sale_order o ")
                .append("LEFT JOIN app_customer c ON c.id = o.customer_id ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND ABS(COALESCE(o.freight_amount, 0)) >= 0.005 ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        sql.append(" UNION ALL ")
                .append("SELECT ").append(businessTimeSql("o")).append(" AS businessTime, ")
                .append("o.order_no AS orderNo, ")
                .append("CASE WHEN o.note IS NOT NULL AND trim(o.note) <> '' THEN o.note ELSE '进货运费' END AS summary, ")
                .append("COALESCE(s.name, '') AS counterparty, ")
                .append("CASE WHEN COALESCE(o.freight_amount, 0) < 0 THEN -o.freight_amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(o.freight_amount, 0) > 0 THEN o.freight_amount ELSE 0 END AS expense ")
                .append("FROM app_purchase_order o ")
                .append("LEFT JOIN app_supplier s ON s.id = o.supplier_id ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND ABS(COALESCE(o.freight_amount, 0)) >= 0.005 ");
        appendDateRange(sql, params, businessTimeSql("o"), startTime, endTime);
        sql.append(" ORDER BY businessTime ASC, orderNo ASC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> queryItemDetail(Integer itemId, Long startTime, Long endTime) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(businessTimeSql("r")).append(" AS businessTime, ")
                .append("r.order_no AS orderNo, ")
                .append("COALESCE(r.summary, i.name) AS summary, ")
                .append("COALESCE(r.counterparty, '') AS counterparty, ")
                .append("COALESCE(r.income, 0) AS income, ")
                .append("COALESCE(r.expense, 0) AS expense ")
                .append("FROM app_income_expense_record r ")
                .append("INNER JOIN app_income_expense_item i ON i.name = r.fund_item ")
                .append("WHERE COALESCE(r.is_del, 0) = 0 ")
                .append("AND COALESCE(i.is_del, 0) = 0 ")
                .append("AND COALESCE(i.disabled, 0) = 0 ")
                .append("AND COALESCE(i.participate_performance, 0) = 1 ")
                .append("AND i.id = ? ");
        params.add(itemId);
        appendDateRange(sql, params, businessTimeSql("r"), startTime, endTime);
        sql.append(" ORDER BY businessTime ASC, orderNo ASC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private JSONObject buildRecord(int rowNo, String itemKey, String itemName, String itemType,
                                   Map<String, Object> row) {
        JSONObject record = new JSONObject();
        record.put("rowNo", rowNo);
        record.put("itemKey", itemKey);
        record.put("itemName", itemName);
        record.put("itemType", itemType);
        record.put("income", numberValue(row.get("income")));
        record.put("expense", numberValue(row.get("expense")));
        return record;
    }

    private String normalizeItemType(String itemType) {
        if ("income".equals(itemType) || "expense".equals(itemType)) {
            return itemType;
        }
        return "";
    }

    private boolean matchesItemType(String itemType, String queryItemType) {
        return StringUtils.isEmpty(itemType)
                || "mixed".equals(queryItemType)
                || itemType.equals(queryItemType);
    }

    private boolean hasAmount(Map<String, Object> row) {
        return row != null
                && (Math.abs(numberValue(row.get("income"))) >= 0.005
                || Math.abs(numberValue(row.get("expense"))) >= 0.005);
    }

    private boolean isFixedItemName(String itemName) {
        for (FixedQueryItem item : FIXED_QUERY_ITEMS) {
            if (item.name.equals(itemName)) {
                return true;
            }
        }
        return false;
    }

    private AppUser currentUser(JSONObject param) {
        AppUser user = userService.getById(param.getString("userId"));
        if (user == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }
        return user;
    }

    private void appendDateRange(StringBuilder sql, List<Object> params, String timeSql,
                                 Long startTime, Long endTime) {
        if (startTime != null) {
            sql.append(" AND ").append(timeSql).append(" >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            sql.append(" AND ").append(timeSql).append(" <= ?");
            params.add(endTime);
        }
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

    private Integer parseItemId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            throw new AppRunTimeException("收支项目参数不正确");
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

    private String businessTimeSql(String alias) {
        return databaseDialect.epochMillis(alias + ".create_time");
    }

    private static class DetailQuery {
        private final String sql;
        private final List<Object> params;

        private DetailQuery(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    private static class FixedQueryItem {
        private final String itemKey;
        private final String name;
        private final String itemType;

        private FixedQueryItem(String itemKey, String name, String itemType) {
            this.itemKey = itemKey;
            this.name = name;
            this.itemType = itemType;
        }
    }
}
