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

      return flag;
   }

    @Override
    public boolean updateById(AppGoods entity){
        entity.setUnit(appUnitService.normalizeName(entity.getUnit()));
        appUnitService.updateByName(entity.getUnit());
        return super.updateById(entity);
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
        Map<String, List<Map<String, Object>>> movementMap = new HashMap<>();
        for (Map<String, Object> movement : queryStockStatisticMovements(categoryId, key)) {
            String goodsId = stringValue(movement.get("goods_id"));
            movementMap.computeIfAbsent(goodsId, item -> new ArrayList<>()).add(movement);
        }

        BigDecimal openingQtyTotal = BigDecimal.ZERO;
        BigDecimal openingAmountTotal = BigDecimal.ZERO;
        BigDecimal inQtyTotal = BigDecimal.ZERO;
        BigDecimal inAmountTotal = BigDecimal.ZERO;
        BigDecimal outQtyTotal = BigDecimal.ZERO;
        BigDecimal outAmountTotal = BigDecimal.ZERO;
        BigDecimal endingQtyTotal = BigDecimal.ZERO;
        BigDecimal endingAmountTotal = BigDecimal.ZERO;
        int fromIndex = Math.min((currentPage - 1) * size, goodsList.size());
        int toIndex = Math.min(fromIndex + size, goodsList.size());
        JSONArray records = new JSONArray();

        for (int index = 0; index < goodsList.size(); index++) {
            AppGoods goods = goodsList.get(index);
            JSONObject detail = calculateStockSummary(goods,
                    movementMap.getOrDefault(String.valueOf(goods.getId()), Collections.emptyList()),
                    startTime, endTime);
            BigDecimal openingQty = decimal(detail.get("openingQty"));
            BigDecimal openingAmount = decimal(detail.get("openingAmount"));
            BigDecimal inQty = decimal(detail.get("inQtyTotal"));
            BigDecimal inAmount = decimal(detail.get("inTotal"));
            BigDecimal outQty = decimal(detail.get("outQtyTotal"));
            BigDecimal outAmount = decimal(detail.get("outTotal"));
            BigDecimal endingQty = decimal(detail.get("endingQty"));
            BigDecimal endingAmount = decimal(detail.get("endingAmount"));
            openingQtyTotal = openingQtyTotal.add(openingQty);
            openingAmountTotal = openingAmountTotal.add(openingAmount);
            inQtyTotal = inQtyTotal.add(inQty);
            inAmountTotal = inAmountTotal.add(inAmount);
            outQtyTotal = outQtyTotal.add(outQty);
            outAmountTotal = outAmountTotal.add(outAmount);
            endingQtyTotal = endingQtyTotal.add(endingQty);
            endingAmountTotal = endingAmountTotal.add(endingAmount);

            if (index < fromIndex || index >= toIndex) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("rowNo", index + 1);
            row.put("goodsId", goods.getId());
            row.put("goodsName", goods.getTitle());
            row.put("unit", goods.getUnit());
            row.put("openingQty", number(openingQty, 4));
            row.put("openingAmount", number(openingAmount, 2));
            row.put("inQty", number(inQty, 4));
            row.put("inAmount", number(inAmount, 2));
            row.put("outQty", number(outQty, 4));
            row.put("outAmount", number(outAmount, 2));
            row.put("endingQty", number(endingQty, 4));
            row.put("endingAmount", number(endingAmount, 2));
            records.add(row);
        }

        JSONObject result = new JSONObject();
        result.put("current", currentPage);
        result.put("pageSize", size);
        result.put("total", goodsList.size());
        result.put("pages", (int) Math.ceil(goodsList.size() * 1D / size));
        result.put("openingQtyTotal", number(openingQtyTotal, 4));
        result.put("openingAmountTotal", number(openingAmountTotal, 2));
        result.put("inQtyTotal", number(inQtyTotal, 4));
        result.put("inAmountTotal", number(inAmountTotal, 2));
        result.put("outQtyTotal", number(outQtyTotal, 4));
        result.put("outAmountTotal", number(outAmountTotal, 2));
        result.put("endingQtyTotal", number(endingQtyTotal, 4));
        result.put("endingAmountTotal", number(endingAmountTotal, 2));
        result.put("records", records);
        return result;
    }

    private JSONObject calculateStockSummary(AppGoods goods, List<Map<String, Object>> sourceMovements,
                                             Long startTime, Long endTime) {
        List<Map<String, Object>> movements = new ArrayList<>(sourceMovements);
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

            Integer billType = integer(movement.get("bill_type"));
            if (billType != null && billType == 3) {
                BigDecimal unitPrice = decimal(movement.get("unit_price"));
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

            StockBalance balance = applyMovement(goods, movement, stockQty, stockCost, costPrice);
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

        JSONObject result = new JSONObject();
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
        return result;
    }

    private List<Map<String, Object>> queryStockStatisticMovements(String categoryId, String key) {
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
        String sql = "SELECT * FROM (" +
                " SELECT 1 AS bill_type, i.id AS item_id, i.goods_id, i.quantity, i.unit_price, i.total_amount, " +
                "        i.is_init, COALESCE(o.create_time, i.create_time) AS bill_time, o.order_type " +
                " FROM app_purchase_order_item i " +
                " INNER JOIN app_goods g ON g.id = i.goods_id AND COALESCE(g.is_del, 0) = 0 " +
                " LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0 " +
                " WHERE i.is_del = 0 AND (i.order_id IS NULL OR o.status = 1)" + goodsFilter +
                " UNION ALL " +
                " SELECT 2 AS bill_type, i.id AS item_id, i.goods_id, i.quantity, i.unit_price, i.total_amount, " +
                "        0 AS is_init, o.create_time AS bill_time, o.order_type " +
                " FROM app_sale_order_item i " +
                " INNER JOIN app_goods g ON g.id = i.goods_id AND COALESCE(g.is_del, 0) = 0 " +
                " INNER JOIN app_sale_order o ON o.id = i.order_id AND o.is_del = 0 AND o.status = 1 " +
                " WHERE i.is_del = 0" + goodsFilter +
                " UNION ALL " +
                " SELECT 3 AS bill_type, i.id AS item_id, i.goods_id, i.profit_loss_quantity AS quantity, " +
                "        i.unit_price, i.profit_loss_amount AS total_amount, 0 AS is_init, " +
                "        o.create_time AS bill_time, 0 AS order_type " +
                " FROM app_stock_check_item i " +
                " INNER JOIN app_goods g ON g.id = i.goods_id AND COALESCE(g.is_del, 0) = 0 " +
                " INNER JOIN app_stock_check o ON o.id = i.check_id AND o.is_del = 0 " +
                " WHERE i.is_del = 0" + goodsFilter +
                ") t";
        List<Object> params = new ArrayList<>(filterParams);
        params.addAll(filterParams);
        params.addAll(filterParams);
        return jdbcTemplate.queryForList(sql, params.toArray());
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
}




