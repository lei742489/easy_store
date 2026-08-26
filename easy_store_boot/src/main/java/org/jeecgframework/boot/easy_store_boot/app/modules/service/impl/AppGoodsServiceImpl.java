package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.CommonConstant;
import org.jeecgframework.boot.easy_store_boot.app.common.CommonUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.DatabaseDialect;
import org.jeecgframework.boot.easy_store_boot.app.common.DoubleUtil;
import org.jeecgframework.boot.easy_store_boot.app.common.PinyinUtil;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.ApiQuery;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.GoodsSearchResult;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppPurchaseOrderItemMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppSaleOrderItemMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.*;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppGoodsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
* @author Administrator
* @description 针对表【app_goods】的数据库操作Service实现
* @createDate 2025-06-27 10:35:51
*/
@Service
public class AppGoodsServiceImpl extends ServiceImpl<AppGoodsMapper, AppGoods>
    implements IAppGoodsService {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long STOCK_STATISTICS_CACHE_TTL_MILLIS = 30_000L;
    @Autowired
    private IAppUnitService appUnitService;
    @Autowired
    private AppGoodsMapper mapper;
    @Autowired
    private AppPurchaseOrderItemMapper purchaseOrderItemMapper;
    @Autowired
    @Lazy
    public IAppPurchaseOrderItemService purchaseOrderItemService;
    @Autowired
    @Lazy
    public IAppSaleOrderItemService saleOrderItemService;
    @Autowired
    @Lazy
    public IAppStockCheckItemService stockCheckItemService;
    @Autowired
    @Lazy
    public IAppStockLedgerService stockLedgerService;
    @Autowired
    private AppSaleOrderItemMapper saleOrderItemMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DatabaseDialect databaseDialect;
    private final Map<String, StockStatisticsCache> stockStatisticsCache = new ConcurrentHashMap<>();
    private final Object stockStatisticsCacheLock = new Object();

   @Override
    public boolean save(AppGoods entity){
       entity.setUnit(appUnitService.normalizeName(entity.getUnit()));
       appUnitService.updateByName(entity.getUnit());
       entity.setInitStock(entity.getStock());
       initCostFields(entity);
       boolean flag = super.save(entity);
       if(entity.getInitStock()!=null)
           purchaseOrderItemService.insertInitStore(entity.getId().toString(),entity.getInitStock());
       clearStockStatisticsCache();
      return flag;
   }

    @Override
    public boolean updateById(AppGoods entity){
        entity.setUnit(appUnitService.normalizeName(entity.getUnit()));
        appUnitService.updateByName(entity.getUnit());
        boolean updated = super.updateById(entity);
        clearStockStatisticsCache();
        return updated;
    }

    @Override
    public List<GoodsSearchResult> searchByKey(Integer pageNo, String key) {
        return searchByKey(pageNo, key, null);
    }

    @Override
    public List<GoodsSearchResult> searchByKey(Integer pageNo, String key, Boolean hideZeroStock) {
        return searchByKey(pageNo, key, hideZeroStock, null);
    }

    @Override
    public List<GoodsSearchResult> searchByKey(Integer pageNo, String key,
                                                Boolean hideZeroStock, String searchType) {
       if(pageNo ==null) pageNo = 1;
       /* if(StringUtils.isEmpty(key))
             return new ArrayList<>();*/

        if(key == null) key = "";
        String pyCode = null;
        if(key.matches("^[a-zA-Z0-9]+$"))
            pyCode = key;

        return mapper.searchByKey(key, pyCode, pageNo,
                CommonConstant.AUTO_COMPLETE_MAX_SEARCH_COUNT, hideZeroStock, searchType);
    }



    @Override
    public IPage<AppGoods> search(ApiQuery query) {

        if(StringUtils.isNotEmpty(query.getColumn()))
            query.setColumn(CommonUtils.camelToUnderline(query.getColumn()));
        if(StringUtils.isNotEmpty(query.getKey()) && query.getKey().matches("^[a-zA-Z0-9]+$"))
            query.setPyCode(query.getKey());

        return mapper.search(new Page<AppGoods>(query.getPageNo(), query.getPageSize()),query);
    }

    @Override
    @Async("myExecutor")
    public void setDefCategoryId(String categoryId) {
        LambdaUpdateWrapper<AppGoods> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AppGoods::getCategoryId, categoryId);
        updateWrapper.set(AppGoods::getCategoryId,null);
        update(updateWrapper);
    }

    @Override
    public void updateStock(String goodsId) {
        if(StringUtils.isBlank(goodsId) || !StringUtils.isNumeric(goodsId.trim())) {
            return;
        }
        goodsId = goodsId.trim();
        clearStockStatisticsCache();
        stockLedgerService.rebuildGoodsLedger(goodsId);
    }

    @Override
    public String getTitleById(String goodsId) {
       if(StringUtils.isBlank(goodsId)) return "";
       if(!StringUtils.isNumeric(goodsId.trim())) return goodsId;
       AppGoods appGoods = getById(goodsId.trim());
        return appGoods!=null ? appGoods.getTitle() : "";
    }

    @Override
    public JSONObject getStockDetail(String goodsId, Long startTime, Long endTime) {
        AppGoods goods = getById(goodsId);
        if (goods == null) {
            return null;
        }

        BigDecimal openingQty = BigDecimal.ZERO;
        BigDecimal openingCost = BigDecimal.ZERO;
        BigDecimal openingPrice = BigDecimal.ZERO;
        BigDecimal inQtyTotal = BigDecimal.ZERO;
        BigDecimal inTotal = BigDecimal.ZERO;
        BigDecimal outQtyTotal = BigDecimal.ZERO;
        BigDecimal outTotal = BigDecimal.ZERO;
        BigDecimal endingQty = BigDecimal.ZERO;
        BigDecimal endingCost = BigDecimal.ZERO;
        BigDecimal endingPrice = BigDecimal.ZERO;
        List<JSONObject> records = new ArrayList<>();

        List<Map<String, Object>> ledgerRows = queryStockLedgerRows(goodsId, endTime);
        int rowNo = 1;
        for (Map<String, Object> ledger : ledgerRows) {
            if (isZeroInitLedger(ledger)) {
                continue;
            }
            long businessTime = timeValue(ledger.get("business_time"));
            if (startTime != null && businessTime < startTime) {
                openingQty = decimal(ledger.get("after_qty"));
                openingCost = decimal(ledger.get("after_amount"));
                openingPrice = decimal(ledger.get("after_cost_price"));
                endingQty = openingQty;
                endingCost = openingCost;
                endingPrice = openingPrice;
                continue;
            }
            if (endTime != null && businessTime > endTime) {
                break;
            }

            BigDecimal inQty = decimal(ledger.get("in_qty"));
            BigDecimal inPrice = decimal(ledger.get("in_price"));
            BigDecimal inAmount = decimal(ledger.get("in_amount"));
            BigDecimal outQty = decimal(ledger.get("out_qty"));
            BigDecimal outPrice = decimal(ledger.get("cost_price"));
            BigDecimal outAmount = decimal(ledger.get("cost_amount"));

            inQtyTotal = inQtyTotal.add(inQty);
            inTotal = inTotal.add(inAmount);
            outQtyTotal = outQtyTotal.add(outQty);
            outTotal = outTotal.add(outAmount);
            endingQty = decimal(ledger.get("after_qty"));
            endingCost = decimal(ledger.get("after_amount"));
            endingPrice = decimal(ledger.get("after_cost_price"));

            JSONObject row = new JSONObject();
            row.put("rowNo", rowNo++);
            row.put("businessType", ledgerBusinessType(ledger.get("business_type")));
            row.put("businessDate", formatDate(businessTime));
            row.put("counterpartyName", ledgerCounterpartyName(ledger));
            row.put("inQty", number(inQty, 4));
            row.put("inUnitPrice", number(inPrice, 4));
            row.put("inAmount", number(inAmount, 2));
            row.put("outQty", number(outQty, 4));
            row.put("outCostPrice", number(outPrice, 4));
            row.put("outAmount", number(outAmount, 2));
            row.put("endingQty", number(endingQty, 4));
            row.put("endingCostPrice", number(endingPrice, 4));
            row.put("endingAmount", number(endingCost, 2));
            records.add(row);
        }

        JSONObject result = new JSONObject();
        result.put("goodsId", goods.getId());
        result.put("goodsName", goods.getTitle());
        result.put("unit", goods.getUnit());
        result.put("openingQty", number(openingQty, 4));
        result.put("openingCostPrice", number(openingPrice, 4));
        result.put("openingAmount", number(openingCost, 2));
        result.put("inQtyTotal", number(inQtyTotal, 4));
        result.put("inTotal", number(inTotal, 2));
        result.put("outQtyTotal", number(outQtyTotal, 4));
        result.put("outTotal", number(outTotal, 2));
        result.put("endingQty", number(endingQty, 4));
        result.put("endingCostPrice", number(endingPrice, 4));
        result.put("endingAmount", number(endingCost, 2));
        result.put("records", records);
        return result;
    }

    private boolean isZeroInitLedger(Map<String, Object> ledger) {
        return "INIT".equals(stringValue(ledger.get("business_type")))
                && decimal(ledger.get("in_qty")).compareTo(BigDecimal.ZERO) == 0
                && decimal(ledger.get("in_amount")).compareTo(BigDecimal.ZERO) == 0
                && decimal(ledger.get("out_qty")).compareTo(BigDecimal.ZERO) == 0
                && decimal(ledger.get("cost_amount")).compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public JSONObject getStockStatistics(String categoryId, String key, Long startTime, Long endTime,
                                         Integer current, Integer pageSize) {
        int currentPage = current == null || current < 1 ? 1 : current;
        int size = pageSize == null || pageSize < 1 ? 50 : pageSize;
        if (databaseDialect.isMySql()) {
            return getStockStatisticsMySql(categoryId, key, startTime, endTime, currentPage, size);
        }
        StockStatisticsCache cache = getStockStatisticsCache(categoryId, key, startTime, endTime);
        int total = cache.records.size();
        int fromIndex = Math.min((currentPage - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        JSONArray records = new JSONArray();
        for (int index = fromIndex; index < toIndex; index++) {
            records.add(cache.records.get(index).toJson(index + 1));
        }

        JSONObject result = new JSONObject();
        result.put("current", currentPage);
        result.put("pageSize", size);
        result.put("total", total);
        result.put("pages", (int) Math.ceil(total * 1D / size));
        result.put("openingQtyTotal", number(cache.openingQtyTotal, 4));
        result.put("openingAmountTotal", number(cache.openingAmountTotal, 2));
        result.put("inQtyTotal", number(cache.inQtyTotal, 4));
        result.put("inAmountTotal", number(cache.inAmountTotal, 2));
        result.put("outQtyTotal", number(cache.outQtyTotal, 4));
        result.put("outAmountTotal", number(cache.outAmountTotal, 2));
        result.put("endingQtyTotal", number(cache.endingQtyTotal, 4));
        result.put("endingAmountTotal", number(cache.endingAmountTotal, 2));
        result.put("records", records);
        return result;
    }

    private StockStatisticsCache getStockStatisticsCache(String categoryId, String key,
                                                         Long startTime, Long endTime) {
        String cacheKey = String.valueOf(categoryId) + "|" + String.valueOf(key) + "|"
                + String.valueOf(startTime) + "|" + String.valueOf(endTime);
        long now = System.currentTimeMillis();
        StockStatisticsCache cache = stockStatisticsCache.get(cacheKey);
        if (cache != null && now - cache.createdAt < STOCK_STATISTICS_CACHE_TTL_MILLIS) {
            return cache;
        }
        synchronized (stockStatisticsCacheLock) {
            cache = stockStatisticsCache.get(cacheKey);
            if (cache == null || now - cache.createdAt >= STOCK_STATISTICS_CACHE_TTL_MILLIS) {
                cache = calculateStockStatistics(categoryId, key, startTime, endTime, now);
                if (stockStatisticsCache.size() > 20) {
                    stockStatisticsCache.clear();
                }
                stockStatisticsCache.put(cacheKey, cache);
            }
        }
        return cache;
    }

    private StockStatisticsCache calculateStockStatistics(String categoryId, String key,
                                                          Long startTime, Long endTime, long createdAt) {
        LambdaQueryWrapper<AppGoods> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotEmpty(categoryId) && !"0".equals(categoryId)) {
            wrapper.eq(AppGoods::getCategoryId, categoryId);
        }
        if (StringUtils.isNotEmpty(key)) {
            wrapper.and(item -> item.like(AppGoods::getTitle, key)
                    .or().like(AppGoods::getGoodsCode, key)
                    .or().like(AppGoods::getPyCode, key));
        }
        wrapper.orderByAsc(AppGoods::getTitle).orderByAsc(AppGoods::getId);
        List<AppGoods> goodsList = list(wrapper);
        Map<String, List<Map<String, Object>>> ledgerMap =
                queryStockLedgerStatisticRows(categoryId, key, endTime);

        BigDecimal openingQtyTotal = BigDecimal.ZERO;
        BigDecimal openingAmountTotal = BigDecimal.ZERO;
        BigDecimal inQtyTotal = BigDecimal.ZERO;
        BigDecimal inAmountTotal = BigDecimal.ZERO;
        BigDecimal outQtyTotal = BigDecimal.ZERO;
        BigDecimal outAmountTotal = BigDecimal.ZERO;
        BigDecimal endingQtyTotal = BigDecimal.ZERO;
        BigDecimal endingAmountTotal = BigDecimal.ZERO;

        List<StockStatisticsRow> records = new ArrayList<>();
        for (AppGoods goods : goodsList) {
            List<Map<String, Object>> ledgerRows =
                    ledgerMap.getOrDefault(String.valueOf(goods.getId()), Collections.emptyList());
            if (ledgerRows.isEmpty()) {
                continue;
            }
            StockSummary detail = calculateLedgerStockSummary(ledgerRows, startTime, endTime);
            if (detail.isEmpty()) {
                continue;
            }
            BigDecimal openingQty = detail.openingQty;
            BigDecimal openingAmount = detail.openingAmount;
            BigDecimal inQty = detail.inQty;
            BigDecimal inAmount = detail.inAmount;
            BigDecimal outQty = detail.outQty;
            BigDecimal outAmount = detail.outAmount;
            BigDecimal endingQty = detail.endingQty;
            BigDecimal endingAmount = detail.endingAmount;
            openingQtyTotal = openingQtyTotal.add(openingQty);
            openingAmountTotal = openingAmountTotal.add(openingAmount);
            inQtyTotal = inQtyTotal.add(inQty);
            inAmountTotal = inAmountTotal.add(inAmount);
            outQtyTotal = outQtyTotal.add(outQty);
            outAmountTotal = outAmountTotal.add(outAmount);
            endingQtyTotal = endingQtyTotal.add(endingQty);
            endingAmountTotal = endingAmountTotal.add(endingAmount);
            records.add(new StockStatisticsRow(goods, detail));
        }

        return new StockStatisticsCache(createdAt, records, openingQtyTotal, openingAmountTotal,
                inQtyTotal, inAmountTotal, outQtyTotal, outAmountTotal,
                endingQtyTotal, endingAmountTotal);
    }

    private StockSummary calculateLedgerStockSummary(List<Map<String, Object>> ledgerRows,
                                                     Long startTime, Long endTime) {
        ledgerRows.sort(Comparator
                .comparingLong((Map<String, Object> row) -> timeValue(row.get("business_time")))
                .thenComparing(row -> integer(row.get("seq_no")), Comparator.nullsLast(Integer::compareTo))
                .thenComparing(row -> integer(row.get("id")), Comparator.nullsLast(Integer::compareTo)));
        BigDecimal openingQty = BigDecimal.ZERO;
        BigDecimal openingAmount = BigDecimal.ZERO;
        BigDecimal endingQty = BigDecimal.ZERO;
        BigDecimal endingAmount = BigDecimal.ZERO;
        BigDecimal inQtyTotal = BigDecimal.ZERO;
        BigDecimal inTotal = BigDecimal.ZERO;
        BigDecimal outQtyTotal = BigDecimal.ZERO;
        BigDecimal outTotal = BigDecimal.ZERO;

        for (Map<String, Object> ledger : ledgerRows) {
            long businessTime = timeValue(ledger.get("business_time"));
            if (startTime != null && businessTime < startTime) {
                openingQty = decimal(ledger.get("after_qty"));
                openingAmount = decimal(ledger.get("after_amount"));
                endingQty = openingQty;
                endingAmount = openingAmount;
                continue;
            }
            if (endTime != null && businessTime > endTime) {
                break;
            }
            BigDecimal inQty = decimal(ledger.get("in_qty"));
            BigDecimal inAmount = decimal(ledger.get("in_amount"));
            BigDecimal outQty = decimal(ledger.get("out_qty"));
            BigDecimal outAmount = decimal(ledger.get("cost_amount"));
            inQtyTotal = inQtyTotal.add(inQty);
            inTotal = inTotal.add(inAmount);
            outQtyTotal = outQtyTotal.add(outQty);
            outTotal = outTotal.add(outAmount);
            endingQty = decimal(ledger.get("after_qty"));
            endingAmount = decimal(ledger.get("after_amount"));
        }

        return new StockSummary(openingQty, openingAmount, inQtyTotal, inTotal,
                outQtyTotal, outTotal, endingQty, endingAmount);
    }

    private List<Map<String, Object>> queryStockLedgerRows(String goodsId, Long endTime) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT l.*, ")
                .append("CASE ")
                .append("WHEN l.source_table = 'app_purchase_order' THEN ")
                .append("(SELECT COALESCE(s.name, '') FROM app_purchase_order o ")
                .append("LEFT JOIN app_supplier s ON s.id = o.supplier_id AND COALESCE(s.is_del, 0) = 0 ")
                .append("WHERE o.id = l.source_id LIMIT 1) ")
                .append("WHEN l.source_table = 'app_sale_order' THEN ")
                .append("(SELECT COALESCE(c.name, '') FROM app_sale_order o ")
                .append("LEFT JOIN app_customer c ON c.id = o.customer_id AND COALESCE(c.is_del, 0) = 0 ")
                .append("WHERE o.id = l.source_id LIMIT 1) ")
                .append("ELSE '' END AS counterparty_name ")
                .append("FROM app_stock_ledger l ")
                .append("WHERE ").append(notDeleted("l")).append(" AND l.goods_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(goodsId);
        if (endTime != null) {
            sql.append("AND ").append(buildBusinessTimeCompare("l.business_time", "<=")).append(" ");
            params.add(endTime);
        }
        sql.append("ORDER BY l.business_time, l.seq_no, l.id");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    private Map<String, List<Map<String, Object>>> queryStockLedgerStatisticRows(String categoryId,
                                                                                 String key,
                                                                                 Long endTime) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT l.id, l.goods_id, l.seq_no, l.business_time, ")
                .append("l.in_qty, l.in_amount, l.out_qty, l.cost_amount, ")
                .append("l.after_qty, l.after_amount ")
                .append("FROM app_stock_ledger l ")
                .append("INNER JOIN app_goods g ON g.id = l.goods_id AND ").append(notDeleted("g")).append(" ")
                .append("WHERE ").append(notDeleted("l")).append(" ");
        List<Object> params = new ArrayList<>();
        if (StringUtils.isNotEmpty(categoryId) && !"0".equals(categoryId)) {
            sql.append("AND g.category_id = ? ");
            params.add(categoryId);
        }
        if (StringUtils.isNotEmpty(key)) {
            sql.append("AND (g.title LIKE ? OR g.goods_code LIKE ? OR g.py_code LIKE ?) ");
            String likeKey = "%" + key + "%";
            params.add(likeKey);
            params.add(likeKey);
            params.add(likeKey);
        }
        if (endTime != null) {
            sql.append("AND ").append(buildBusinessTimeCompare("l.business_time", "<=")).append(" ");
            params.add(endTime);
        }
        sql.append("ORDER BY l.goods_id, l.business_time, l.seq_no, l.id");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String goodsId = stringValue(row.get("goods_id"));
            if (StringUtils.isBlank(goodsId)) {
                continue;
            }
            result.computeIfAbsent(goodsId, item -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private JSONObject getStockStatisticsMySql(String categoryId, String key, Long startTime, Long endTime,
                                               int currentPage, int size) {
        StockStatisticsSqlParts parts = canUseCurrentStockSnapshot(endTime)
                ? buildCurrentStockStatisticsSql(categoryId, key, startTime, endTime)
                : buildLedgerStockStatisticsSql(categoryId, key, startTime, endTime);

        Map<String, Object> totals = jdbcTemplate.queryForMap(parts.cteSql +
                " SELECT COUNT(*) AS total, " +
                "COALESCE(SUM(opening_qty), 0) AS opening_qty_total, " +
                "COALESCE(SUM(opening_amount), 0) AS opening_amount_total, " +
                "COALESCE(SUM(in_qty), 0) AS in_qty_total, " +
                "COALESCE(SUM(in_amount), 0) AS in_amount_total, " +
                "COALESCE(SUM(out_qty), 0) AS out_qty_total, " +
                "COALESCE(SUM(out_amount), 0) AS out_amount_total, " +
                "COALESCE(SUM(ending_qty), 0) AS ending_qty_total, " +
                "COALESCE(SUM(ending_amount), 0) AS ending_amount_total " +
                "FROM rows_filtered", parts.params.toArray());
        int total = integer(totals.get("total")) == null ? 0 : integer(totals.get("total"));

        List<Object> pageParams = new ArrayList<>(parts.params);
        pageParams.add(size);
        pageParams.add((currentPage - 1) * size);
        List<Map<String, Object>> pageRows = jdbcTemplate.queryForList(parts.cteSql +
                " SELECT goods_id, goods_name, unit, opening_qty, opening_amount, " +
                "in_qty, in_amount, out_qty, out_amount, ending_qty, ending_amount " +
                "FROM rows_filtered ORDER BY goods_name, goods_id LIMIT ? OFFSET ?", pageParams.toArray());

        JSONArray records = new JSONArray();
        int rowNo = (currentPage - 1) * size + 1;
        for (Map<String, Object> row : pageRows) {
            JSONObject item = new JSONObject();
            item.put("rowNo", rowNo++);
            item.put("goodsId", row.get("goods_id"));
            item.put("goodsName", stringValue(row.get("goods_name")));
            item.put("unit", stringValue(row.get("unit")));
            item.put("openingQty", number(decimal(row.get("opening_qty")), 4));
            item.put("openingAmount", number(decimal(row.get("opening_amount")), 2));
            item.put("inQty", number(decimal(row.get("in_qty")), 4));
            item.put("inAmount", number(decimal(row.get("in_amount")), 2));
            item.put("outQty", number(decimal(row.get("out_qty")), 4));
            item.put("outAmount", number(decimal(row.get("out_amount")), 2));
            item.put("endingQty", number(decimal(row.get("ending_qty")), 4));
            item.put("endingAmount", number(decimal(row.get("ending_amount")), 2));
            records.add(item);
        }

        JSONObject result = new JSONObject();
        result.put("current", currentPage);
        result.put("pageSize", size);
        result.put("total", total);
        result.put("pages", (int) Math.ceil(total * 1D / size));
        result.put("openingQtyTotal", number(decimal(totals.get("opening_qty_total")), 4));
        result.put("openingAmountTotal", number(decimal(totals.get("opening_amount_total")), 2));
        result.put("inQtyTotal", number(decimal(totals.get("in_qty_total")), 4));
        result.put("inAmountTotal", number(decimal(totals.get("in_amount_total")), 2));
        result.put("outQtyTotal", number(decimal(totals.get("out_qty_total")), 4));
        result.put("outAmountTotal", number(decimal(totals.get("out_amount_total")), 2));
        result.put("endingQtyTotal", number(decimal(totals.get("ending_qty_total")), 4));
        result.put("endingAmountTotal", number(decimal(totals.get("ending_amount_total")), 2));
        result.put("records", records);
        return result;
    }

    private StockStatisticsSqlParts buildCurrentStockStatisticsSql(String categoryId, String key,
                                                                   Long startTime, Long endTime) {
        StringBuilder goodsWhere = new StringBuilder(" WHERE g.is_del = 0 ");
        List<Object> goodsParams = new ArrayList<>();
        appendGoodsFilter(goodsWhere, goodsParams, categoryId, key);

        StringBuilder periodWhere = new StringBuilder(" WHERE l.is_del = 0 ");
        List<Object> params = new ArrayList<>();
        appendBusinessTimeRange(periodWhere, params, "l.business_time", startTime, endTime);
        params.addAll(goodsParams);

        String cteSql = "WITH period_sum AS (" +
                " SELECT l.goods_id, " +
                "COALESCE(SUM(l.in_qty), 0) AS in_qty, " +
                "COALESCE(SUM(l.in_amount), 0) AS in_amount, " +
                "COALESCE(SUM(l.out_qty), 0) AS out_qty, " +
                "COALESCE(SUM(l.cost_amount), 0) AS out_amount " +
                "FROM app_stock_ledger l " + periodWhere +
                " GROUP BY l.goods_id" +
                "), rows_base AS (" +
                " SELECT g.id AS goods_id, g.title AS goods_name, g.unit AS unit, " +
                "CAST(COALESCE(NULLIF(g.stock, ''), '0') AS DECIMAL(18,4)) - COALESCE(p.in_qty, 0) + COALESCE(p.out_qty, 0) AS opening_qty, " +
                "COALESCE(g.stock_cost, 0) - COALESCE(p.in_amount, 0) + COALESCE(p.out_amount, 0) AS opening_amount, " +
                "COALESCE(p.in_qty, 0) AS in_qty, COALESCE(p.in_amount, 0) AS in_amount, " +
                "COALESCE(p.out_qty, 0) AS out_qty, COALESCE(p.out_amount, 0) AS out_amount, " +
                "CAST(COALESCE(NULLIF(g.stock, ''), '0') AS DECIMAL(18,4)) AS ending_qty, " +
                "COALESCE(g.stock_cost, 0) AS ending_amount " +
                "FROM app_goods g LEFT JOIN period_sum p ON p.goods_id = CAST(g.id AS CHAR) " +
                goodsWhere +
                "), rows_filtered AS (" +
                " SELECT * FROM rows_base WHERE opening_qty <> 0 OR opening_amount <> 0 " +
                "OR in_qty <> 0 OR in_amount <> 0 OR out_qty <> 0 OR out_amount <> 0 " +
                "OR ending_qty <> 0 OR ending_amount <> 0" +
                ")";
        return new StockStatisticsSqlParts(cteSql, params);
    }

    private StockStatisticsSqlParts buildLedgerStockStatisticsSql(String categoryId, String key,
                                                                  Long startTime, Long endTime) {
        StringBuilder goodsWhere = new StringBuilder(" WHERE g.is_del = 0 ");
        List<Object> params = new ArrayList<>();
        appendGoodsFilter(goodsWhere, params, categoryId, key);

        Long openingEndTime = startTime == null ? 0L : startTime - 1;
        Long endingEndTime = endTime == null ? Long.MAX_VALUE : endTime;

        String cteSql = "WITH goods_filter AS (" +
                " SELECT g.id, g.title, g.unit FROM app_goods g " + goodsWhere +
                "), opening_ranked AS (" +
                " SELECT l.goods_id, l.after_qty, l.after_amount, " +
                "ROW_NUMBER() OVER (PARTITION BY l.goods_id ORDER BY l.business_time DESC, l.seq_no DESC, l.id DESC) AS rn " +
                "FROM app_stock_ledger l INNER JOIN goods_filter g ON l.goods_id = CAST(g.id AS CHAR) " +
                "WHERE l.is_del = 0 AND l.business_time <= FROM_UNIXTIME(? / 1000)" +
                "), ending_ranked AS (" +
                " SELECT l.goods_id, l.after_qty, l.after_amount, " +
                "ROW_NUMBER() OVER (PARTITION BY l.goods_id ORDER BY l.business_time DESC, l.seq_no DESC, l.id DESC) AS rn " +
                "FROM app_stock_ledger l INNER JOIN goods_filter g ON l.goods_id = CAST(g.id AS CHAR) " +
                "WHERE l.is_del = 0 AND l.business_time <= FROM_UNIXTIME(? / 1000)" +
                "), period_sum AS (" +
                " SELECT l.goods_id, COALESCE(SUM(l.in_qty), 0) AS in_qty, " +
                "COALESCE(SUM(l.in_amount), 0) AS in_amount, COALESCE(SUM(l.out_qty), 0) AS out_qty, " +
                "COALESCE(SUM(l.cost_amount), 0) AS out_amount " +
                "FROM app_stock_ledger l INNER JOIN goods_filter g ON l.goods_id = CAST(g.id AS CHAR) " +
                "WHERE l.is_del = 0";
        params.add(openingEndTime);
        params.add(endingEndTime);
        StringBuilder periodRange = new StringBuilder();
        appendBusinessTimeRange(periodRange, params, "l.business_time", startTime, endTime);
        cteSql += periodRange +
                " GROUP BY l.goods_id" +
                "), rows_base AS (" +
                " SELECT g.id AS goods_id, g.title AS goods_name, g.unit AS unit, " +
                "COALESCE(o.after_qty, 0) AS opening_qty, COALESCE(o.after_amount, 0) AS opening_amount, " +
                "COALESCE(p.in_qty, 0) AS in_qty, COALESCE(p.in_amount, 0) AS in_amount, " +
                "COALESCE(p.out_qty, 0) AS out_qty, COALESCE(p.out_amount, 0) AS out_amount, " +
                "COALESCE(e.after_qty, o.after_qty, 0) AS ending_qty, " +
                "COALESCE(e.after_amount, o.after_amount, 0) AS ending_amount " +
                "FROM goods_filter g " +
                "LEFT JOIN opening_ranked o ON o.goods_id = CAST(g.id AS CHAR) AND o.rn = 1 " +
                "LEFT JOIN ending_ranked e ON e.goods_id = CAST(g.id AS CHAR) AND e.rn = 1 " +
                "LEFT JOIN period_sum p ON p.goods_id = CAST(g.id AS CHAR)" +
                "), rows_filtered AS (" +
                " SELECT * FROM rows_base WHERE opening_qty <> 0 OR opening_amount <> 0 " +
                "OR in_qty <> 0 OR in_amount <> 0 OR out_qty <> 0 OR out_amount <> 0 " +
                "OR ending_qty <> 0 OR ending_amount <> 0" +
                ")";
        return new StockStatisticsSqlParts(cteSql, params);
    }

    private void appendGoodsFilter(StringBuilder sql, List<Object> params, String categoryId, String key) {
        if (StringUtils.isNotEmpty(categoryId) && !"0".equals(categoryId)) {
            sql.append("AND g.category_id = ? ");
            params.add(categoryId);
        }
        if (StringUtils.isNotEmpty(key)) {
            sql.append("AND (g.title LIKE ? OR g.goods_code LIKE ? OR g.py_code LIKE ?) ");
            String likeKey = "%" + key + "%";
            params.add(likeKey);
            params.add(likeKey);
            params.add(likeKey);
        }
    }

    private String ledgerCounterpartyName(Map<String, Object> ledger) {
        String counterparty = stringValue(ledger.get("counterparty_name"));
        if (StringUtils.isNotBlank(counterparty)) {
            return counterparty;
        }
        return stringValue(ledger.get("business_no"));
    }

    private String ledgerBusinessType(Object value) {
        String type = stringValue(value);
        switch (type) {
            case "INIT":
                return "期初";
            case "PURCHASE_IN":
                return "进货入库";
            case "PURCHASE_RETURN":
                return "采购退货";
            case "SALE_OUT":
                return "销售出库";
            case "SALE_RETURN":
                return "销售退货";
            case "STOCK_CHECK_IN":
                return "库存盘盈";
            case "STOCK_CHECK_OUT":
                return "库存盘亏";
            default:
                return type;
        }
    }

    private boolean canUseCurrentStockSnapshot(Long endTime) {
        return endTime == null || endTime >= System.currentTimeMillis();
    }

    private StockStatisticsCache calculateCurrentStockStatistics(List<AppGoods> goodsList,
                                                                 String categoryId,
                                                                 String key,
                                                                 Long startTime,
                                                                 Long endTime,
                                                                 long createdAt) {
        Map<String, StockPeriodAggregate> periodMap =
                queryCurrentStockPeriodAggregates(categoryId, key, startTime, endTime);
        BigDecimal openingQtyTotal = BigDecimal.ZERO;
        BigDecimal openingAmountTotal = BigDecimal.ZERO;
        BigDecimal inQtyTotal = BigDecimal.ZERO;
        BigDecimal inAmountTotal = BigDecimal.ZERO;
        BigDecimal outQtyTotal = BigDecimal.ZERO;
        BigDecimal outAmountTotal = BigDecimal.ZERO;
        BigDecimal endingQtyTotal = BigDecimal.ZERO;
        BigDecimal endingAmountTotal = BigDecimal.ZERO;
        List<StockStatisticsRow> records = new ArrayList<>();

        for (AppGoods goods : goodsList) {
            StockPeriodAggregate aggregate = periodMap.getOrDefault(
                    String.valueOf(goods.getId()), StockPeriodAggregate.EMPTY);
            BigDecimal endingQty = decimal(goods.getStock());
            BigDecimal endingAmount = decimal(goods.getStockCost());
            BigDecimal openingQty = endingQty.subtract(aggregate.inQty).add(aggregate.outQty);
            BigDecimal openingAmount = endingAmount.subtract(aggregate.inAmount).add(aggregate.outAmount);
            StockSummary summary = new StockSummary(openingQty, openingAmount,
                    aggregate.inQty, aggregate.inAmount, aggregate.outQty, aggregate.outAmount,
                    endingQty, endingAmount);
            if (summary.isEmpty()) {
                continue;
            }

            openingQtyTotal = openingQtyTotal.add(openingQty);
            openingAmountTotal = openingAmountTotal.add(openingAmount);
            inQtyTotal = inQtyTotal.add(aggregate.inQty);
            inAmountTotal = inAmountTotal.add(aggregate.inAmount);
            outQtyTotal = outQtyTotal.add(aggregate.outQty);
            outAmountTotal = outAmountTotal.add(aggregate.outAmount);
            endingQtyTotal = endingQtyTotal.add(endingQty);
            endingAmountTotal = endingAmountTotal.add(endingAmount);
            records.add(new StockStatisticsRow(goods, summary));
        }

        return new StockStatisticsCache(createdAt, records, openingQtyTotal, openingAmountTotal,
                inQtyTotal, inAmountTotal, outQtyTotal, outAmountTotal,
                endingQtyTotal, endingAmountTotal);
    }

    private StockSummary calculateStockSummary(AppGoods goods, List<StockStatisticMovement> movements,
                                               Long startTime, Long endTime) {
        movements.sort(Comparator
                .comparingLong((StockStatisticMovement row) -> timeValue(row.billTime))
                .thenComparing(row -> row.itemId, Comparator.nullsLast(Integer::compareTo)));

        BigDecimal stockQty = BigDecimal.ZERO;
        BigDecimal stockCost = BigDecimal.ZERO;
        BigDecimal costPrice = BigDecimal.ZERO;
        BigDecimal openingQty = BigDecimal.ZERO;
        BigDecimal openingCost = BigDecimal.ZERO;
        BigDecimal openingPrice = BigDecimal.ZERO;
        boolean openingCaptured = startTime == null;
        BigDecimal inQtyTotal = BigDecimal.ZERO;
        BigDecimal inTotal = BigDecimal.ZERO;
        BigDecimal outQtyTotal = BigDecimal.ZERO;
        BigDecimal outTotal = BigDecimal.ZERO;

        for (StockStatisticMovement movement : movements) {
            BigDecimal quantity = movement.quantity;
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            long businessTime = timeValue(movement.billTime);
            if (startTime != null && businessTime < startTime) {
                StockBalance balance = applyStockStatisticMovement(goods, movement, stockQty, stockCost, costPrice);
                stockQty = balance.stockQty;
                stockCost = balance.stockCost;
                costPrice = balance.costPrice;
                continue;
            }
            if (!openingCaptured) {
                openingQty = stockQty;
                openingCost = stockCost;
                openingPrice = costPrice;
                openingCaptured = true;
            }
            if (endTime != null && businessTime > endTime) {
                break;
            }

            Integer billType = movement.billType;
            if (billType != null && billType == 3) {
                BigDecimal unitPrice = movement.unitPrice;
                if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                    inQtyTotal = inQtyTotal.add(quantity);
                    inTotal = inTotal.add(quantity.multiply(unitPrice));
                } else {
                    BigDecimal quantityAbs = quantity.abs();
                    outQtyTotal = outQtyTotal.add(quantityAbs);
                    outTotal = outTotal.add(quantityAbs.multiply(unitPrice));
                }
            } else if (billType != null && billType == 1) {
                BigDecimal unitPrice = resolvePurchaseUnitPrice(goods, movement, quantity);
                if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                    inQtyTotal = inQtyTotal.add(quantity);
                    inTotal = inTotal.add(quantity.multiply(unitPrice));
                } else {
                    BigDecimal quantityAbs = quantity.abs();
                    outQtyTotal = outQtyTotal.add(quantityAbs);
                    outTotal = outTotal.add(quantityAbs.multiply(unitPrice));
                }
            } else {
                BigDecimal quantityAbs = quantity.abs();
                if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                    outQtyTotal = outQtyTotal.add(quantityAbs);
                    outTotal = outTotal.add(quantityAbs.multiply(costPrice));
                } else {
                    inQtyTotal = inQtyTotal.add(quantityAbs);
                    inTotal = inTotal.add(quantityAbs.multiply(costPrice));
                }
            }

            StockBalance balance = applyStockStatisticMovement(goods, movement, stockQty, stockCost, costPrice);
            stockQty = balance.stockQty;
            stockCost = balance.stockCost;
            costPrice = balance.costPrice;
        }

        if (!openingCaptured) {
            openingQty = stockQty;
            openingCost = stockCost;
            openingPrice = costPrice;
        } else if (startTime == null) {
            openingQty = BigDecimal.ZERO;
            openingCost = BigDecimal.ZERO;
            openingPrice = BigDecimal.ZERO;
        }

        return new StockSummary(openingQty, openingCost, inQtyTotal, inTotal,
                outQtyTotal, outTotal, stockQty, stockCost);
    }

    private Map<String, StockPeriodAggregate> queryCurrentStockPeriodAggregates(String categoryId,
                                                                                String key,
                                                                                Long startTime,
                                                                                Long endTime) {
        String purchaseAmount = "CASE WHEN ABS(COALESCE(i.total_amount, 0)) > 0 " +
                "THEN COALESCE(i.total_amount, 0) " +
                "ELSE COALESCE(i.quantity, 0) * COALESCE(i.unit_price, 0) END";
        String stockCheckAmount = "CASE WHEN ABS(COALESCE(i.profit_loss_amount, 0)) > 0 " +
                "THEN COALESCE(i.profit_loss_amount, 0) " +
                "ELSE COALESCE(i.profit_loss_quantity, 0) * COALESCE(i.unit_price, 0) END";
        String goodsFilter = "";
        List<Object> filterParams = new ArrayList<>();
        if (StringUtils.isNotEmpty(categoryId) && !"0".equals(categoryId)) {
            goodsFilter += " AND g.category_id = ?";
            filterParams.add(categoryId);
        }
        if (StringUtils.isNotEmpty(key)) {
            goodsFilter += " AND (g.title LIKE ? OR g.goods_code LIKE ? OR g.py_code LIKE ?)";
            String likeKey = "%" + key + "%";
            filterParams.add(likeKey);
            filterParams.add(likeKey);
            filterParams.add(likeKey);
        }

        StringBuilder sql = new StringBuilder("SELECT goods_id, " +
                "COALESCE(SUM(in_qty), 0) AS in_qty, " +
                "COALESCE(SUM(in_amount), 0) AS in_amount, " +
                "COALESCE(SUM(out_qty), 0) AS out_qty, " +
                "COALESCE(SUM(out_amount), 0) AS out_amount FROM (");
        List<Object> params = new ArrayList<>();

        sql.append(" SELECT i.goods_id, ")
                .append("CASE WHEN COALESCE(i.quantity, 0) > 0 THEN COALESCE(i.quantity, 0) ELSE 0 END AS in_qty, ")
                .append("CASE WHEN COALESCE(i.quantity, 0) > 0 THEN ABS(").append(purchaseAmount).append(") ELSE 0 END AS in_amount, ")
                .append("CASE WHEN COALESCE(i.quantity, 0) < 0 THEN ABS(COALESCE(i.quantity, 0)) ELSE 0 END AS out_qty, ")
                .append("CASE WHEN COALESCE(i.quantity, 0) < 0 THEN ABS(").append(purchaseAmount).append(") ELSE 0 END AS out_amount ")
                .append("FROM app_purchase_order_item i ")
                .append("INNER JOIN app_goods g ON g.id = i.goods_id AND COALESCE(g.is_del, 0) = 0 ")
                .append("LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0 ")
                .append("WHERE i.is_del = 0 AND (i.order_id IS NULL OR o.status = 1)")
                .append(goodsFilter);
        params.addAll(filterParams);
        appendBusinessTimeRange(sql, params, "COALESCE(o.create_time, i.create_time)", startTime, endTime);

        sql.append(" UNION ALL SELECT i.goods_id, ")
                .append("CASE WHEN COALESCE(i.quantity, 0) < 0 THEN ABS(COALESCE(i.quantity, 0)) ELSE 0 END AS in_qty, ")
                .append("CASE WHEN COALESCE(i.quantity, 0) < 0 THEN ABS(COALESCE(i.total_amount, 0) - COALESCE(i.gross_profit, 0)) ELSE 0 END AS in_amount, ")
                .append("CASE WHEN COALESCE(i.quantity, 0) > 0 THEN COALESCE(i.quantity, 0) ELSE 0 END AS out_qty, ")
                .append("CASE WHEN COALESCE(i.quantity, 0) > 0 THEN ABS(COALESCE(i.total_amount, 0) - COALESCE(i.gross_profit, 0)) ELSE 0 END AS out_amount ")
                .append("FROM app_sale_order_item i ")
                .append("INNER JOIN app_goods g ON g.id = i.goods_id AND COALESCE(g.is_del, 0) = 0 ")
                .append("INNER JOIN app_sale_order o ON o.id = i.order_id AND o.is_del = 0 AND o.status = 1 ")
                .append("WHERE i.is_del = 0")
                .append(goodsFilter);
        params.addAll(filterParams);
        appendBusinessTimeRange(sql, params, "o.create_time", startTime, endTime);

        sql.append(" UNION ALL SELECT i.goods_id, ")
                .append("CASE WHEN COALESCE(i.profit_loss_quantity, 0) > 0 THEN COALESCE(i.profit_loss_quantity, 0) ELSE 0 END AS in_qty, ")
                .append("CASE WHEN COALESCE(i.profit_loss_quantity, 0) > 0 THEN ABS(").append(stockCheckAmount).append(") ELSE 0 END AS in_amount, ")
                .append("CASE WHEN COALESCE(i.profit_loss_quantity, 0) < 0 THEN ABS(COALESCE(i.profit_loss_quantity, 0)) ELSE 0 END AS out_qty, ")
                .append("CASE WHEN COALESCE(i.profit_loss_quantity, 0) < 0 THEN ABS(").append(stockCheckAmount).append(") ELSE 0 END AS out_amount ")
                .append("FROM app_stock_check_item i ")
                .append("INNER JOIN app_goods g ON g.id = i.goods_id AND COALESCE(g.is_del, 0) = 0 ")
                .append("INNER JOIN app_stock_check o ON o.id = i.check_id AND o.is_del = 0 ")
                .append("WHERE i.is_del = 0")
                .append(goodsFilter);
        params.addAll(filterParams);
        appendBusinessTimeRange(sql, params, "o.create_time", startTime, endTime);

        sql.append(") t GROUP BY goods_id");
        Map<String, StockPeriodAggregate> result = new HashMap<>();
        jdbcTemplate.query(sql.toString(), params.toArray(), resultSet -> {
            result.put(resultSet.getString("goods_id"), new StockPeriodAggregate(
                    decimal(resultSet.getObject("in_qty")),
                    decimal(resultSet.getObject("in_amount")),
                    decimal(resultSet.getObject("out_qty")),
                    decimal(resultSet.getObject("out_amount"))));
        });
        return result;
    }

    private Map<String, List<StockStatisticMovement>> queryStockStatisticMovements(String categoryId, String key,
                                                                                   Long endTime) {
        String goodsFilter = "";
        List<Object> filterParams = new ArrayList<>();
        if (StringUtils.isNotEmpty(categoryId) && !"0".equals(categoryId)) {
            goodsFilter += " AND g.category_id = ?";
            filterParams.add(categoryId);
        }
        if (StringUtils.isNotEmpty(key)) {
            goodsFilter += " AND (g.title LIKE ? OR g.goods_code LIKE ? OR g.py_code LIKE ?)";
            String likeKey = "%" + key + "%";
            filterParams.add(likeKey);
            filterParams.add(likeKey);
            filterParams.add(likeKey);
        }
        String purchaseEndFilter = buildBusinessTimeEndFilter("COALESCE(o.create_time, i.create_time)", endTime);
        String saleEndFilter = buildBusinessTimeEndFilter("o.create_time", endTime);
        String sql = "SELECT * FROM (" +
                " SELECT 1 AS bill_type, i.id AS item_id, i.goods_id, i.quantity, i.unit_price, i.total_amount, " +
                "        i.is_init, COALESCE(o.create_time, i.create_time) AS bill_time, o.order_type " +
                " FROM app_purchase_order_item i " +
                " INNER JOIN app_goods g ON g.id = i.goods_id AND COALESCE(g.is_del, 0) = 0 " +
                " LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0 " +
                " WHERE i.is_del = 0 AND (i.order_id IS NULL OR o.status = 1)" + goodsFilter + purchaseEndFilter +
                " UNION ALL " +
                " SELECT 2 AS bill_type, i.id AS item_id, i.goods_id, i.quantity, i.unit_price, i.total_amount, " +
                "        0 AS is_init, o.create_time AS bill_time, o.order_type " +
                " FROM app_sale_order_item i " +
                " INNER JOIN app_goods g ON g.id = i.goods_id AND COALESCE(g.is_del, 0) = 0 " +
                " INNER JOIN app_sale_order o ON o.id = i.order_id AND o.is_del = 0 AND o.status = 1 " +
                " WHERE i.is_del = 0" + goodsFilter + saleEndFilter +
                " UNION ALL " +
                " SELECT 3 AS bill_type, i.id AS item_id, i.goods_id, i.profit_loss_quantity AS quantity, " +
                "        i.unit_price, i.profit_loss_amount AS total_amount, 0 AS is_init, " +
                "        o.create_time AS bill_time, 0 AS order_type " +
                " FROM app_stock_check_item i " +
                " INNER JOIN app_goods g ON g.id = i.goods_id AND COALESCE(g.is_del, 0) = 0 " +
                " INNER JOIN app_stock_check o ON o.id = i.check_id AND o.is_del = 0 " +
                " WHERE i.is_del = 0" + goodsFilter + saleEndFilter +
                ") t";
        List<Object> params = new ArrayList<>(filterParams);
        addEndTimeParam(params, endTime);
        params.addAll(filterParams);
        addEndTimeParam(params, endTime);
        params.addAll(filterParams);
        addEndTimeParam(params, endTime);
        Map<String, List<StockStatisticMovement>> movementMap = new HashMap<>();
        jdbcTemplate.query(sql, params.toArray(), resultSet -> {
            String goodsId = resultSet.getString("goods_id");
            StockStatisticMovement movement = new StockStatisticMovement(
                    resultSet.getInt("bill_type"),
                    resultSet.getInt("item_id"),
                    decimal(resultSet.getObject("quantity")),
                    decimal(resultSet.getObject("unit_price")),
                    decimal(resultSet.getObject("total_amount")),
                    integer(resultSet.getObject("is_init")),
                    resultSet.getObject("bill_time"));
            movementMap.computeIfAbsent(goodsId, item -> new ArrayList<>()).add(movement);
        });
        return movementMap;
    }

    private void appendBusinessTimeRange(StringBuilder sql, List<Object> params, String expression,
                                         Long startTime, Long endTime) {
        if (startTime != null) {
            sql.append(" AND ").append(buildBusinessTimeCompare(expression, ">="));
            params.add(startTime);
        }
        if (endTime != null) {
            sql.append(" AND ").append(buildBusinessTimeCompare(expression, "<="));
            params.add(endTime);
        }
    }

    private String buildBusinessTimeEndFilter(String expression, Long endTime) {
        if (endTime == null) {
            return "";
        }
        return " AND " + buildBusinessTimeCompare(expression, "<=");
    }

    private void addEndTimeParam(List<Object> params, Long endTime) {
        if (endTime != null) {
            params.add(endTime);
        }
    }

    private String buildBusinessTimeCompare(String expression, String operator) {
        if (databaseDialect.isMySql()) {
            return expression + " " + operator + " FROM_UNIXTIME(? / 1000)";
        }
        return databaseDialect.epochMillis(expression) + " " + operator + " ?";
    }

    private String notDeleted(String alias) {
        if (databaseDialect.isMySql()) {
            return alias + ".is_del = 0";
        }
        return "COALESCE(" + alias + ".is_del, 0) = 0";
    }

    private List<Map<String, Object>> queryStockMovements(String goodsId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM (" +
                        " SELECT 1 AS bill_type, i.id AS item_id, i.quantity, i.unit_price, i.total_amount, " +
                        "        i.is_init, COALESCE(o.create_time, i.create_time) AS bill_time, " +
                        "        o.order_type, COALESCE(s.name, '') AS counterparty_name " +
                        " FROM app_purchase_order_item i " +
                        " LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0 " +
                        " LEFT JOIN app_supplier s ON s.id = o.supplier_id AND s.is_del = 0 " +
                        " WHERE i.is_del = 0 AND i.goods_id = ? AND (i.order_id IS NULL OR o.status = 1) " +
                        " UNION ALL " +
                        " SELECT 2 AS bill_type, i.id AS item_id, i.quantity, i.unit_price, i.total_amount, " +
                        "        0 AS is_init, o.create_time AS bill_time, o.order_type, " +
                        "        COALESCE(c.name, '') AS counterparty_name " +
                        " FROM app_sale_order_item i " +
                " INNER JOIN app_sale_order o ON o.id = i.order_id AND o.is_del = 0 AND o.status = 1 " +
                " LEFT JOIN app_customer c ON c.id = o.customer_id AND c.is_del = 0 " +
                " WHERE i.is_del = 0 AND i.goods_id = ? " +
                " UNION ALL " +
                " SELECT 3 AS bill_type, i.id AS item_id, i.profit_loss_quantity AS quantity, i.unit_price, " +
                "        i.profit_loss_amount AS total_amount, 0 AS is_init, o.create_time AS bill_time, " +
                "        0 AS order_type, '' AS counterparty_name " +
                " FROM app_stock_check_item i " +
                " INNER JOIN app_stock_check o ON o.id = i.check_id AND o.is_del = 0 " +
                " WHERE i.is_del = 0 AND i.goods_id = ? " +
                ") t",
                goodsId, goodsId, goodsId);
    }

    private StockBalance applyMovement(AppGoods goods, Map<String, Object> movement,
                                       BigDecimal stockQty, BigDecimal stockCost, BigDecimal costPrice) {
        BigDecimal quantity = decimal(movement.get("quantity"));
        Integer billType = integer(movement.get("bill_type"));
        if (billType != null && billType == 3) {
            BigDecimal unitPrice = decimal(movement.get("unit_price"));
            stockQty = stockQty.add(quantity);
            stockCost = stockCost.add(quantity.multiply(unitPrice));
        } else if (billType != null && billType == 1) {
            BigDecimal unitPrice = resolvePurchaseUnitPrice(goods, movement, quantity);
            stockQty = stockQty.add(quantity);
            stockCost = stockCost.add(quantity.multiply(unitPrice));
        } else {
            BigDecimal saleQty = quantity.abs();
            BigDecimal direction = quantity.compareTo(BigDecimal.ZERO) > 0
                    ? BigDecimal.ONE : BigDecimal.ONE.negate();
            stockQty = stockQty.subtract(saleQty.multiply(direction));
            stockCost = stockCost.subtract(saleQty.multiply(direction).multiply(costPrice));
        }

        if (stockQty.compareTo(BigDecimal.ZERO) == 0) {
            return new StockBalance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new StockBalance(stockQty, stockCost,
                stockCost.divide(stockQty, 4, RoundingMode.HALF_UP));
    }

    private StockBalance applyStockStatisticMovement(AppGoods goods, StockStatisticMovement movement,
                                                     BigDecimal stockQty, BigDecimal stockCost,
                                                     BigDecimal costPrice) {
        if (movement.billType != null && movement.billType == 3) {
            stockQty = stockQty.add(movement.quantity);
            stockCost = stockCost.add(movement.quantity.multiply(movement.unitPrice));
        } else if (movement.billType != null && movement.billType == 1) {
            BigDecimal unitPrice = resolvePurchaseUnitPrice(goods, movement, movement.quantity);
            stockQty = stockQty.add(movement.quantity);
            stockCost = stockCost.add(movement.quantity.multiply(unitPrice));
        } else {
            BigDecimal saleQty = movement.quantity.abs();
            BigDecimal direction = movement.quantity.compareTo(BigDecimal.ZERO) > 0
                    ? BigDecimal.ONE : BigDecimal.ONE.negate();
            stockQty = stockQty.subtract(saleQty.multiply(direction));
            stockCost = stockCost.subtract(saleQty.multiply(direction).multiply(costPrice));
        }
        if (stockQty.compareTo(BigDecimal.ZERO) == 0) {
            return new StockBalance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new StockBalance(stockQty, stockCost,
                stockCost.divide(stockQty, 4, RoundingMode.HALF_UP));
    }

    private String businessType(Map<String, Object> movement) {
        if (integer(movement.get("is_init")) != null && integer(movement.get("is_init")) == 1) {
            return "期初";
        }
        Integer billType = integer(movement.get("bill_type"));
        if (billType != null && billType == 3) {
            return decimal(movement.get("quantity")).compareTo(BigDecimal.ZERO) >= 0 ? "盘盈" : "盘亏";
        }
        Integer orderType = integer(movement.get("order_type"));
        if (billType != null && billType == 1) {
            return orderType != null && orderType == 2 ? "采购退货" : "进货";
        }
        return orderType != null && orderType == 2 ? "销售退货" : "销售";
    }

    private long timeValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).atZone(ZONE_ID).toInstant().toEpochMilli();
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).getTime();
        }
        if (value == null || StringUtils.isEmpty(value.toString())) {
            return 0L;
        }
        String text = value.toString().trim();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            // Continue with date parsing for legacy SQLite text timestamps.
        }
        try {
            return java.sql.Timestamp.valueOf(text.replace('T', ' ')).getTime();
        } catch (IllegalArgumentException ignored) {
            // Date-only values are also accepted.
        }
        try {
            return LocalDate.parse(text, DATE_FORMATTER).atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String formatDate(long time) {
        return time <= 0 ? "" : Instant.ofEpochMilli(time).atZone(ZONE_ID).format(DATE_FORMATTER);
    }

    private Double number(BigDecimal value, int scale) {
        return (value == null ? BigDecimal.ZERO : value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private Double money(BigDecimal value) {
        return number(value, 2);
    }

    private BigDecimal resolvePercent(Object value) {
        BigDecimal percent = decimal(value);
        return percent.compareTo(BigDecimal.ZERO) == 0 ? new BigDecimal("100") : percent;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static class StockBalance {
        private final BigDecimal stockQty;
        private final BigDecimal stockCost;
        private final BigDecimal costPrice;

        private StockBalance(BigDecimal stockQty, BigDecimal stockCost, BigDecimal costPrice) {
            this.stockQty = stockQty;
            this.stockCost = stockCost;
            this.costPrice = costPrice;
        }
    }

    private static class StockCostState {
        private BigDecimal stockQty = BigDecimal.ZERO;
        private BigDecimal stockCost = BigDecimal.ZERO;
        private BigDecimal costPrice;
        private BigDecimal lastCostPrice;
        private final ArrayDeque<PendingSaleCost> pendingSales = new ArrayDeque<>();

        private StockCostState(BigDecimal fallbackCostPrice) {
            BigDecimal fallback = fallbackCostPrice == null ? BigDecimal.ZERO : fallbackCostPrice;
            this.costPrice = fallback;
            this.lastCostPrice = fallback;
        }
    }

    private static class PendingSaleCost {
        private final Integer itemId;
        private BigDecimal remainingQty;
        private final BigDecimal estimateUnitPrice;

        private PendingSaleCost(Integer itemId, BigDecimal remainingQty, BigDecimal estimateUnitPrice) {
            this.itemId = itemId;
            this.remainingQty = remainingQty;
            this.estimateUnitPrice = estimateUnitPrice;
        }
    }

    private static class SaleCostUpdate {
        private static final String STATUS_NORMAL = "NORMAL";
        private static final String STATUS_TEMP = "TEMP";
        private static final String STATUS_ADJUSTED = "ADJUSTED";

        private final Integer itemId;
        private final Integer orderId;
        private final BigDecimal saleAmount;
        private BigDecimal costAmount = BigDecimal.ZERO;
        private BigDecimal costAdjustAmount = BigDecimal.ZERO;
        private BigDecimal temporaryRemainingQty = BigDecimal.ZERO;
        private boolean hadTemporaryCost;

        private SaleCostUpdate(Integer itemId, Integer orderId, BigDecimal saleAmount) {
            this.itemId = itemId;
            this.orderId = orderId;
            this.saleAmount = saleAmount == null ? BigDecimal.ZERO : saleAmount;
        }

        private String resolveStatus() {
            if (!hadTemporaryCost) {
                return STATUS_NORMAL;
            }
            return temporaryRemainingQty.compareTo(BigDecimal.ZERO) > 0
                    ? STATUS_TEMP
                    : STATUS_ADJUSTED;
        }
    }

    private void updateStockCost(AppGoods appGoods, String goodsId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM (" +
                        " SELECT 1 AS bill_type, i.id AS item_id, COALESCE(o.order_no, '') AS order_no, NULL AS order_id, i.quantity, i.unit_price, " +
                        "        i.total_amount, i.is_init, COALESCE(o.create_time, i.create_time) AS bill_time, 100 AS discount_rate " +
                        " FROM app_purchase_order_item i " +
                        " LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0 " +
                        " WHERE i.is_del = 0 AND i.goods_id = ? AND (i.order_id IS NULL OR o.status = 1) " +
                        " UNION ALL " +
                        " SELECT 2 AS bill_type, i.id AS item_id, o.order_no AS order_no, i.order_id, i.quantity, i.unit_price, i.total_amount, " +
                        "        0 AS is_init, o.create_time AS bill_time, COALESCE(o.discount_rate, 100) AS discount_rate " +
                        " FROM app_sale_order_item i " +
                        " INNER JOIN app_sale_order o ON o.id = i.order_id AND o.is_del = 0 AND o.status = 1 " +
                        " WHERE i.is_del = 0 AND i.goods_id = ? " +
                        " UNION ALL " +
                        " SELECT 3 AS bill_type, i.id AS item_id, o.order_no AS order_no, NULL AS order_id, i.profit_loss_quantity AS quantity, " +
                        "        i.unit_price, i.profit_loss_amount AS total_amount, 0 AS is_init, o.create_time AS bill_time, 100 AS discount_rate " +
                        " FROM app_stock_check_item i " +
                        " INNER JOIN app_stock_check o ON o.id = i.check_id AND o.is_del = 0 " +
                        " WHERE i.is_del = 0 AND i.goods_id = ? " +
                        ") t ORDER BY CASE WHEN bill_time IS NULL THEN 0 ELSE 1 END, bill_time, order_no, item_id",
                goodsId, goodsId, goodsId);
        StockCostState state = new StockCostState(resolveFallbackCostPrice(appGoods));
        Map<Integer, SaleCostUpdate> saleUpdates = new HashMap<>();
        Set<Integer> orderIds = new HashSet<>();
        deleteSaleCostAdjustments(goodsId);

        for (Map<String, Object> row : rows) {
            BigDecimal quantity = decimal(row.get("quantity"));
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Integer billType = integer(row.get("bill_type"));
            if (billType != null && billType == 3) {
                applyInventoryAdjustment(state, quantity, decimal(row.get("unit_price")), saleUpdates,
                        goodsId, "STOCK_CHECK", integer(row.get("item_id")));
            } else if (billType != null && billType == 1) {
                applyPurchaseMovement(state, quantity, resolvePurchaseUnitPrice(appGoods, row, quantity), saleUpdates,
                        goodsId, "PURCHASE", integer(row.get("item_id")));
            } else {
                applySaleMovement(state, row, quantity, saleUpdates, orderIds);
            }
        }

        normalizeEndingCost(state);
        appGoods.setStockCost(state.stockCost.setScale(2, RoundingMode.HALF_UP).doubleValue());
        appGoods.setCostPrice(state.costPrice.setScale(4, RoundingMode.HALF_UP).doubleValue());
        flushSaleCostUpdates(saleUpdates);
        refreshSaleOrderGrossProfits(orderIds);
    }

    private void applyInventoryAdjustment(StockCostState state, BigDecimal quantity, BigDecimal unitPrice,
                                          Map<Integer, SaleCostUpdate> saleUpdates,
                                          String goodsId, String sourceType, Integer sourceItemId) {
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            applyPurchaseMovement(state, quantity, unitPrice, saleUpdates, goodsId, sourceType, sourceItemId);
            return;
        }
        state.stockQty = state.stockQty.add(quantity);
        state.stockCost = state.stockCost.add(quantity.multiply(unitPrice));
        refreshRunningCostPrice(state);
    }

    private void applyPurchaseMovement(StockCostState state, BigDecimal quantity, BigDecimal unitPrice,
                                       Map<Integer, SaleCostUpdate> saleUpdates,
                                       String goodsId, String sourceType, Integer sourceItemId) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            state.stockQty = state.stockQty.add(quantity);
            state.stockCost = state.stockCost.add(quantity.multiply(unitPrice));
            refreshRunningCostPrice(state);
            return;
        }

        BigDecimal remainingQty = quantity;
        if (state.stockQty.compareTo(BigDecimal.ZERO) < 0 && !state.pendingSales.isEmpty()) {
            BigDecimal coverQty = remainingQty.min(state.stockQty.abs());
            BigDecimal estimatedCoveredCost = coverPendingSales(state, coverQty, unitPrice, saleUpdates,
                    goodsId, sourceType, sourceItemId);
            state.stockQty = state.stockQty.add(coverQty);
            state.stockCost = state.stockCost.add(estimatedCoveredCost);
            remainingQty = remainingQty.subtract(coverQty);
            if (state.stockQty.compareTo(BigDecimal.ZERO) == 0) {
                state.stockCost = BigDecimal.ZERO;
            }
        }

        if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            state.stockQty = state.stockQty.add(remainingQty);
            state.stockCost = state.stockCost.add(remainingQty.multiply(unitPrice));
        }
        refreshRunningCostPrice(state);
    }

    private BigDecimal coverPendingSales(StockCostState state, BigDecimal coverQty, BigDecimal actualUnitPrice,
                                         Map<Integer, SaleCostUpdate> saleUpdates,
                                         String goodsId, String sourceType, Integer sourceItemId) {
        BigDecimal remaining = coverQty;
        BigDecimal estimatedCoveredCost = BigDecimal.ZERO;
        while (remaining.compareTo(BigDecimal.ZERO) > 0 && !state.pendingSales.isEmpty()) {
            PendingSaleCost pending = state.pendingSales.peek();
            BigDecimal matchedQty = remaining.min(pending.remainingQty);
            BigDecimal estimatedCost = matchedQty.multiply(pending.estimateUnitPrice);
            BigDecimal adjustAmount = matchedQty.multiply(actualUnitPrice.subtract(pending.estimateUnitPrice));
            SaleCostUpdate update = saleUpdates == null ? null : saleUpdates.get(pending.itemId);
            if (update != null) {
                update.costAmount = update.costAmount.add(adjustAmount);
                update.costAdjustAmount = update.costAdjustAmount.add(adjustAmount);
                update.temporaryRemainingQty = update.temporaryRemainingQty.subtract(matchedQty);
                insertSaleCostAdjustment(goodsId, update.orderId, update.itemId, sourceType, sourceItemId,
                        matchedQty, pending.estimateUnitPrice, actualUnitPrice, adjustAmount);
            }
            pending.remainingQty = pending.remainingQty.subtract(matchedQty);
            remaining = remaining.subtract(matchedQty);
            estimatedCoveredCost = estimatedCoveredCost.add(estimatedCost);
            if (pending.remainingQty.compareTo(BigDecimal.ZERO) == 0) {
                state.pendingSales.poll();
            }
        }
        return estimatedCoveredCost;
    }

    private void applySaleMovement(StockCostState state, Map<String, Object> row, BigDecimal quantity,
                                   Map<Integer, SaleCostUpdate> saleUpdates, Set<Integer> orderIds) {
        Integer itemId = integer(row.get("item_id"));
        if (itemId == null) {
            return;
        }
        Integer orderId = integer(row.get("order_id"));
        if (orderId != null) {
            orderIds.add(orderId);
        }
        BigDecimal saleAmount = decimal(row.get("total_amount"))
                .multiply(resolvePercent(row.get("discount_rate")))
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        SaleCostUpdate update = new SaleCostUpdate(itemId, orderId, saleAmount);
        saleUpdates.put(itemId, update);

        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal returnQty = quantity.abs();
            BigDecimal costPrice = estimateCostPrice(state, decimal(row.get("unit_price")));
            BigDecimal returnCost = returnQty.multiply(costPrice);
            state.stockQty = state.stockQty.add(returnQty);
            state.stockCost = state.stockCost.add(returnCost);
            update.costAmount = update.costAmount.subtract(returnCost);
            refreshRunningCostPrice(state);
            return;
        }

        BigDecimal remainingQty = quantity;
        BigDecimal fallbackUnitPrice = decimal(row.get("unit_price"));
        if (state.stockQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal normalQty = remainingQty.min(state.stockQty);
            BigDecimal normalCost = normalQty.multiply(estimateCostPrice(state, fallbackUnitPrice));
            update.costAmount = update.costAmount.add(normalCost);
            state.stockQty = state.stockQty.subtract(normalQty);
            state.stockCost = state.stockCost.subtract(normalCost);
            remainingQty = remainingQty.subtract(normalQty);
            if (state.stockQty.compareTo(BigDecimal.ZERO) == 0) {
                state.stockCost = BigDecimal.ZERO;
            }
        }

        if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal estimateUnitPrice = estimateCostPrice(state, fallbackUnitPrice);
            BigDecimal tempCost = remainingQty.multiply(estimateUnitPrice);
            update.costAmount = update.costAmount.add(tempCost);
            update.hadTemporaryCost = true;
            update.temporaryRemainingQty = update.temporaryRemainingQty.add(remainingQty);
            state.pendingSales.add(new PendingSaleCost(itemId, remainingQty, estimateUnitPrice));
            state.stockQty = state.stockQty.subtract(remainingQty);
            state.stockCost = state.stockCost.subtract(tempCost);
        }
    }

    private void flushSaleCostUpdates(Map<Integer, SaleCostUpdate> saleUpdates) {
        if (saleUpdates == null || saleUpdates.isEmpty()) {
            return;
        }
        for (SaleCostUpdate update : saleUpdates.values()) {
            String status = update.resolveStatus();
            BigDecimal grossProfit = update.saleAmount.subtract(update.costAmount);
            jdbcTemplate.update("UPDATE app_sale_order_item SET cost_amount = ?, cost_adjust_amount = ?, " +
                            "cost_status = ?, gross_profit = ? WHERE id = ?",
                    money(update.costAmount), money(update.costAdjustAmount), status,
                    money(grossProfit), update.itemId);
        }
    }

    private void refreshSaleOrderGrossProfits(Set<Integer> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        for (Integer orderId : orderIds) {
            jdbcTemplate.update("UPDATE app_sale_order SET gross_profit = (" +
                    "SELECT COALESCE(SUM(gross_profit), 0) FROM app_sale_order_item " +
                    "WHERE order_id = ? AND is_del = 0) WHERE id = ?", orderId, orderId);
        }
    }

    private void deleteSaleCostAdjustments(String goodsId) {
        jdbcTemplate.update("DELETE FROM app_sale_cost_adjustment WHERE goods_id = ?", goodsId);
    }

    private void insertSaleCostAdjustment(String goodsId, Integer saleOrderId, Integer saleItemId,
                                          String sourceType, Integer sourceItemId, BigDecimal quantity,
                                          BigDecimal estimateUnitPrice, BigDecimal actualUnitPrice,
                                          BigDecimal adjustAmount) {
        if (adjustAmount == null || adjustAmount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO app_sale_cost_adjustment " +
                        "(goods_id, sale_order_id, sale_item_id, source_type, source_item_id, quantity, " +
                        "estimate_unit_price, actual_unit_price, adjust_amount, create_time, is_del) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, " + databaseDialect.currentTimestamp() + ", 0)",
                goodsId, saleOrderId, saleItemId, sourceType, sourceItemId,
                number(quantity, 4), number(estimateUnitPrice, 4),
                number(actualUnitPrice, 4), money(adjustAmount));
    }

    private void refreshRunningCostPrice(StockCostState state) {
        if (state.stockQty.compareTo(BigDecimal.ZERO) == 0) {
            state.stockCost = BigDecimal.ZERO;
            return;
        }
        state.costPrice = state.stockCost.divide(state.stockQty, 4, RoundingMode.HALF_UP);
        if (state.costPrice.compareTo(BigDecimal.ZERO) > 0) {
            state.lastCostPrice = state.costPrice;
        }
    }

    private void normalizeEndingCost(StockCostState state) {
        if (state.stockQty.compareTo(BigDecimal.ZERO) == 0) {
            state.stockCost = BigDecimal.ZERO;
            state.costPrice = BigDecimal.ZERO;
            return;
        }
        state.costPrice = state.stockCost.divide(state.stockQty, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal estimateCostPrice(StockCostState state) {
        return estimateCostPrice(state, BigDecimal.ZERO);
    }

    private BigDecimal estimateCostPrice(StockCostState state, BigDecimal fallbackUnitPrice) {
        if (state.costPrice.compareTo(BigDecimal.ZERO) > 0) {
            return state.costPrice;
        }
        if (state.lastCostPrice.compareTo(BigDecimal.ZERO) > 0) {
            return state.lastCostPrice;
        }
        if (fallbackUnitPrice != null && fallbackUnitPrice.compareTo(BigDecimal.ZERO) > 0) {
            return fallbackUnitPrice;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveFallbackCostPrice(AppGoods appGoods) {
        if (appGoods == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal costPrice = decimal(appGoods.getCostPrice());
        if (costPrice.compareTo(BigDecimal.ZERO) > 0) {
            return costPrice;
        }
        BigDecimal purchasePrice = decimal(appGoods.getPurPrc());
        if (purchasePrice.compareTo(BigDecimal.ZERO) > 0) {
            return purchasePrice;
        }
        return decimal(appGoods.getInitCost());
    }

    private BigDecimal resolvePurchaseUnitPrice(AppGoods appGoods, Map<String, Object> row, BigDecimal quantity) {
        BigDecimal unitPrice = decimal(row.get("unit_price"));
        if (unitPrice.compareTo(BigDecimal.ZERO) != 0) {
            return unitPrice;
        }
        BigDecimal totalAmount = decimal(row.get("total_amount"));
        if (totalAmount.compareTo(BigDecimal.ZERO) != 0 && quantity.compareTo(BigDecimal.ZERO) != 0) {
            return totalAmount.divide(quantity, 4, RoundingMode.HALF_UP);
        }
        Integer isInit = integer(row.get("is_init"));
        if (isInit != null && isInit == 1) {
            return decimal(appGoods.getInitCost());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolvePurchaseUnitPrice(AppGoods appGoods, StockStatisticMovement row,
                                                BigDecimal quantity) {
        if (row.unitPrice.compareTo(BigDecimal.ZERO) != 0) {
            return row.unitPrice;
        }
        if (row.totalAmount.compareTo(BigDecimal.ZERO) != 0 && quantity.compareTo(BigDecimal.ZERO) != 0) {
            return row.totalAmount.divide(quantity, 4, RoundingMode.HALF_UP);
        }
        if (row.isInit != null && row.isInit == 1) {
            return decimal(appGoods.getInitCost());
        }
        return BigDecimal.ZERO;
    }

    private void initCostFields(AppGoods entity) {
        if(entity == null) return;
        BigDecimal stock = decimal(entity.getStock());
        BigDecimal costPrice = decimal(entity.getInitCost());
        entity.setCostPrice(costPrice.setScale(4, RoundingMode.HALF_UP).doubleValue());
        entity.setStockCost(stock.multiply(costPrice).setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(value.toString());
    }

    private void clearStockStatisticsCache() {
        stockStatisticsCache.clear();
    }

    private static class StockPeriodAggregate {
        private static final StockPeriodAggregate EMPTY = new StockPeriodAggregate(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        private final BigDecimal inQty;
        private final BigDecimal inAmount;
        private final BigDecimal outQty;
        private final BigDecimal outAmount;

        private StockPeriodAggregate(BigDecimal inQty, BigDecimal inAmount,
                                     BigDecimal outQty, BigDecimal outAmount) {
            this.inQty = inQty;
            this.inAmount = inAmount;
            this.outQty = outQty;
            this.outAmount = outAmount;
        }
    }

    private static class StockStatisticsSqlParts {
        private final String cteSql;
        private final List<Object> params;

        private StockStatisticsSqlParts(String cteSql, List<Object> params) {
            this.cteSql = cteSql;
            this.params = params;
        }
    }
    private static class StockStatisticMovement {
        private final Integer billType;
        private final Integer itemId;
        private final BigDecimal quantity;
        private final BigDecimal unitPrice;
        private final BigDecimal totalAmount;
        private final Integer isInit;
        private final Object billTime;

        private StockStatisticMovement(Integer billType, Integer itemId, BigDecimal quantity,
                                       BigDecimal unitPrice, BigDecimal totalAmount, Integer isInit,
                                       Object billTime) {
            this.billType = billType;
            this.itemId = itemId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalAmount = totalAmount;
            this.isInit = isInit;
            this.billTime = billTime;
        }
    }

    private static class StockSummary {
        private final BigDecimal openingQty;
        private final BigDecimal openingAmount;
        private final BigDecimal inQty;
        private final BigDecimal inAmount;
        private final BigDecimal outQty;
        private final BigDecimal outAmount;
        private final BigDecimal endingQty;
        private final BigDecimal endingAmount;

        private StockSummary(BigDecimal openingQty, BigDecimal openingAmount,
                             BigDecimal inQty, BigDecimal inAmount,
                             BigDecimal outQty, BigDecimal outAmount,
                             BigDecimal endingQty, BigDecimal endingAmount) {
            this.openingQty = openingQty;
            this.openingAmount = openingAmount;
            this.inQty = inQty;
            this.inAmount = inAmount;
            this.outQty = outQty;
            this.outAmount = outAmount;
            this.endingQty = endingQty;
            this.endingAmount = endingAmount;
        }

        private boolean isEmpty() {
            return openingQty.compareTo(BigDecimal.ZERO) == 0
                    && openingAmount.compareTo(BigDecimal.ZERO) == 0
                    && inQty.compareTo(BigDecimal.ZERO) == 0
                    && inAmount.compareTo(BigDecimal.ZERO) == 0
                    && outQty.compareTo(BigDecimal.ZERO) == 0
                    && outAmount.compareTo(BigDecimal.ZERO) == 0
                    && endingQty.compareTo(BigDecimal.ZERO) == 0
                    && endingAmount.compareTo(BigDecimal.ZERO) == 0;
        }
    }

    private class StockStatisticsRow {
        private final Integer goodsId;
        private final String goodsName;
        private final String unit;
        private final StockSummary summary;

        private StockStatisticsRow(AppGoods goods, StockSummary summary) {
            this.goodsId = goods.getId();
            this.goodsName = goods.getTitle();
            this.unit = goods.getUnit();
            this.summary = summary;
        }

        private JSONObject toJson(int rowNo) {
            JSONObject row = new JSONObject();
            row.put("rowNo", rowNo);
            row.put("goodsId", goodsId);
            row.put("goodsName", goodsName);
            row.put("unit", unit);
            row.put("openingQty", number(summary.openingQty, 4));
            row.put("openingAmount", number(summary.openingAmount, 2));
            row.put("inQty", number(summary.inQty, 4));
            row.put("inAmount", number(summary.inAmount, 2));
            row.put("outQty", number(summary.outQty, 4));
            row.put("outAmount", number(summary.outAmount, 2));
            row.put("endingQty", number(summary.endingQty, 4));
            row.put("endingAmount", number(summary.endingAmount, 2));
            return row;
        }
    }

    private static class StockStatisticsCache {
        private final long createdAt;
        private final List<StockStatisticsRow> records;
        private final BigDecimal openingQtyTotal;
        private final BigDecimal openingAmountTotal;
        private final BigDecimal inQtyTotal;
        private final BigDecimal inAmountTotal;
        private final BigDecimal outQtyTotal;
        private final BigDecimal outAmountTotal;
        private final BigDecimal endingQtyTotal;
        private final BigDecimal endingAmountTotal;

        private StockStatisticsCache(long createdAt, List<StockStatisticsRow> records,
                                     BigDecimal openingQtyTotal, BigDecimal openingAmountTotal,
                                     BigDecimal inQtyTotal, BigDecimal inAmountTotal,
                                     BigDecimal outQtyTotal, BigDecimal outAmountTotal,
                                     BigDecimal endingQtyTotal, BigDecimal endingAmountTotal) {
            this.createdAt = createdAt;
            this.records = records;
            this.openingQtyTotal = openingQtyTotal;
            this.openingAmountTotal = openingAmountTotal;
            this.inQtyTotal = inQtyTotal;
            this.inAmountTotal = inAmountTotal;
            this.outQtyTotal = outQtyTotal;
            this.outAmountTotal = outAmountTotal;
            this.endingQtyTotal = endingQtyTotal;
            this.endingAmountTotal = endingAmountTotal;
        }
    }
}




