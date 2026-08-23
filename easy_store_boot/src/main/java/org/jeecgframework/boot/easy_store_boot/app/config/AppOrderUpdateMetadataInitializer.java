package org.jeecgframework.boot.easy_store_boot.app.config;

import org.jeecgframework.boot.easy_store_boot.app.common.DatabaseDialect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
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
    public void init() {
        String textType = databaseDialect.isMySql() ? "VARCHAR(100)" : "TEXT";
        for (String tableName : ORDER_TABLES) {
            addColumnIfAbsent(tableName, "update_by", textType);
            addColumnIfAbsent(tableName, "update_time", "DATETIME");
        }
        createSalePendingGoodsTable();
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
