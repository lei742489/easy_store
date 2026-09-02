package org.jeecgframework.boot.easy_store_boot.app.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Small database-dialect facade for SQL assembled by the application.
 *
 * <p>The project supports both the local SQLite database and the online
 * MySQL database.  Detecting the actual connection keeps the SQL choice
 * independent from the active Spring profile.</p>
 */
@Component
public class DatabaseDialect {

    @Autowired
    private DataSource dataSource;

    private volatile Boolean mysql;

    public boolean isMySql() {
        Boolean value = mysql;
        if (value != null) {
            return value;
        }
        synchronized (this) {
            if (mysql == null) {
                mysql = detectMySql();
            }
            return mysql;
        }
    }

    public String currentTimestamp() {
        return isMySql() ? "CURRENT_TIMESTAMP(3)" : "strftime('%Y-%m-%d %H:%M:%f', 'now')";
    }

    public String currentTimestampMinusDays(int days) {
        if (isMySql()) {
            return "DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL " + days + " DAY)";
        }
        return "strftime('%Y-%m-%d %H:%M:%f', 'now', '-" + days + " days')";
    }

    /**
     * Converts either a millisecond timestamp or a datetime value to epoch
     * milliseconds.  Older SQLite data stores both formats, so the same
     * behavior is retained for MySQL imports.
     */
    public String epochMillis(String expression) {
        if (isMySql()) {
            return "CASE WHEN CAST(" + expression + " AS CHAR) REGEXP '^[0-9]{10,}$' " +
                    "THEN CAST(" + expression + " AS UNSIGNED) " +
                    "ELSE COALESCE(UNIX_TIMESTAMP(" + expression + ") * 1000, 0) END";
        }
        return "CASE WHEN typeof(" + expression + ") IN ('integer', 'real') " +
                "THEN CAST(" + expression + " AS INTEGER) " +
                "ELSE COALESCE(CAST(strftime('%s', " + expression + ") AS INTEGER) * 1000, 0) END";
    }

    public String integerCast(String expression) {
        return isMySql() ? "CAST(" + expression + " AS SIGNED)" : "CAST(" + expression + " AS INTEGER)";
    }

    public String dateBucket(String epochMillisExpression, boolean month) {
        String format = month ? "%Y-%m" : "%Y-%m-%d";
        if (isMySql()) {
            return "DATE_FORMAT(FROM_UNIXTIME((" + epochMillisExpression + ") / 1000), '" + format + "')";
        }
        return "strftime('" + format + "', (" + epochMillisExpression + ") / 1000, 'unixepoch', '+8 hours')";
    }

    private boolean detectMySql() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("mysql");
        } catch (SQLException e) {
            throw new IllegalStateException("无法识别当前数据库类型", e);
        }
    }
}
