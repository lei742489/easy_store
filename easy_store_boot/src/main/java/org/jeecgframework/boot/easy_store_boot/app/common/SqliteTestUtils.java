package org.jeecgframework.boot.easy_store_boot.app.common;

import com.alibaba.fastjson.JSONObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 底层查询测试类，无需关注
 */
public class SqliteTestUtils {

   // private static final String DB_URL = "jdbc:sqlite:D:/db/sqlite/easy_store.db";
   private static final String DB_URL = "jdbc:sqlite:D:/Zhihuiji/zhjd/273a47085fb64eec90671e65bde00ab0.db";

    public static void main(String[] args) {
        String keyword = "498到帐"; // 搜索的关键字
        SqliteTestUtils searcher = new SqliteTestUtils();
        List<MatchResult> results = searcher.searchKeyword(keyword);

        if (results.isEmpty()) {
            System.out.println("未找到匹配记录");
        } else {
            for (MatchResult r : results) {
                System.out.println(r);
            }
        }
    }

    public List<MatchResult> searchKeyword(String keyword) {
        List<MatchResult> results = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn == null) {
                System.err.println("数据库连接失败");
                return results;
            }

            // 1. 获取所有非系统表
            List<String> tables = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
            }

            // 2. 遍历表，找字段
            for (String table : tables) {
                List<ColumnInfo> columns = getTableColumns(conn, table);
                for (ColumnInfo col : columns) {
                    if (col.type.contains("CHAR") || col.type.contains("TEXT") || col.type.contains("CLOB")) {
                        int count = countKeywordInColumn(conn, table, col.name, keyword);
                        if (count > 0) {
                            results.add(new MatchResult(table, col.name, count));
                        }
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }

    private List<ColumnInfo> getTableColumns(Connection conn, String table) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        String pragmaSql = "PRAGMA table_info(" + table + ")";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(pragmaSql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type").toUpperCase();
                columns.add(new ColumnInfo(name, type));
            }
        }
        return columns;
    }

    private int countKeywordInColumn(Connection conn, String table, String column, String keyword) throws SQLException {
        String sql = "SELECT COUNT(*) AS cnt FROM " + table + " WHERE " + column + " LIKE ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + keyword + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        }
        return 0;
    }

    private static class ColumnInfo {
        String name;
        String type;

        ColumnInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    public static class MatchResult {
        String tableName;
        String columnName;
        int matchCount;

        MatchResult(String tableName, String columnName, int matchCount) {
            this.tableName = tableName;
            this.columnName = columnName;
            this.matchCount = matchCount;
        }

        @Override
        public String toString() {
            return String.format("Table: %s, Column: %s, Matches: %d", tableName, columnName, matchCount);
        }
    }


    /**
     * 查询指定表的所有记录并返回 List<Map<String, Object>>
     *
     * @return List<Map<String, Object>> 结果集
     */
    public List<JSONObject> queryAllFromTable(String sql) {
        List<JSONObject> resultList = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn == null) {
                System.err.println("数据库连接失败");
                return resultList;
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    JSONObject rowMap = new JSONObject();
                    for (int i = 1; i <= columnCount; i++) {
                        String column = metaData.getColumnLabel(i); // 或 getColumnName(i)
                        Object value = rs.getObject(i);
                        rowMap.put(column, value);
                    }
                    resultList.add(rowMap);
                }

            }
        } catch (SQLException e) {
            System.err.println("查询sql 时出错: " + e.getMessage());
        }

        return resultList;
    }

}
