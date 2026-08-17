package org.jeecgframework.boot.easy_store_boot.app.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

import org.jeecgframework.boot.easy_store_boot.app.common.CommonUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.PasswordUtil;

@Component
public class AppPermissionInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        createTables();
        addOperationLogMenuNameColumn();
        addUserRoleColumn();
        addUserCommissionRateColumn();
        addOrderCashierNameColumns();
        addGoodsCostColumns();
        addStockStatisticIndexes();
        addSaleGrossProfitColumns();
        backfillOrderStatus();
        repairUnitNames();
        repairDateColumns();
        repairLegacyUsers();
        initHomeMenus();
        repairSaleStatisticsMenu();
        repairPurchaseStatisticsMenu();
        repairProfitStatisticsMenu();
        repairCashierStatisticsMenu();
        repairFundStatisticsMenu();
        repairIncomeExpenseRecordMenu();
        repairStockStatisticsMenu();
        repairStockWarningMenu();
        repairStockCheckMenu();
    }

    private void createTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_role (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "remarks TEXT," +
                "status INTEGER DEFAULT 1," +
                "create_time DATETIME," +
                "is_del INTEGER DEFAULT 0" +
                ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_home_menu (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "code TEXT NOT NULL UNIQUE," +
                "group_code TEXT NOT NULL," +
                "group_title TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "icon TEXT," +
                "url TEXT," +
                "action TEXT," +
                "sort_no INTEGER DEFAULT 0," +
                "status INTEGER DEFAULT 1," +
                "root_only INTEGER DEFAULT 0," +
                "create_time DATETIME," +
                "is_del INTEGER DEFAULT 0" +
                ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_role_menu (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "role_id INTEGER NOT NULL," +
                "menu_id INTEGER NOT NULL," +
                "create_time DATETIME," +
                "UNIQUE(role_id, menu_id)" +
                ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_role_permission (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "role_id INTEGER NOT NULL," +
                "permission_code TEXT NOT NULL," +
                "create_time DATETIME," +
                "UNIQUE(role_id, permission_code)" +
                ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_customer_quote (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "customer_id TEXT NOT NULL," +
                "customer_name TEXT," +
                "customer_py_code TEXT," +
                "goods_id TEXT NOT NULL," +
                "goods_title TEXT," +
                "goods_py_code TEXT," +
                "unit TEXT," +
                "sale_prc REAL," +
                "trade_prc REAL," +
                "quote_price REAL," +
                "note TEXT," +
                "create_time DATETIME," +
                "update_time DATETIME," +
                "is_del INTEGER DEFAULT 0" +
                ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_stock_check (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "order_no TEXT NOT NULL," +
                "cashier_id TEXT," +
                "cashier_name TEXT," +
                "profit_loss_quantity REAL DEFAULT 0," +
                "profit_loss_amount REAL DEFAULT 0," +
                "note TEXT," +
                "create_time DATETIME," +
                "update_time DATETIME," +
                "is_del INTEGER DEFAULT 0" +
                ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_stock_check_item (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "goods_id TEXT NOT NULL," +
                "unit TEXT," +
                "book_quantity INTEGER DEFAULT 0," +
                "actual_quantity INTEGER DEFAULT 0," +
                "profit_loss_quantity INTEGER DEFAULT 0," +
                "unit_price REAL DEFAULT 0," +
                "profit_loss_amount REAL DEFAULT 0," +
                "check_id INTEGER NOT NULL," +
                "note TEXT," +
                "create_time DATETIME," +
                "update_time DATETIME," +
                "is_del INTEGER DEFAULT 0" +
                ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_income_expense_item (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "item_type TEXT NOT NULL," +
                "participate_performance INTEGER DEFAULT 1," +
                "disabled INTEGER DEFAULT 0," +
                "create_time DATETIME," +
                "update_time DATETIME," +
                "is_del INTEGER DEFAULT 0" +
                ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_income_expense_record (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "order_no TEXT NOT NULL," +
                "settle_id TEXT NOT NULL," +
                "cashier_id TEXT," +
                "cashier_name TEXT," +
                "summary TEXT NOT NULL," +
                "counterparty TEXT," +
                "fund_item TEXT NOT NULL," +
                "income REAL DEFAULT 0," +
                "expense REAL DEFAULT 0," +
                "create_time DATETIME," +
                "is_del INTEGER DEFAULT 0" +
                ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_operation_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "operator_id TEXT," +
                "operator_name TEXT," +
                "menu_name TEXT," +
                "operation_type TEXT NOT NULL," +
                "request_uri TEXT," +
                "client_ip TEXT," +
                "operate_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "data_json TEXT" +
                ")");

        createIndexIfAbsent("CREATE INDEX IF NOT EXISTS idx_operation_log_time " +
                "ON app_operation_log(operate_time)");
        createIndexIfAbsent("CREATE INDEX IF NOT EXISTS idx_operation_log_operator " +
                "ON app_operation_log(operator_id)");

        createIndexIfAbsent("CREATE INDEX IF NOT EXISTS idx_income_expense_record_time_del " +
                "ON app_income_expense_record(create_time, is_del)");
        createIndexIfAbsent("CREATE INDEX IF NOT EXISTS idx_income_expense_item_type_del " +
                "ON app_income_expense_item(item_type, disabled, is_del)");

        try {
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_app_customer_quote_customer_goods " +
                    "ON app_customer_quote(customer_id, goods_id) WHERE is_del = 0");
        } catch (Exception ignored) {
        }
    }

    private void addOperationLogMenuNameColumn() {
        addColumnIfAbsent("app_operation_log", "menu_name", "TEXT");
    }

    private void addUserRoleColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE app_user ADD COLUMN role_id INTEGER");
        } catch (Exception ignored) {
        }
    }

    private void addUserCommissionRateColumn() {
        addColumnIfAbsent("app_user", "commission_rate", "REAL DEFAULT 0");
        jdbcTemplate.update("UPDATE app_user SET commission_rate = 0 WHERE commission_rate IS NULL");
    }

    private void addOrderCashierNameColumns() {
        addColumnIfAbsent("app_sale_order", "cashier_name", "TEXT");
        addColumnIfAbsent("app_purchase_order", "cashier_name", "TEXT");
        addColumnIfAbsent("app_receive_payment_voucher", "cashier_name", "TEXT");
        addColumnIfAbsent("app_payment_voucher", "cashier_name", "TEXT");
        addColumnIfAbsent("app_sale_order", "status", "INTEGER DEFAULT 1");
        addColumnIfAbsent("app_purchase_order", "status", "INTEGER DEFAULT 1");
        addColumnIfAbsent("app_receive_payment_voucher", "status", "INTEGER DEFAULT 1");
        addColumnIfAbsent("app_payment_voucher", "status", "INTEGER DEFAULT 1");
    }

    private void addGoodsCostColumns() {
        addColumnIfAbsent("app_goods", "stock_cost", "REAL DEFAULT 0");
        addColumnIfAbsent("app_goods", "cost_price", "REAL DEFAULT 0");
        jdbcTemplate.update("UPDATE app_goods SET stock_cost = COALESCE(stock_cost, COALESCE(stock, 0) * COALESCE(init_cost, pur_prc, 0)) WHERE stock_cost IS NULL");
        jdbcTemplate.update("UPDATE app_goods SET cost_price = COALESCE(cost_price, COALESCE(init_cost, pur_prc, 0)) WHERE cost_price IS NULL");
    }

    private void addStockStatisticIndexes() {
        createIndexIfAbsent("CREATE INDEX IF NOT EXISTS idx_purchase_item_goods_del " +
                "ON app_purchase_order_item(goods_id, is_del)");
        createIndexIfAbsent("CREATE INDEX IF NOT EXISTS idx_sale_item_goods_del " +
                "ON app_sale_order_item(goods_id, is_del)");
        createIndexIfAbsent("CREATE INDEX IF NOT EXISTS idx_stock_check_item_goods_del " +
                "ON app_stock_check_item(goods_id, is_del)");
        createIndexIfAbsent("CREATE INDEX IF NOT EXISTS idx_stock_check_item_check_del " +
                "ON app_stock_check_item(check_id, is_del)");
    }

    private void createIndexIfAbsent(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
        }
    }

    private void addSaleGrossProfitColumns() {
        addColumnIfAbsent("app_sale_order", "gross_profit", "REAL DEFAULT 0");
        addColumnIfAbsent("app_sale_order_item", "gross_profit", "REAL DEFAULT 0");
        jdbcTemplate.update("UPDATE app_sale_order SET gross_profit = 0 WHERE gross_profit IS NULL");
        jdbcTemplate.update("UPDATE app_sale_order_item SET gross_profit = 0 WHERE gross_profit IS NULL");
    }

    private void repairUnitNames() {
        repairUnitName("app_goods");
        repairUnitName("app_purchase_order_item");
        repairUnitName("app_sale_order_item");
    }

    private void repairUnitName(String tableName) {
        jdbcTemplate.update("UPDATE " + tableName + " SET unit = " +
                "(SELECT name FROM app_unit WHERE app_unit.id = CAST(" + tableName + ".unit AS INTEGER)) " +
                "WHERE unit IS NOT NULL AND unit <> '' AND unit NOT GLOB '*[^0-9]*' " +
                "AND EXISTS (SELECT 1 FROM app_unit WHERE app_unit.id = CAST(" + tableName + ".unit AS INTEGER))");
    }

    private void backfillOrderStatus() {
        jdbcTemplate.update("UPDATE app_sale_order SET status = 1 WHERE status IS NULL");
        jdbcTemplate.update("UPDATE app_purchase_order SET status = 1 WHERE status IS NULL");
        jdbcTemplate.update("UPDATE app_receive_payment_voucher SET status = 1 WHERE status IS NULL");
        jdbcTemplate.update("UPDATE app_payment_voucher SET status = 1 WHERE status IS NULL");
    }

    private void addColumnIfAbsent(String tableName, String columnName, String columnType) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
        } catch (Exception ignored) {
        }
    }

    private void repairDateColumns() {
        repairDateColumn("app_role", "create_time");
        repairDateColumn("app_home_menu", "create_time");
        repairDateColumn("app_role_menu", "create_time");
        repairDateColumn("app_role_permission", "create_time");
        repairDateColumn("app_customer_quote", "create_time");
        repairDateColumn("app_customer_quote", "update_time");
        repairDateColumn("app_stock_check", "create_time");
        repairDateColumn("app_stock_check", "update_time");
        repairDateColumn("app_stock_check_item", "create_time");
        repairDateColumn("app_stock_check_item", "update_time");
        repairDateColumn("app_goods_category", "create_time");
    }

    private void repairDateColumn(String tableName, String columnName) {
        jdbcTemplate.update("UPDATE " + tableName + " SET " + columnName + " = " +
                "strftime('%Y-%m-%d %H:%M:%f', " + columnName + ") " +
                "WHERE " + columnName + " IS NOT NULL " +
                "AND " + columnName + " <> '' " +
                "AND instr(" + columnName + ", '.') = 0");
    }

    private void repairLegacyUsers() {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, user_name, password, salt FROM app_user WHERE password IS NULL OR password = '' OR salt IS NULL OR salt = ''");
        for (Map<String, Object> user : users) {
            Object id = user.get("id");
            Object userNameObj = user.get("user_name");
            if (id == null || userNameObj == null) {
                continue;
            }
            String userName = String.valueOf(userNameObj);
            String salt = CommonUtils.generateCaptcha(8);
            String password = PasswordUtil.encrypt(userName, "123456", salt);
            jdbcTemplate.update("UPDATE app_user SET salt = ?, password = ? WHERE id = ?", salt, password, id);
        }
    }

    private void initHomeMenus() {
        insertMenu("sale_order_add", "common", "常用功能", "销售单", "sm1.png", "", "SaleOrderModalRef", 10, 1, 0, 0);
        insertMenu("receive_payment_add", "common", "常用功能", "收款单", "sm2.png", "", "ReceivePaymentVoucherModal", 20, 1, 0, 0);
        insertMenu("purchase_order_add", "common", "常用功能", "进货单", "sm3.png", "", "PurchaseOrderModal", 30, 1, 0, 0);
        insertMenu("payment_add", "common", "常用功能", "付款单", "sm4.png", "", "PaymentVoucherModal", 40, 1, 0, 0);
        insertMenu("goods", "common", "常用功能", "库存管理", "sm5.png", "/custom/goods", "", 50, 1, 0, 0);
        insertMenu("customer", "common", "常用功能", "客户管理", "sm6.png", "/custom/customer", "", 60, 1, 0, 0);
        insertMenu("supplier", "common", "常用功能", "供应商管理", "sm7.png", "/custom/supplier", "", 70, 1, 0, 0);
        insertMenu("customer_quote", "common", "常用功能", "大客户报价", "sm8.png", "/custom/customerQuote", "", 80, 1, 0, 1);
        insertMenu("account_settle", "common", "常用功能", "结算账户", "sm9.png", "/custom/accountSettle", "", 90, 1, 0, 0);

        insertMenu("sale_order_list", "sale", "销售相关", "销售单查询", "dm1.png", "/custom/salesOrder", "", 110, 1, 0, 0);
        insertMenu("receive_payment_list", "sale", "销售相关", "收款单查询", "sm2.png", "/custom/appReceivePaymentVoucher", "", 120, 1, 0, 0);
        insertMenu("sale_stats", "sale", "销售相关", "销售统计", "dm2.png", "/custom/salesStatistics", "", 130, 1, 0, 0);
        insertMenu("customer_statement", "sale", "销售相关", "应收对账单", "dm4.png", "/custom/receivableStatement", "", 140, 1, 0, 0);
        insertMenu("debt_stats", "sale", "销售相关", "欠款统计", "dm3.png", "/custom/debtStatistics", "", 150, 1, 0, 0);
        insertMenu("debt_detail", "sale", "销售相关", "欠款明细", "dm5.png", "/custom/debtDetail", "", 160, 1, 0, 0);

        insertMenu("purchase_order_list", "purchase", "进货/库存", "进货单查询", "jm1.png", "/custom/appSaleOrder", "", 210, 1, 0, 0);
        insertMenu("payment_list", "purchase", "进货/库存", "付款单查询", "sm4.png", "/custom/appPaymentVoucher", "", 220, 1, 0, 0);
        insertMenu("purchase_stats", "purchase", "进货/库存", "进货统计", "jm2.png", "/custom/purchaseStatistics", "", 230, 1, 0, 0);
        insertMenu("payable_order", "purchase", "进货/库存", "应付对帐单", "jm3.png", "/custom/payableStatement", "", 240, 1, 0, 0);
        insertMenu("payable_stats", "purchase", "进货/库存", "应付统计", "jm4.png", "/custom/payableStatistics", "", 250, 1, 0, 0);
        insertMenu("payable_detail", "purchase", "进货/库存", "应付明细", "jm5.png", "/custom/payableDetail", "", 260, 1, 0, 0);
        insertMenu("stock_stats", "purchase", "进货/库存", "库存统计", "cc1.png", "/custom/stockStatistics", "", 270, 1, 0, 0);
        insertMenu("stock_warning", "purchase", "进货/库存", "库存预警", "cc2.png", "/custom/stockWarning", "", 280, 1, 0, 0);
        insertMenu("stock_check", "purchase", "进货/库存", "库存盘点", "cc3.png", "/custom/stockCheck", "", 290, 1, 0, 0);

        insertMenu("fund_stats", "report", "统计报告", "资金统计", "tj1.png", "/custom/fundStatistics", "", 310, 1, 0, 0);
        insertMenu("profit_stats", "report", "统计报告", "利润统计", "tj2.png", "/custom/profitStatistics", "", 320, 1, 0, 0);
        insertMenu("cashier_stats", "report", "统计报告", "营业员统计", "tj3.png", "/custom/cashierStatistics", "", 330, 1, 0, 0);

        insertMenu("app_user", "other", "其它功能", "员工管理", "icon-user-group", "/custom/appUser", "", 410, 1, 1, 0);
        insertMenu("app_role", "other", "其它功能", "角色管理", "icon-safe", "/custom/appRole", "", 420, 1, 1, 0);
        insertMenu("app_unit", "other", "其它功能", "单位管理", "icon-storage", "/custom/appUnit", "", 430, 1, 1, 0);
        insertMenu("income_expense_record", "other", "其它功能", "收支记录", "icon-book", "/custom/incomeExpenseRecord", "", 440, 1, 1, 0);
        insertMenu("operation_log", "other", "其它功能", "操作日志", "icon-history", "/custom/appOperationLog", "", 450, 1, 1, 0);
    }

    private void insertMenu(String code, String groupCode, String groupTitle, String name,
                            String icon, String url, String action, Integer sortNo,
                            Integer status, Integer rootOnly, Integer isDel) {
        jdbcTemplate.update("INSERT OR IGNORE INTO app_home_menu " +
                        "(code, group_code, group_title, name, icon, url, action, sort_no, status, root_only, create_time, is_del) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, strftime('%Y-%m-%d %H:%M:%f', 'now'), ?)",
                code, groupCode, groupTitle, name, icon, url, action, sortNo, status, rootOnly, isDel);
    }

    private void repairStockStatisticsMenu() {
        jdbcTemplate.update("UPDATE app_home_menu SET url = ? WHERE code = ? " +
                        "AND (url IS NULL OR trim(url) = '')",
                "/custom/stockStatistics", "stock_stats");
    }

    private void repairSaleStatisticsMenu() {
        jdbcTemplate.update("UPDATE app_home_menu SET url = ? WHERE code = ? " +
                        "AND (url IS NULL OR trim(url) = '')",
                "/custom/salesStatistics", "sale_stats");
    }

    private void repairPurchaseStatisticsMenu() {
        jdbcTemplate.update("UPDATE app_home_menu SET url = ? WHERE code = ? " +
                        "AND (url IS NULL OR trim(url) = '')",
                "/custom/purchaseStatistics", "purchase_stats");
    }

    private void repairProfitStatisticsMenu() {
        jdbcTemplate.update("UPDATE app_home_menu SET url = ? WHERE code = ? " +
                        "AND (url IS NULL OR trim(url) = '')",
                "/custom/profitStatistics", "profit_stats");
    }

    private void repairCashierStatisticsMenu() {
        jdbcTemplate.update("UPDATE app_home_menu SET url = ? WHERE code = ? " +
                        "AND (url IS NULL OR trim(url) = '')",
                "/custom/cashierStatistics", "cashier_stats");
    }

    private void repairFundStatisticsMenu() {
        jdbcTemplate.update("UPDATE app_home_menu SET url = ? WHERE code = ? " +
                        "AND (url IS NULL OR trim(url) = '')",
                "/custom/fundStatistics", "fund_stats");
    }

    private void repairIncomeExpenseRecordMenu() {
        jdbcTemplate.update("UPDATE app_home_menu SET url = ? WHERE code = ? " +
                        "AND (url IS NULL OR trim(url) = '')",
                "/custom/incomeExpenseRecord", "income_expense_record");
    }

    private void repairStockWarningMenu() {
        jdbcTemplate.update("UPDATE app_home_menu SET url = ? WHERE code = ? " +
                        "AND (url IS NULL OR trim(url) = '')",
                "/custom/stockWarning", "stock_warning");
    }

    private void repairStockCheckMenu() {
        jdbcTemplate.update("UPDATE app_home_menu SET url = ? WHERE code = ? " +
                        "AND (url IS NULL OR trim(url) = '')",
                "/custom/stockCheck", "stock_check");
        jdbcTemplate.update("UPDATE app_home_menu SET name = ? WHERE code = ? AND name = ?",
                "库存盘点", "stock_check", "盘点查询");
    }
}
