package org.jeecgframework.boot.easy_store_boot.app.config;

import org.jeecgframework.boot.easy_store_boot.app.common.DatabaseDialect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Adds order update metadata to existing SQLite and MySQL databases.
 *
 * The project is deployed against databases created by different versions,
 * so these columns must be checked at runtime instead of relying only on a
 * fresh database schema.
 */
@Component
public class AppOrderUpdateMetadataInitializer {

    private static final String[] ORDER_TABLES = {
            "app_sale_order",
            "app_purchase_order",
            "app_receive_payment_voucher",
            "app_payment_voucher"
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseDialect databaseDialect;

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void init() {
        String textType = databaseDialect.isMySql() ? "VARCHAR(100)" : "TEXT";
        for (String tableName : ORDER_TABLES) {
            addColumnIfAbsent(tableName, "update_by", textType);
            addColumnIfAbsent(tableName, "update_time", "DATETIME");
        }
        repairOrderDateTimeColumns();
        addSaleItemCostColumns();
        createStockLedgerTable();
        createSaleCostAdjustmentTable();
        createSalePendingGoodsTable();
    }

    private void repairOrderDateTimeColumns() {
        String[] tables = {
                "app_sale_order",
                "app_purchase_order",
                "app_receive_payment_voucher",
                "app_payment_voucher"
        };
        for (String table : tables) {
            convertColumnToDateTime(table, "create_time");
            convertColumnToDateTime(table, "update_time");
        }
    }

    private void convertColumnToDateTime(String tableName, String columnName) {
        if (!databaseDialect.isMySql()) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " DATETIME");
        } catch (Exception ignored) {
            // Column may already be DATETIME or the table may not exist yet.
        }
    }

    private void addSaleItemCostColumns() {
        String textType = databaseDialect.isMySql() ? "VARCHAR(20)" : "TEXT";
        addColumnIfAbsent("app_sale_order_item", "cost_amount", "REAL DEFAULT 0");
        addColumnIfAbsent("app_sale_order_item", "cost_adjust_amount", "REAL DEFAULT 0");
        addColumnIfAbsent("app_sale_order_item", "cost_status", textType + " DEFAULT 'NORMAL'");
        jdbcTemplate.update("UPDATE app_sale_order_item SET cost_amount = 0 WHERE cost_amount IS NULL");
        jdbcTemplate.update("UPDATE app_sale_order_item SET cost_adjust_amount = 0 WHERE cost_adjust_amount IS NULL");
        jdbcTemplate.update("UPDATE app_sale_order_item SET cost_status = 'NORMAL' WHERE cost_status IS NULL OR cost_status = ''");
    }

    private void createSaleCostAdjustmentTable() {
        if (databaseDialect.isMySql()) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_sale_cost_adjustment (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "goods_id VARCHAR(100) NOT NULL," +
                    "sale_order_id INT," +
                    "sale_item_id INT NOT NULL," +
                    "source_type VARCHAR(40)," +
                    "source_item_id INT," +
                    "quantity DECIMAL(18,4) DEFAULT 0," +
                    "estimate_unit_price DECIMAL(18,4) DEFAULT 0," +
                    "actual_unit_price DECIMAL(18,4) DEFAULT 0," +
                    "adjust_amount DECIMAL(18,2) DEFAULT 0," +
                    "create_time DATETIME," +
                    "is_del INT DEFAULT 0" +
                    ")");
        } else {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_sale_cost_adjustment (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "goods_id TEXT NOT NULL," +
                    "sale_order_id INTEGER," +
                    "sale_item_id INTEGER NOT NULL," +
                    "source_type TEXT," +
                    "source_item_id INTEGER," +
                    "quantity REAL DEFAULT 0," +
                    "estimate_unit_price REAL DEFAULT 0," +
                    "actual_unit_price REAL DEFAULT 0," +
                    "adjust_amount REAL DEFAULT 0," +
                    "create_time DATETIME," +
                    "is_del INTEGER DEFAULT 0" +
                    ")");
        }
        createIndexIfAbsent("CREATE INDEX idx_sale_cost_adjustment_goods " +
                "ON app_sale_cost_adjustment(goods_id, is_del)");
        createIndexIfAbsent("CREATE INDEX idx_sale_cost_adjustment_sale_item " +
                "ON app_sale_cost_adjustment(sale_item_id, is_del)");
    }

    private void createStockLedgerTable() {
        if (databaseDialect.isMySql()) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_stock_ledger (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "goods_id VARCHAR(100) NOT NULL," +
                    "seq_no INT DEFAULT 0," +
                    "business_time DATETIME," +
                    "business_type VARCHAR(40)," +
                    "business_no VARCHAR(100)," +
                    "source_table VARCHAR(100)," +
                    "source_id INT," +
                    "source_item_id INT," +
                    "in_qty DECIMAL(18,4) DEFAULT 0," +
                    "in_price DECIMAL(18,4) DEFAULT 0," +
                    "in_amount DECIMAL(18,2) DEFAULT 0," +
                    "out_qty DECIMAL(18,4) DEFAULT 0," +
                    "out_price DECIMAL(18,4) DEFAULT 0," +
                    "out_amount DECIMAL(18,2) DEFAULT 0," +
                    "cost_price DECIMAL(18,4) DEFAULT 0," +
                    "cost_amount DECIMAL(18,2) DEFAULT 0," +
                    "before_qty DECIMAL(18,4) DEFAULT 0," +
                    "before_amount DECIMAL(18,2) DEFAULT 0," +
                    "after_qty DECIMAL(18,4) DEFAULT 0," +
                    "after_amount DECIMAL(18,2) DEFAULT 0," +
                    "after_cost_price DECIMAL(18,4) DEFAULT 0," +
                    "is_calc INT DEFAULT 0," +
                    "create_time DATETIME," +
                    "update_time DATETIME," +
                    "is_del INT DEFAULT 0" +
                    ")");
        } else {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_stock_ledger (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "goods_id TEXT NOT NULL," +
                    "seq_no INTEGER DEFAULT 0," +
                    "business_time DATETIME," +
                    "business_type TEXT," +
                    "business_no TEXT," +
                    "source_table TEXT," +
                    "source_id INTEGER," +
                    "source_item_id INTEGER," +
                    "in_qty REAL DEFAULT 0," +
                    "in_price REAL DEFAULT 0," +
                    "in_amount REAL DEFAULT 0," +
                    "out_qty REAL DEFAULT 0," +
                    "out_price REAL DEFAULT 0," +
                    "out_amount REAL DEFAULT 0," +
                    "cost_price REAL DEFAULT 0," +
                    "cost_amount REAL DEFAULT 0," +
                    "before_qty REAL DEFAULT 0," +
                    "before_amount REAL DEFAULT 0," +
                    "after_qty REAL DEFAULT 0," +
                    "after_amount REAL DEFAULT 0," +
                    "after_cost_price REAL DEFAULT 0," +
                    "is_calc INTEGER DEFAULT 0," +
                    "create_time DATETIME," +
                    "update_time DATETIME," +
                    "is_del INTEGER DEFAULT 0" +
                    ")");
        }
        if (databaseDialect.isMySql()) {
            try {
                jdbcTemplate.update("UPDATE app_stock_ledger SET is_del = 0 WHERE is_del IS NULL");
                jdbcTemplate.update("UPDATE app_goods SET is_del = 0 WHERE is_del IS NULL");
            } catch (Exception ignored) {
                // Legacy databases may not have all columns before other initializers finish.
            }
        }
        createIndexIfAbsent("CREATE INDEX idx_stock_ledger_goods_time " +
                "ON app_stock_ledger(goods_id, business_time, seq_no, is_del)");
        createIndexIfAbsent("CREATE INDEX idx_stock_ledger_del_time_goods " +
                "ON app_stock_ledger(is_del, business_time, goods_id)");
        createIndexIfAbsent("CREATE INDEX idx_stock_ledger_goods_del_time_seq " +
                "ON app_stock_ledger(goods_id, is_del, business_time, seq_no, id)");
        createIndexIfAbsent("CREATE INDEX idx_stock_ledger_source " +
                "ON app_stock_ledger(source_table, source_id, source_item_id, is_del)");
        createIndexIfAbsent("CREATE INDEX idx_stock_ledger_business_no " +
                "ON app_stock_ledger(business_no, is_del)");
    }

    private void createSalePendingGoodsTable() {
        if (databaseDialect.isMySql()) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_sale_pending_goods (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "order_id INT NOT NULL," +
                    "order_item_id INT," +
                    "goods_name VARCHAR(255) NOT NULL," +
                    "category_id VARCHAR(100)," +
                    "unit VARCHAR(100)," +
                    "unit_price DECIMAL(18,4) DEFAULT 0," +
                    "status INT DEFAULT 0," +
                    "goods_id VARCHAR(100)," +
                    "create_time DATETIME," +
                    "update_time DATETIME," +
                    "is_del INT DEFAULT 0" +
                    ")");
        } else {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_sale_pending_goods (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "order_id INTEGER NOT NULL," +
                    "order_item_id INTEGER," +
                    "goods_name TEXT NOT NULL," +
                    "category_id TEXT," +
                    "unit TEXT," +
                    "unit_price REAL DEFAULT 0," +
                    "status INTEGER DEFAULT 0," +
                    "goods_id TEXT," +
                    "create_time DATETIME," +
                    "update_time DATETIME," +
                    "is_del INTEGER DEFAULT 0" +
                    ")");
        }
        createIndexIfAbsent("CREATE INDEX idx_sale_pending_goods_order " +
                "ON app_sale_pending_goods(order_id, status, is_del)");
        createIndexIfAbsent("CREATE INDEX idx_sale_pending_goods_item " +
                "ON app_sale_pending_goods(order_item_id, is_del)");
    }

    private void addColumnIfAbsent(String tableName, String columnName, String columnType) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName
                    + " ADD COLUMN " + columnName + " " + columnType);
        } catch (Exception ignored) {
            // The column already exists, or the table is not available yet.
        }
    }

    private void createIndexIfAbsent(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
            // The index already exists, or the table is not available yet.
        }
    }
}
