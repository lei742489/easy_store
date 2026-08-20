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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private AppSaleOrderItemMapper saleOrderItemMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DatabaseDialect databaseDialect;
    private final Map<String, StockStatisticsCache> stockStatisticsCache = new ConcurrentHashMap<>();
    private final Object stockStatisticsCacheLock = new Object();

    @EventListener(ApplicationReadyEvent.class)
    public void refreshAllStockCosts() {
       List<AppGoods> goodsList = list();
       for(AppGoods goods:goodsList){
           if(goods.getId()!=null){
               updateStock(goods.getId().toString());
           }
       }
    }

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
       if(pageNo ==null) pageNo = 1;
       /* if(StringUtils.isEmpty(key))
            return new ArrayList<>();*/

        String pyCode = null;
        if(key.matches("^[a-zA-Z0-9]+$"))
            pyCode = key;

        return mapper.searchByKey(key,pyCode,pageNo,CommonConstant.AUTO_COMPLETE_MAX_SEARCH_COUNT);
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
        clearStockStatisticsCache();
        AppGoods appGoods = getById(goodsId);
        if(appGoods!=null){
            Integer p = purchaseOrderItemService. sumQuantityGoodsId(appGoods.getId().toString());
            Integer s = saleOrderItemService.sumQuantityGoodsId(appGoods.getId().toString());
            Integer adjustmentQuantity = stockCheckItemService
                    .sumProfitLossQuantityByGoodsId(appGoods.getId().toString());
            appGoods.setStock(p - s + adjustmentQuantity);
            updateStockCost(appGoods, goodsId);

            Map<String,Double> map = purchaseOrderItemMapper.sumPurchaseGoodsPrices(goodsId);
            Map<String,Double> saleMap = saleOrderItemMapper.sumPurchaseGoodsSalePrc(goodsId);
            if(map !=null && map.get("purPrc")!=null){
                appGoods.setPurPrc(map.get("purPrc"));
            }
            if(saleMap !=null && saleMap.get("salePrc")!=null){
                appGoods.setSalePrc(saleMap.get("salePrc"));
            }
            super.updateById(appGoods);
        }
    }

    @Override
    public String getTitleById(String goodsId) {
       AppGoods appGoods = getById(goodsId);
        return appGoods!=null ? appGoods.getTitle() : "";
    }

    @Override
    public JSONObject getStockDetail(String goodsId, Long startTime, Long endTime) {
        AppGoods goods = getById(goodsId);
        if (goods == null) {
            return null;
        }

        List<Map<String, Object>> movements = queryStockMovements(goodsId);
        movements.sort(Comparator
                .comparingLong((Map<String, Object> row) -> timeValue(row.get("bill_time")))
                .thenComparing(row -> integer(row.get("item_id")), Comparator.nullsLast(Integer::compareTo)));

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
        int rowNo = 1;
        List<JSONObject> records = new ArrayList<>();

        for (Map<String, Object> movement : movements) {
            BigDecimal quantity = decimal(movement.get("quantity"));
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            long businessTime = timeValue(movement.get("bill_time"));
            if (startTime != null && businessTime < startTime) {
                StockBalance balance = applyMovement(goods, movement, stockQty, stockCost, costPrice);
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

            BigDecimal inQty = BigDecimal.ZERO;
            BigDecimal inPrice = BigDecimal.ZERO;
            BigDecimal inAmount = BigDecimal.ZERO;
            BigDecimal outQty = BigDecimal.ZERO;
            BigDecimal outPrice = BigDecimal.ZERO;
            BigDecimal outAmount = BigDecimal.ZERO;
            Integer billType = integer(movement.get("bill_type"));

            if (billType != null && billType == 3) {
                BigDecimal unitPrice = decimal(movement.get("unit_price"));
                if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                    inQty = quantity;
                    inPrice = unitPrice;
                    inAmount = quantity.multiply(unitPrice);
                    inQtyTotal = inQtyTotal.add(inQty);
                    inTotal = inTotal.add(inAmount);
                } else {
                    outQty = quantity.abs();
                    outPrice = unitPrice;
                    outAmount = outQty.multiply(unitPrice);
                    outQtyTotal = outQtyTotal.add(outQty);
                    outTotal = outTotal.add(outAmount);
                }
            } else if (billType != null && billType == 1) {
                BigDecimal unitPrice = resolvePurchaseUnitPrice(goods, movement, quantity);
                if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                    inQty = quantity;
                    inPrice = unitPrice;
                    inAmount = quantity.multiply(unitPrice);
                    inQtyTotal = inQtyTotal.add(inQty);
                    inTotal = inTotal.add(inAmount);
                } else {
                    outQty = quantity.abs();
                    outPrice = unitPrice;
                    outAmount = outQty.multiply(unitPrice);
                    outQtyTotal = outQtyTotal.add(outQty);
                    outTotal = outTotal.add(outAmount);
                }
            } else {
                BigDecimal quantityAbs = quantity.abs();
                if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                    outQty = quantityAbs;
                    outPrice = costPrice;
                    outAmount = quantityAbs.multiply(costPrice);
                    outQtyTotal = outQtyTotal.add(outQty);
                    outTotal = outTotal.add(outAmount);
                } else {
                    inQty = quantityAbs;
                    inPrice = costPrice;
                    inAmount = quantityAbs.multiply(costPrice);
                    inQtyTotal = inQtyTotal.add(inQty);
                    inTotal = inTotal.add(inAmount);
                }
            }

            StockBalance balance = applyMovement(goods, movement, stockQty, stockCost, costPrice);
            stockQty = balance.stockQty;
            stockCost = balance.stockCost;
            costPrice = balance.costPrice;

            JSONObject row = new JSONObject();
            row.put("rowNo", rowNo++);
            row.put("businessType", businessType(movement));
            row.put("businessDate", formatDate(businessTime));
            row.put("counterpartyName", stringValue(movement.get("counterparty_name")));
            row.put("inQty", number(inQty, 4));
            row.put("inUnitPrice", number(inPrice, 4));
            row.put("inAmount", number(inAmount, 2));
            row.put("outQty", number(outQty, 4));
            row.put("outCostPrice", number(outPrice, 4));
            row.put("outAmount", number(outAmount, 2));
            row.put("endingQty", number(stockQty, 4));
            row.put("endingCostPrice", number(costPrice, 4));
            row.put("endingAmount", number(stockCost, 2));
            records.add(row);
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
        result.put("endingQty", number(stockQty, 4));
        result.put("endingCostPrice", number(costPrice, 4));
        result.put("endingAmount", number(stockCost, 2));
        result.put("records", records);
        return result;
    }

    @Override
    public JSONObject getStockStatistics(String categoryId, String key, Long startTime, Long endTime,
                                         Integer current, Integer pageSize) {
        int currentPage = current == null || current < 1 ? 1 : current;
        int size = pageSize == null || pageSize < 1 ? 50 : pageSize;
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
        if (canUseCurrentStockSnapshot(endTime)) {
            return calculateCurrentStockStatistics(goodsList, categoryId, key,
                    startTime, endTime, createdAt);
        }
        Map<String, List<StockStatisticMovement>> movementMap =
                queryStockStatisticMovements(categoryId, key, endTime);

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
            List<StockStatisticMovement> movements =
                    movementMap.getOrDefault(String.valueOf(goods.getId()), Collections.emptyList());
            if (movements.isEmpty()) {
                continue;
            }
            StockSummary detail = calculateStockSummary(goods, movements,
                    startTime, endTime);
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
            return java.sql.Timestamp.valueOf(text).getTime();
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

    private void updateStockCost(AppGoods appGoods, String goodsId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM (" +
                        " SELECT 1 AS bill_type, i.id AS item_id, i.quantity, i.unit_price, i.total_amount, i.is_init, " +
                        "        COALESCE(o.create_time, i.create_time) AS bill_time " +
                        " FROM app_purchase_order_item i " +
                        " LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0 " +
                        " WHERE i.is_del = 0 AND i.goods_id = ? AND (i.order_id IS NULL OR o.status = 1) " +
                        " UNION ALL " +
                        " SELECT 2 AS bill_type, i.id AS item_id, i.quantity, i.unit_price, i.total_amount, 0 AS is_init, " +
                        "        o.create_time AS bill_time " +
                        " FROM app_sale_order_item i " +
                        " INNER JOIN app_sale_order o ON o.id = i.order_id AND o.is_del = 0 AND o.status = 1 " +
                        " WHERE i.is_del = 0 AND i.goods_id = ? " +
                        " UNION ALL " +
                        " SELECT 3 AS bill_type, i.id AS item_id, i.profit_loss_quantity AS quantity, i.unit_price, " +
                        "        i.profit_loss_amount AS total_amount, 0 AS is_init, o.create_time AS bill_time " +
                        " FROM app_stock_check_item i " +
                        " INNER JOIN app_stock_check o ON o.id = i.check_id AND o.is_del = 0 " +
                        " WHERE i.is_del = 0 AND i.goods_id = ? " +
                        ") t ORDER BY CASE WHEN bill_time IS NULL THEN 0 ELSE 1 END, bill_time, item_id",
                goodsId, goodsId, goodsId);
        BigDecimal stockQty = BigDecimal.ZERO;
        BigDecimal stockCost = BigDecimal.ZERO;
        BigDecimal costPrice = BigDecimal.ZERO;

        for (Map<String, Object> row : rows) {
            BigDecimal quantity = decimal(row.get("quantity"));
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Integer billType = integer(row.get("bill_type"));
            if (billType != null && billType == 3) {
                BigDecimal unitPrice = decimal(row.get("unit_price"));
                stockQty = stockQty.add(quantity);
                stockCost = stockCost.add(quantity.multiply(unitPrice));
                if (stockQty.compareTo(BigDecimal.ZERO) != 0) {
                    costPrice = stockCost.divide(stockQty, 4, RoundingMode.HALF_UP);
                } else {
                    stockCost = BigDecimal.ZERO;
                    costPrice = BigDecimal.ZERO;
                }
            } else if (billType != null && billType == 1) {
                BigDecimal unitPrice = resolvePurchaseUnitPrice(appGoods, row, quantity);
                stockQty = stockQty.add(quantity);
                stockCost = stockCost.add(quantity.multiply(unitPrice));
                if (stockQty.compareTo(BigDecimal.ZERO) != 0) {
                    costPrice = stockCost.divide(stockQty, 4, RoundingMode.HALF_UP);
                } else {
                    stockCost = BigDecimal.ZERO;
                    costPrice = BigDecimal.ZERO;
                }
            } else {
                BigDecimal saleQty = quantity.abs();
                BigDecimal direction = quantity.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.ONE : BigDecimal.ONE.negate();
                stockQty = stockQty.subtract(saleQty.multiply(direction));
                stockCost = stockCost.subtract(saleQty.multiply(direction).multiply(costPrice));
                if (stockQty.compareTo(BigDecimal.ZERO) == 0) {
                    stockCost = BigDecimal.ZERO;
                    costPrice = BigDecimal.ZERO;
                } else {
                    costPrice = stockCost.divide(stockQty, 4, RoundingMode.HALF_UP);
                }
            }
        }

        appGoods.setStockCost(stockCost.setScale(2, RoundingMode.HALF_UP).doubleValue());
        appGoods.setCostPrice(costPrice.setScale(4, RoundingMode.HALF_UP).doubleValue());
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




