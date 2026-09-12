package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jeecgframework.boot.easy_store_boot.app.common.DatabaseDialect;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Maintains daily sales and purchase aggregates.
 *
 * <p>The source tables remain the source of truth. A changed business date is
 * rebuilt from source rows, so edits, deletes and returns do not accumulate
 * rounding or sign errors in the summary.</p>
 */
@Service
public class AppBusinessDailySummaryService {

    private static final Logger log = LoggerFactory.getLogger(AppBusinessDailySummaryService.class);
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SALE_AMOUNT_SQL =
            "COALESCE(i.total_amount, 0) * " +
                    "(CASE WHEN COALESCE(o.discount_rate, 100) <= 0 THEN 100 " +
                    "ELSE COALESCE(o.discount_rate, 100) END) / 100.0";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DatabaseDialect databaseDialect;
    @Autowired
    @Qualifier("myExecutor")
    private Executor executor;

    /**
     * Rebuilds the affected date after a sale order mutation.
     */
    public void refreshSaleDate(Date date) {
        refreshDatesAfterCommit(date);
    }

    /**
     * Rebuilds the affected date after a purchase order mutation.
     */
    public void refreshPurchaseDate(Date date) {
        refreshDatesAfterCommit(date);
    }

    /**
     * Rebuilds both summaries when a date may contain either order type.
     */
    public void refreshDates(Date... dates) {
        refreshDatesAfterCommit(dates);
    }

    /**
     * Summary rows are derived data. Do not rebuild them while the order
     * transaction is still holding order, item, goods and account locks.
     * Refreshing after commit also prevents two concurrent orders on the
     * same date from extending each other's lock wait.
     */
    private void refreshDatesAfterCommit(Date... dates) {
        if (dates == null) {
            return;
        }
        List<Date> validDates = new ArrayList<>();
        List<String> refreshed = new ArrayList<>();
        for (Date date : dates) {
            String dateText = dateText(date);
            if (dateText == null || refreshed.contains(dateText)) {
                continue;
            }
            refreshed.add(dateText);
            validDates.add(date);
        }
        if (validDates.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronizationAdapter() {
                        @Override
                        public void afterCommit() {
                            try {
                                executor.execute(() -> {
                                    try {
                                        refreshDatesNow(validDates);
                                    } catch (Exception ex) {
                                        log.error("刷新营业日报汇总失败", ex);
                                    }
                                });
                            } catch (Exception ex) {
                                log.error("提交后投递营业日报汇总任务失败", ex);
                            }
                        }
                    });
            return;
        }
        refreshDatesNow(validDates);
    }

    private void refreshDatesNow(Collection<Date> dates) {
        if (dates == null || dates.isEmpty()) {
            return;
        }
        for (Date date : dates) {
            refreshDate(date, true, true);
        }
    }

    /**
     * Refreshes dates for sales whose cost/profit was changed by stock
     * recalculation.
     */
    public void refreshSaleOrderIds(Collection<Integer> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        List<Integer> ids = new ArrayList<>();
        for (Integer orderId : orderIds) {
            if (orderId != null && !ids.contains(orderId)) {
                ids.add(orderId);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + databaseDialect.epochMillis("create_time") + " AS create_time " +
                        "FROM app_sale_order WHERE id IN (" + placeholders + ")",
                ids.toArray());
        List<Date> dates = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("create_time");
            if (value != null) {
                try {
                    dates.add(new Date(Long.parseLong(value.toString())));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed legacy timestamps.
                }
            }
        }
        refreshDates(dates.toArray(new Date[0]));
    }

    /**
     * Existing installations are populated once after the summary table is
     * created. Later starts skip this full scan because the table is nonempty.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void backfillExistingData(ApplicationReadyEvent event) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM app_business_daily_summary", Integer.class);
            if (count == null || count == 0) {
                rebuildAll();
            }
        } catch (Exception ignored) {
            // The schema initializer may not have completed on an old database.
        }
    }

    private void refreshDate(Date date, boolean sale, boolean purchase) {
        String dateText = dateText(date);
        if (dateText == null) {
            return;
        }
        LocalDate localDate = LocalDate.parse(dateText, DATE_FORMATTER);
        long startTime = localDate.atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        long endTime = localDate.plusDays(1).atStartOfDay(ZONE_ID).toInstant().toEpochMilli();

        jdbcTemplate.update("DELETE FROM app_business_daily_summary WHERE business_date = ?", dateText);
        insertDateSummary(dateText, startTime, endTime, sale, purchase);
    }

    private void insertDateSummary(String dateText, long startTime, long endTime,
                                   boolean sale, boolean purchase) {
        List<Object> params = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        if (sale) {
            parts.add("SELECT ? AS business_date, COALESCE(o.cashier_id, '-1') AS cashier_id, " +
                    "COALESCE(SUM(i.quantity), 0) AS sales_quantity, " +
                    "COALESCE(SUM(" + SALE_AMOUNT_SQL + "), 0) AS sales_amount, " +
                    "COALESCE(SUM(i.gross_profit), 0) AS sales_profit, " +
                    "0 AS purchase_quantity, 0 AS purchase_amount, " +
                    "COUNT(DISTINCT o.id) AS sales_order_count, 0 AS purchase_order_count " +
                    "FROM app_sale_order_item i " +
                    "INNER JOIN app_sale_order o ON o.id = i.order_id " +
                    "WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 " +
                    "AND COALESCE(i.is_del, 0) = 0 " +
                    "AND " + databaseDialect.epochMillis("o.create_time") + " >= ? " +
                    "AND " + databaseDialect.epochMillis("o.create_time") + " < ? " +
                    "GROUP BY COALESCE(o.cashier_id, '-1')");
            params.add(dateText);
            params.add(startTime);
            params.add(endTime);
        }
        if (purchase) {
            if (!parts.isEmpty()) {
                parts.add(" UNION ALL ");
            }
            parts.add("SELECT ? AS business_date, COALESCE(o.cashier_id, '-1') AS cashier_id, " +
                    "0 AS sales_quantity, 0 AS sales_amount, 0 AS sales_profit, " +
                    "COALESCE(SUM(i.quantity), 0) AS purchase_quantity, " +
                    "COALESCE(SUM(i.total_amount), 0) AS purchase_amount, " +
                    "0 AS sales_order_count, COUNT(DISTINCT o.id) AS purchase_order_count " +
                    "FROM app_purchase_order_item i " +
                    "INNER JOIN app_purchase_order o ON o.id = i.order_id " +
                    "WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 " +
                    "AND COALESCE(i.is_del, 0) = 0 " +
                    "AND " + databaseDialect.epochMillis("o.create_time") + " >= ? " +
                    "AND " + databaseDialect.epochMillis("o.create_time") + " < ? " +
                    "GROUP BY COALESCE(o.cashier_id, '-1')");
            params.add(dateText);
            params.add(startTime);
            params.add(endTime);
        }
        if (parts.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO app_business_daily_summary " +
                "(business_date, cashier_id, sales_quantity, sales_amount, sales_profit, " +
                "purchase_quantity, purchase_amount, sales_order_count, purchase_order_count, " +
                "create_time, update_time) " +
                "SELECT business_date, cashier_id, SUM(sales_quantity), SUM(sales_amount), " +
                "SUM(sales_profit), SUM(purchase_quantity), SUM(purchase_amount), " +
                "SUM(sales_order_count), SUM(purchase_order_count), " +
                databaseDialect.currentTimestamp() + ", " + databaseDialect.currentTimestamp() + " " +
                "FROM (" + String.join("", parts) + ") summary " +
                "GROUP BY business_date, cashier_id";
        jdbcTemplate.update(sql, params.toArray());
    }

    private void rebuildAll() {
        jdbcTemplate.update("DELETE FROM app_business_daily_summary");
        String saleDate = databaseDialect.dateBucket(databaseDialect.epochMillis("o.create_time"), false);
        String purchaseDate = databaseDialect.dateBucket(databaseDialect.epochMillis("o.create_time"), false);
        String sql = "INSERT INTO app_business_daily_summary " +
                "(business_date, cashier_id, sales_quantity, sales_amount, sales_profit, " +
                "purchase_quantity, purchase_amount, sales_order_count, purchase_order_count, " +
                "create_time, update_time) " +
                "SELECT business_date, cashier_id, SUM(sales_quantity), SUM(sales_amount), " +
                "SUM(sales_profit), SUM(purchase_quantity), SUM(purchase_amount), " +
                "SUM(sales_order_count), SUM(purchase_order_count), " +
                databaseDialect.currentTimestamp() + ", " + databaseDialect.currentTimestamp() + " " +
                "FROM (" +
                "SELECT " + saleDate + " AS business_date, COALESCE(o.cashier_id, '-1') AS cashier_id, " +
                "SUM(i.quantity) AS sales_quantity, SUM(" + SALE_AMOUNT_SQL + ") AS sales_amount, " +
                "SUM(i.gross_profit) AS sales_profit, 0 AS purchase_quantity, 0 AS purchase_amount, " +
                "COUNT(DISTINCT o.id) AS sales_order_count, 0 AS purchase_order_count " +
                "FROM app_sale_order_item i INNER JOIN app_sale_order o ON o.id = i.order_id " +
                "WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 AND COALESCE(i.is_del, 0) = 0 " +
                "GROUP BY " + saleDate + ", COALESCE(o.cashier_id, '-1') " +
                "UNION ALL " +
                "SELECT " + purchaseDate + " AS business_date, COALESCE(o.cashier_id, '-1') AS cashier_id, " +
                "0 AS sales_quantity, 0 AS sales_amount, 0 AS sales_profit, SUM(i.quantity), " +
                "SUM(i.total_amount), 0 AS sales_order_count, COUNT(DISTINCT o.id) " +
                "FROM app_purchase_order_item i INNER JOIN app_purchase_order o ON o.id = i.order_id " +
                "WHERE o.status = 1 AND COALESCE(o.is_del, 0) = 0 AND COALESCE(i.is_del, 0) = 0 " +
                "GROUP BY " + purchaseDate + ", COALESCE(o.cashier_id, '-1')" +
                ") summary GROUP BY business_date, cashier_id";
        jdbcTemplate.update(sql);
    }

    private String dateText(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER);
    }
}
