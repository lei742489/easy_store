package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppAccountSettleService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/user/appIncomeExpenseRecord")
public class AppIncomeExpenseRecordController {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SALE_TIME_SQL = businessTimeSql("o");
    private static final String PURCHASE_TIME_SQL = businessTimeSql("o");
    private static final String RECEIVE_TIME_SQL = businessTimeSql("r");
    private static final String PAYMENT_TIME_SQL = businessTimeSql("p");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IAppUserService userService;
    @Autowired
    private IAppAccountSettleService accountSettleService;

    @PostMapping("add")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> add(@RequestBody JSONObject param) {
        assertRoot(param);
        String settleId = param.getString("settleId");
        String cashierId = param.getString("cashierId");
        String summary = StringUtils.trimToEmpty(param.getString("summary"));
        String fundItem = StringUtils.trimToEmpty(param.getString("fundItem"));
        double income = param.getDoubleValue("income");
        double expense = param.getDoubleValue("expense");
        if (StringUtils.isEmpty(settleId)) {
            throw new AppRunTimeException("请选择结算账户");
        }
        if (StringUtils.isEmpty(cashierId)) {
            throw new AppRunTimeException("请选择营业员");
        }
        if (StringUtils.isEmpty(fundItem)) {
            throw new AppRunTimeException("请选择收支项目");
        }
        if ((income <= 0D && expense <= 0D) || (income > 0D && expense > 0D)) {
            throw new AppRunTimeException("收入和支出必须且只能填写一项");
        }
        if (accountSettleService.getById(settleId) == null) {
            throw new AppRunTimeException("结算账户不存在或已删除");
        }
        AppUser cashier = userService.getById(cashierId);
        if (cashier == null) {
            throw new AppRunTimeException("营业员不存在或已删除");
        }

        String businessDate = param.getString("businessDate");
        if (StringUtils.isEmpty(businessDate)) {
            businessDate = LocalDate.now(ZONE_ID).format(DATE_FORMATTER);
        }
        Long businessTime = parseStartTime(businessDate);
        String orderNo = "JZ" + LocalDateTime.now(ZONE_ID)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String cashierName = StringUtils.isNotEmpty(cashier.getRealName())
                ? cashier.getRealName() : cashier.getUserName();
        jdbcTemplate.update("INSERT INTO app_income_expense_record " +
                        "(order_no, settle_id, cashier_id, cashier_name, summary, counterparty, fund_item, income, expense, create_time, is_del) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                orderNo, settleId, cashierId, cashierName, summary,
                StringUtils.trimToEmpty(param.getString("counterparty")),
                fundItem, income, expense, businessTime);
        accountSettleService.updateCurPrc(settleId, income - expense);
        return Result.ok(orderNo);
    }

    @PostMapping("itemList")
    public Result<?> itemList(@RequestBody JSONObject param) {
        assertRoot(param);
        String itemType = param.getString("itemType");
        boolean enabledOnly = param.getBooleanValue("enabledOnly");
        StringBuilder sql = new StringBuilder("SELECT id, name, item_type AS itemType, " +
                "participate_performance AS participatePerformance, disabled FROM app_income_expense_item " +
                "WHERE COALESCE(is_del, 0) = 0");
        List<Object> params = new ArrayList<>();
        if (StringUtils.isNotEmpty(itemType)) {
            sql.append(" AND item_type = ?");
            params.add(itemType);
        }
        if (enabledOnly) {
            sql.append(" AND COALESCE(disabled, 0) = 0");
        }
        sql.append(" ORDER BY item_type ASC, id ASC");
        return Result.ok(jdbcTemplate.queryForList(sql.toString(), params.toArray()));
    }

    @PostMapping("itemAdd")
    public Result<?> itemAdd(@RequestBody JSONObject param) {
        assertRoot(param);
        String name = StringUtils.trimToEmpty(param.getString("name"));
        String itemType = param.getString("itemType");
        validateItem(name, itemType);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM app_income_expense_item " +
                        "WHERE name = ? AND item_type = ? AND COALESCE(is_del, 0) = 0",
                Integer.class, name, itemType);
        if (count != null && count > 0) {
            throw new AppRunTimeException("该收支项目已存在");
        }
        jdbcTemplate.update("INSERT INTO app_income_expense_item " +
                        "(name, item_type, participate_performance, disabled, create_time, update_time, is_del) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 0)",
                name, itemType, param.getBooleanValue("participatePerformance") ? 1 : 0,
                param.getBooleanValue("disabled") ? 1 : 0,
                System.currentTimeMillis(), System.currentTimeMillis());
        return Result.ok();
    }

    @PostMapping("itemEdit")
    public Result<?> itemEdit(@RequestBody JSONObject param) {
        assertRoot(param);
        Integer id = param.getInteger("id");
        String name = StringUtils.trimToEmpty(param.getString("name"));
        String itemType = param.getString("itemType");
        if (id == null) {
            throw new AppRunTimeException("收支项目编号不能为空");
        }
        validateItem(name, itemType);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM app_income_expense_item " +
                        "WHERE name = ? AND item_type = ? AND id <> ? AND COALESCE(is_del, 0) = 0",
                Integer.class, name, itemType, id);
        if (count != null && count > 0) {
            throw new AppRunTimeException("该收支项目已存在");
        }
        jdbcTemplate.update("UPDATE app_income_expense_item SET name = ?, item_type = ?, " +
                        "participate_performance = ?, disabled = ?, update_time = ? " +
                        "WHERE id = ? AND COALESCE(is_del, 0) = 0",
                name, itemType, param.getBooleanValue("participatePerformance") ? 1 : 0,
                param.getBooleanValue("disabled") ? 1 : 0, System.currentTimeMillis(), id);
        return Result.ok();
    }

    @PostMapping("itemRemove")
    public Result<?> itemRemove(@RequestBody JSONObject param) {
        assertRoot(param);
        Integer id = param.getInteger("id");
        if (id == null) {
            throw new AppRunTimeException("收支项目编号不能为空");
        }
        jdbcTemplate.update("UPDATE app_income_expense_item SET is_del = 1, update_time = ? WHERE id = ?",
                System.currentTimeMillis(), id);
        return Result.ok();
    }

    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param) {
        assertRoot(param);
        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        if (startTime != null && endTime != null && startTime > endTime) {
            throw new AppRunTimeException("开始日期不能晚于结束日期");
        }

        String customerId = param.getString("customerId");
        String supplierId = param.getString("supplierId");
        String fundItem = StringUtils.trimToEmpty(param.getString("fundItem"));
        String incomeExpenseType = param.getString("incomeExpenseType");
        if (!StringUtils.isEmpty(incomeExpenseType)
                && !"income".equals(incomeExpenseType)
                && !"expense".equals(incomeExpenseType)) {
            incomeExpenseType = "";
        }
        int current = param.getIntValue("current");
        int pageSize = param.getIntValue("pageSize");
        if (current < 1) current = 1;
        if (pageSize < 1) pageSize = 50;
        pageSize = Math.min(pageSize, 200);
        int offset = (current - 1) * pageSize;

        SqlAndParams openingSql = buildLedgerSql(customerId, supplierId, fundItem,
                incomeExpenseType, null, startTime, true);
        SqlAndParams currentSql = buildLedgerSql(customerId, supplierId, fundItem,
                incomeExpenseType, startTime, endTime, false);
        Map<String, Object> openingSummary = querySummary(openingSql);
        Map<String, Object> currentSummary = querySummary(currentSql);
        double openingBalance = initialBalance()
                + numberValue(openingSummary.get("incomeTotal"))
                - numberValue(openingSummary.get("expenseTotal"));
        double incomeTotal = numberValue(currentSummary.get("incomeTotal"));
        double expenseTotal = numberValue(currentSummary.get("expenseTotal"));
        long total = longValue(currentSummary.get("total"));
        double endingBalance = openingBalance + incomeTotal - expenseTotal;
        double beforePageBalance = offset == 0 ? 0D : queryBeforePageBalance(currentSql, offset);
        List<Map<String, Object>> pageEvents = queryPageEvents(currentSql, pageSize, offset);

        JSONArray records = new JSONArray();
        if (current == 1) {
            records.add(buildRecord("期初", "", "", "期初结存", "", "", 0D, 0D, openingBalance));
        }

        double balance = openingBalance + beforePageBalance;
        int rowNo = offset + 1;
        for (Map<String, Object> event : pageEvents) {
            double income = numberValue(event.get("income"));
            double expense = numberValue(event.get("expense"));
            balance += income - expense;
            records.add(buildRecord(rowNo++, formatDate(numberValue(event.get("businessTime"))),
                    stringValue(event.get("orderNo")), stringValue(event.get("summary")),
                    stringValue(event.get("counterparty")), stringValue(event.get("fundItem")),
                    income, expense, balance));
        }

        JSONObject result = new JSONObject();
        result.put("incomeTotal", incomeTotal);
        result.put("expenseTotal", expenseTotal);
        result.put("openingBalance", openingBalance);
        result.put("endingBalance", endingBalance);
        result.put("current", current);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("records", records);
        return Result.ok(result);
    }

    private SqlAndParams buildLedgerSql(String customerId, String supplierId, String fundItem,
                                        String incomeExpenseType, Long startTime, Long endTime,
                                        boolean beforeStart) {
        List<Object> params = new ArrayList<>();
        StringBuilder unionSql = new StringBuilder();
        appendSaleSql(unionSql, params, customerId);
        unionSql.append(" UNION ALL ");
        appendPurchaseSql(unionSql, params, supplierId);
        unionSql.append(" UNION ALL ");
        appendReceiveSql(unionSql, params, customerId);
        unionSql.append(" UNION ALL ");
        appendPaymentSql(unionSql, params, supplierId);
        unionSql.append(" UNION ALL ");
        appendManualSql(unionSql, params, customerId, supplierId);

        StringBuilder sql = new StringBuilder("SELECT * FROM (").append(unionSql).append(") ledger WHERE 1 = 1");
        appendEquals(sql, params, "fundItem", fundItem);
        if ("income".equals(incomeExpenseType)) {
            sql.append(" AND income > 0");
        } else if ("expense".equals(incomeExpenseType)) {
            sql.append(" AND expense > 0");
        }
        if (beforeStart && endTime != null) {
            sql.append(" AND businessTime < ?");
            params.add(endTime);
        } else {
            if (startTime != null) {
                sql.append(" AND businessTime >= ?");
                params.add(startTime);
            }
            if (endTime != null) {
                sql.append(" AND businessTime <= ?");
                params.add(endTime);
            }
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private Map<String, Object> querySummary(SqlAndParams ledgerSql) {
        String sql = "SELECT COUNT(1) AS total, " +
                "COALESCE(SUM(income), 0) AS incomeTotal, " +
                "COALESCE(SUM(expense), 0) AS expenseTotal " +
                "FROM (" + ledgerSql.sql + ") summaryLedger";
        return jdbcTemplate.queryForMap(sql, ledgerSql.params.toArray());
    }

    private double queryBeforePageBalance(SqlAndParams ledgerSql, int offset) {
        List<Object> params = new ArrayList<>(ledgerSql.params);
        params.add(offset);
        String sql = "SELECT COALESCE(SUM(income - expense), 0) AS balanceChange FROM (" +
                "SELECT income, expense FROM (" + ledgerSql.sql + ") pagePrefix " +
                "ORDER BY businessTime ASC, orderNo ASC, recordType ASC, recordId ASC LIMIT ?" +
                ") prefixLedger";
        Map<String, Object> result = jdbcTemplate.queryForMap(sql, params.toArray());
        return numberValue(result.get("balanceChange"));
    }

    private List<Map<String, Object>> queryPageEvents(SqlAndParams ledgerSql, int pageSize, int offset) {
        List<Object> params = new ArrayList<>(ledgerSql.params);
        params.add(pageSize);
        params.add(offset);
        String sql = ledgerSql.sql +
                " ORDER BY businessTime ASC, orderNo ASC, recordType ASC, recordId ASC LIMIT ? OFFSET ?";
        return jdbcTemplate.queryForList(sql, params.toArray());
    }

    private void appendSaleSql(StringBuilder sql, List<Object> params, String customerId) {
        sql.append("SELECT 'sale' AS recordType, ").append(SALE_TIME_SQL)
                .append(" AS businessTime, o.id AS recordId, o.order_no AS orderNo, ")
                .append("CASE WHEN o.note IS NOT NULL AND trim(o.note) <> '' THEN o.note ")
                .append("WHEN o.order_type = 2 THEN '销售退货' ELSE '销售出货' END AS summary, ")
                .append("COALESCE(c.name, '') AS counterparty, '销售收入' AS fundItem, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.paid_amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) < 0 THEN -o.paid_amount ELSE 0 END AS expense ")
                .append("FROM app_sale_order o LEFT JOIN app_customer c ON c.id = o.customer_id ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND o.settle_id IS NOT NULL AND trim(o.settle_id) <> '' ")
                .append("AND ABS(COALESCE(o.paid_amount, 0)) >= 0.005");
        appendEquals(sql, params, "o.customer_id", customerId);
    }

    private void appendPurchaseSql(StringBuilder sql, List<Object> params, String supplierId) {
        sql.append("SELECT 'purchase' AS recordType, ").append(PURCHASE_TIME_SQL)
                .append(" AS businessTime, o.id AS recordId, o.order_no AS orderNo, ")
                .append("CASE WHEN o.note IS NOT NULL AND trim(o.note) <> '' THEN o.note ")
                .append("WHEN o.order_type = 2 THEN '进货退货' ELSE '采购进货' END AS summary, ")
                .append("COALESCE(s.name, '') AS counterparty, '进货支出' AS fundItem, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) < 0 THEN -o.paid_amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.paid_amount ELSE 0 END AS expense ")
                .append("FROM app_purchase_order o LEFT JOIN app_supplier s ON s.id = o.supplier_id ")
                .append("WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 ")
                .append("AND o.settle_id IS NOT NULL AND trim(o.settle_id) <> '' ")
                .append("AND ABS(COALESCE(o.paid_amount, 0)) >= 0.005");
        appendEquals(sql, params, "o.supplier_id", supplierId);
    }

    private void appendReceiveSql(StringBuilder sql, List<Object> params, String customerId) {
        sql.append("SELECT 'receive' AS recordType, ").append(RECEIVE_TIME_SQL)
                .append(" AS businessTime, r.id AS recordId, r.order_no AS orderNo, ")
                .append("CASE WHEN r.note IS NOT NULL AND trim(r.note) <> '' THEN r.note ELSE '收款' END AS summary, ")
                .append("COALESCE(c.name, '') AS counterparty, '收款收入' AS fundItem, ")
                .append("CASE WHEN COALESCE(si.amount, 0) > 0 THEN si.amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(si.amount, 0) < 0 THEN -si.amount ELSE 0 END AS expense ")
                .append("FROM app_receive_payment_voucher r ")
                .append("INNER JOIN (SELECT payment_id, SUM(COALESCE(amount, 0)) AS amount ")
                .append("FROM app_receive_payment_settle_item GROUP BY payment_id) si ON si.payment_id = r.id ")
                .append("LEFT JOIN app_customer c ON c.id = r.customer_id ")
                .append("WHERE r.status = 1 AND COALESCE(r.is_del, 0) = 0 ")
                .append("AND ABS(COALESCE(si.amount, 0)) >= 0.005");
        appendEquals(sql, params, "r.customer_id", customerId);
    }

    private void appendPaymentSql(StringBuilder sql, List<Object> params, String supplierId) {
        sql.append("SELECT 'payment' AS recordType, ").append(PAYMENT_TIME_SQL)
                .append(" AS businessTime, p.id AS recordId, p.order_no AS orderNo, ")
                .append("CASE WHEN p.note IS NOT NULL AND trim(p.note) <> '' THEN p.note ELSE '付款' END AS summary, ")
                .append("COALESCE(s.name, '') AS counterparty, '付款支出' AS fundItem, ")
                .append("CASE WHEN COALESCE(si.amount, 0) < 0 THEN -si.amount ELSE 0 END AS income, ")
                .append("CASE WHEN COALESCE(si.amount, 0) > 0 THEN si.amount ELSE 0 END AS expense ")
                .append("FROM app_payment_voucher p ")
                .append("INNER JOIN (SELECT payment_id, SUM(COALESCE(amount, 0)) AS amount ")
                .append("FROM app_payment_settle_item GROUP BY payment_id) si ON si.payment_id = p.id ")
                .append("LEFT JOIN app_supplier s ON s.id = p.supplier_id ")
                .append("WHERE p.status = 1 AND COALESCE(p.is_del, 0) = 0 ")
                .append("AND ABS(COALESCE(si.amount, 0)) >= 0.005");
        appendEquals(sql, params, "p.supplier_id", supplierId);
    }

    private void appendManualSql(StringBuilder sql, List<Object> params, String customerId, String supplierId) {
        sql.append("SELECT 'manual' AS recordType, ").append(businessTimeSql("m"))
                .append(" AS businessTime, m.id AS recordId, m.order_no AS orderNo, ")
                .append("m.summary AS summary, COALESCE(m.counterparty, '') AS counterparty, ")
                .append("m.fund_item AS fundItem, COALESCE(m.income, 0) AS income, ")
                .append("COALESCE(m.expense, 0) AS expense ")
                .append("FROM app_income_expense_record m ")
                .append("WHERE COALESCE(m.is_del, 0) = 0 ");
        List<String> counterpartyFilters = new ArrayList<>();
        if (StringUtils.isNotEmpty(customerId)) {
            counterpartyFilters.add("m.counterparty = (SELECT name FROM app_customer WHERE id = ?)");
            params.add(customerId);
        }
        if (StringUtils.isNotEmpty(supplierId)) {
            counterpartyFilters.add("m.counterparty = (SELECT name FROM app_supplier WHERE id = ?)");
            params.add(supplierId);
        }
        if (!counterpartyFilters.isEmpty()) {
            sql.append("AND (").append(StringUtils.join(counterpartyFilters, " OR ")).append(") ");
        }
    }

    private double initialBalance() {
        Double balance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(init_prc, 0)), 0) FROM app_account_settle " +
                        "WHERE COALESCE(is_del, 0) = 0",
                Double.class);
        return balance == null ? 0D : balance;
    }

    private JSONObject buildRecord(Object rowNo, String businessDate, String orderNo, String summary,
                                   String counterparty, String fundItem, double income, double expense,
                                   double balance) {
        JSONObject record = new JSONObject();
        record.put("rowNo", rowNo);
        record.put("businessDate", businessDate);
        record.put("orderNo", orderNo);
        record.put("summary", summary);
        record.put("counterparty", counterparty);
        record.put("fundItem", fundItem);
        record.put("income", income);
        record.put("expense", expense);
        record.put("balance", balance);
        return record;
    }

    private void assertRoot(JSONObject param) {
        AppUser user = userService.getById(param.getString("userId"));
        if (user == null || user.getIsRoot() == null || user.getIsRoot() != 1) {
            throw new AppRunTimeException("只有老板账号可以查看收支记录");
        }
    }

    private void appendEquals(StringBuilder sql, List<Object> params, String column, String value) {
        if (StringUtils.isEmpty(value)) return;
        sql.append(" AND ").append(column).append(" = ?");
        params.add(value);
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

    private long longValue(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void validateItem(String name, String itemType) {
        if (StringUtils.isEmpty(name)) {
            throw new AppRunTimeException("请输入项目名称");
        }
        if (!"income".equals(itemType) && !"expense".equals(itemType)) {
            throw new AppRunTimeException("项目类别不正确");
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String formatDate(double value) {
        if (value <= 0) return "";
        return Instant.ofEpochMilli((long) value).atZone(ZONE_ID).format(DATE_FORMATTER);
    }

    private static String businessTimeSql(String alias) {
        return "CASE WHEN typeof(" + alias + ".create_time) IN ('integer', 'real') " +
                "THEN CAST(" + alias + ".create_time AS INTEGER) " +
                "ELSE COALESCE(CAST(strftime('%s', " + alias + ".create_time) AS INTEGER) * 1000, 0) END";
    }

    private static class SqlAndParams {
        private final String sql;
        private final List<Object> params;

        private SqlAndParams(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
}
