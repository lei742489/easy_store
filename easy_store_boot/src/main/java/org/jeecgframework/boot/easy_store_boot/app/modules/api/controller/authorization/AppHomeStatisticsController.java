package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
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
@RequestMapping("api/user/appHomeStatistics")
public class AppHomeStatisticsController {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DISCOUNTED_AMOUNT_SQL =
            "COALESCE(i.total_amount, 0) * " +
                    "(CASE WHEN COALESCE(o.discount_rate, 100) <= 0 THEN 100 " +
                    "ELSE COALESCE(o.discount_rate, 100) END) / 100.0";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IAppUserService userService;
    @Autowired
    private DatabaseDialect databaseDialect;

    @PostMapping("today")
    public Result<?> today(@RequestBody JSONObject param) {
        AppUser user = userService.getById(param.getString("userId"));
        if (user == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }

        LocalDate today = LocalDate.now(ZONE_ID);
        long startTime = today.atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        long endTime = today.plusDays(1).atStartOfDay(ZONE_ID).toInstant().toEpochMilli() - 1;
        LocalDate yesterday = today.minusDays(1);
        long yesterdayStartTime = yesterday.atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        long yesterdayEndTime = today.atStartOfDay(ZONE_ID).toInstant().toEpochMilli() - 1;
        boolean rootUser = isRoot(user);

        Map<String, Object> sale = querySaleAmount(startTime, endTime, rootUser, user.getId());
        Map<String, Object> yesterdaySale = querySaleAmount(
                yesterdayStartTime, yesterdayEndTime, rootUser, user.getId());
        long monthStartTime = today.withDayOfMonth(1).atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        long yearStartTime = today.withDayOfYear(1).atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        Map<String, Object> monthSale = querySaleAmount(
                monthStartTime, endTime, rootUser, user.getId());
        Map<String, Object> yearSale = querySaleAmount(
                yearStartTime, endTime, rootUser, user.getId());

        Map<String, Object> purchase = queryPurchaseAmount(
                startTime, endTime, rootUser, user.getId());
        Map<String, Object> yesterdayPurchase = queryPurchaseAmount(
                yesterdayStartTime, yesterdayEndTime, rootUser, user.getId());

        double stockTotal = queryCurrentStockTotal();

        JSONObject result = new JSONObject();
        result.put("salesAmount", numberValue(sale.get("salesAmount")));
        result.put("salesAmountYesterday", numberValue(yesterdaySale.get("salesAmount")));
        result.put("salesAmountMonth", numberValue(monthSale.get("salesAmount")));
        result.put("salesAmountYear", numberValue(yearSale.get("salesAmount")));
        result.put("purchaseAmount", numberValue(purchase.get("purchaseAmount")));
        result.put("purchaseAmountYesterday", numberValue(yesterdayPurchase.get("purchaseAmount")));
        result.put("stockTotal", stockTotal);
        if (rootUser) {
            result.put("profitAmount", numberValue(sale.get("profitAmount")));
            result.put("profitAmountYesterday", numberValue(yesterdaySale.get("profitAmount")));
        }
        result.put("date", today.format(DATE_FORMATTER));
        return Result.ok(result);
    }

    @PostMapping("asset")
    public Result<?> asset(@RequestBody JSONObject param) {
        AppUser user = userService.getById(param.getString("userId"));
        if (user == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }
        if (!isRoot(user)) {
            throw new AppRunTimeException("无权限查看资产统计");
        }

        double accountBalance = queryAssetAmount(
                "SELECT COALESCE(SUM(COALESCE(cur_prc, 0)), 0) AS amount " +
                        "FROM app_account_settle WHERE COALESCE(is_del, 0) = 0");
        double inventoryAmount = queryAssetAmount(
                "SELECT COALESCE(SUM(COALESCE(stock_cost, 0)), 0) AS amount " +
                        "FROM app_goods WHERE COALESCE(is_del, 0) = 0");
        double receivableAmount = queryAssetAmount(
                "SELECT COALESCE(SUM(COALESCE(payable, 0)), 0) AS amount " +
                        "FROM app_customer WHERE COALESCE(is_del, 0) = 0");
        double payableAmount = queryAssetAmount(
                "SELECT COALESCE(SUM(COALESCE(payable, 0)), 0) AS amount " +
                        "FROM app_supplier WHERE COALESCE(is_del, 0) = 0");

        JSONObject result = new JSONObject();
        result.put("accountBalance", accountBalance);
        result.put("inventoryAmount", inventoryAmount);
        result.put("receivableAmount", receivableAmount);
        result.put("payableAmount", payableAmount);
        result.put("totalAssets",
                accountBalance + inventoryAmount + receivableAmount - payableAmount);
        return Result.ok(result);
    }

    private Map<String, Object> querySaleAmount(
            long startTime, long endTime, boolean rootUser, Integer userId) {
        List<Object> params = summaryDateParams(startTime, endTime, rootUser ? null : userId);
        return jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(sales_amount), 0) AS salesAmount, " +
                        "COALESCE(SUM(sales_profit), 0) AS profitAmount " +
                        "FROM app_business_daily_summary " +
                        "WHERE business_date >= ? AND business_date <= ?" +
                        summaryCashierCondition(rootUser),
                params.toArray());
    }

    private Map<String, Object> queryPurchaseAmount(
            long startTime, long endTime, boolean rootUser, Integer userId) {
        List<Object> params = summaryDateParams(startTime, endTime, rootUser ? null : userId);
        return jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(purchase_amount), 0) AS purchaseAmount " +
                        "FROM app_business_daily_summary " +
                        "WHERE business_date >= ? AND business_date <= ?" +
                        summaryCashierCondition(rootUser),
                params.toArray());
    }

    private double queryAssetAmount(String sql) {
        Map<String, Object> row = jdbcTemplate.queryForMap(sql);
        return numberValue(row.get("amount"));
    }

    private double queryCurrentStockTotal() {
        Map<String, Object> stock = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(COALESCE(stock, 0)), 0) AS stockTotal " +
                        "FROM app_goods WHERE COALESCE(is_del, 0) = 0");
        return numberValue(stock.get("stockTotal"));
    }

    private List<Object> dateParams(long startTime, long endTime, Integer cashierId) {
        List<Object> params = new ArrayList<>();
        params.add(startTime);
        params.add(endTime);
        if (cashierId != null) {
            params.add(cashierId);
        }
        return params;
    }

    private List<Object> summaryDateParams(long startTime, long endTime, Integer cashierId) {
        LocalDate startDate = java.time.Instant.ofEpochMilli(startTime).atZone(ZONE_ID).toLocalDate();
        LocalDate endDate = java.time.Instant.ofEpochMilli(endTime).atZone(ZONE_ID).toLocalDate();
        List<Object> params = new ArrayList<>();
        params.add(startDate.format(DATE_FORMATTER));
        params.add(endDate.format(DATE_FORMATTER));
        if (cashierId != null) {
            params.add(String.valueOf(cashierId));
        }
        return params;
    }

    private String cashierCondition(boolean rootUser) {
        return rootUser ? "" : " AND o.cashier_id = ?";
    }

    private String summaryCashierCondition(boolean rootUser) {
        return rootUser ? "" : " AND cashier_id = ?";
    }

    private String saleTimeSql() {
        return databaseDialect.epochMillis("o.create_time");
    }

    private String purchaseTimeSql() {
        return databaseDialect.epochMillis("o.create_time");
    }

    private boolean isRoot(AppUser user) {
        return user.getIsRoot() != null && user.getIsRoot() == 1;
    }

    private double numberValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }
}
