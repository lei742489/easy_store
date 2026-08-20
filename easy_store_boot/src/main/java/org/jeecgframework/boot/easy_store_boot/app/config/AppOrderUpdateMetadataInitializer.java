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
    }

    private void addColumnIfAbsent(String tableName, String columnName, String columnType) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName
                    + " ADD COLUMN " + columnName + " " + columnType);
        } catch (Exception ignored) {
            // The column already exists, or the table is not available yet.
        }
    }
}
