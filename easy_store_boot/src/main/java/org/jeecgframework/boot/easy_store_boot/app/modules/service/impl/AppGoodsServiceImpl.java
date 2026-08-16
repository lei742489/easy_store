package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.alibaba.fastjson.JSONObject;
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
import java.util.ArrayList;
import java.util.Collections;
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
            appGoods.setStock(p - s);
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
                        ") t ORDER BY CASE WHEN bill_time IS NULL THEN 0 ELSE 1 END, bill_time, item_id",
                goodsId, goodsId);
        BigDecimal stockQty = BigDecimal.ZERO;
        BigDecimal stockCost = BigDecimal.ZERO;
        BigDecimal costPrice = BigDecimal.ZERO;

        for (Map<String, Object> row : rows) {
            BigDecimal quantity = decimal(row.get("quantity"));
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Integer billType = integer(row.get("bill_type"));
            if (billType != null && billType == 1) {
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




