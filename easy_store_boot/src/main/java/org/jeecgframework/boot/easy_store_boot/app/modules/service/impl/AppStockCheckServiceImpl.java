package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppStockCheck;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppStockCheckItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppStockCheckMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppStockCheckItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppStockCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class AppStockCheckServiceImpl extends ServiceImpl<AppStockCheckMapper, AppStockCheck>
        implements IAppStockCheckService {

    @Autowired
    private IAppStockCheckItemService stockCheckItemService;
    @Autowired
    private IAppGoodsService goodsService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(AppStockCheck entity) {
        normalizeItems(entity, new HashMap<>());
        goodsService.lockGoodsForUpdate(collectGoodsIds(entity.getItems(), null));
        boolean saved = super.save(entity);
        for (AppStockCheckItem item : entity.getItems()) {
            item.setCheckId(entity.getId());
        }
        stockCheckItemService.saveBatch(entity.getItems());
        refreshGoods(entity.getItems());
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(AppStockCheck entity) {
        if (entity == null || entity.getId() == null) {
            throw new AppRunTimeException("盘点单不存在或已删除");
        }
        AppStockCheck oldCheck = getById(entity.getId());
        if (oldCheck == null) {
            throw new AppRunTimeException("盘点单不存在或已删除");
        }
        List<AppStockCheckItem> oldItems = stockCheckItemService.listByCheckId(entity.getId());
        Map<Integer, AppStockCheckItem> oldItemMap = new HashMap<>();
        for (AppStockCheckItem item : oldItems) {
            oldItemMap.put(item.getId(), item);
        }
        entity.setOrderNo(null);
        normalizeItems(entity, oldItemMap);
        goodsService.lockGoodsForUpdate(collectGoodsIds(oldItems, entity.getItems()));
        boolean updated = super.updateById(entity);
        stockCheckItemService.removeByUpdate(entity.getItems(), entity.getId());
        stockCheckItemService.saveOrUpdateBatch(entity.getItems());

        List<AppStockCheckItem> affectedItems = new ArrayList<>(oldItems);
        affectedItems.addAll(entity.getItems());
        refreshGoods(affectedItems);
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        AppStockCheck stockCheck = getById(id);
        if (stockCheck == null) {
            return false;
        }
        List<AppStockCheckItem> oldItems = stockCheckItemService.listByCheckId(stockCheck.getId());
        goodsService.lockGoodsForUpdate(collectGoodsIds(oldItems, null));
        boolean removed = super.removeById(id);
        if (removed) {
            stockCheckItemService.removeByCheckId(stockCheck.getId());
            refreshGoods(oldItems);
        }
        return removed;
    }

    private void normalizeItems(AppStockCheck entity, Map<Integer, AppStockCheckItem> oldItemMap) {
        if (entity == null || entity.getItems() == null || entity.getItems().isEmpty()) {
            throw new AppRunTimeException("请至少录入一项盘点商品");
        }
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        Set<String> goodsIds = new HashSet<>();
        for (AppStockCheckItem item : entity.getItems()) {
            AppGoods goods = resolveGoods(item);
            String goodsId = goods.getId().toString();
            if (!goodsIds.add(goodsId)) {
                throw new AppRunTimeException("同一商品只能录入一次");
            }
            AppStockCheckItem oldItem = item.getId() == null ? null : oldItemMap.get(item.getId());
            boolean sameGoods = oldItem != null && StringUtils.equals(oldItem.getGoodsId(), goodsId);
            double bookQuantity = sameGoods
                    ? safeDouble(oldItem.getBookQuantity())
                    : safeDouble(goods.getStock());
            BigDecimal unitPrice = sameGoods
                    ? decimal(oldItem.getUnitPrice())
                    : decimal(goods.getCostPrice());
            double actualQuantity = safeDouble(item.getActualQuantity());
            double profitLossQuantity = actualQuantity - bookQuantity;
            BigDecimal profitLossAmount = unitPrice.multiply(BigDecimal.valueOf(profitLossQuantity));

            item.setCheckId(entity.getId());
            item.setGoodsId(goodsId);
            item.setUnit(goods.getUnit());
            item.setBookQuantity(bookQuantity);
            item.setActualQuantity(actualQuantity);
            item.setProfitLossQuantity(profitLossQuantity);
            item.setUnitPrice(unitPrice.setScale(4, RoundingMode.HALF_UP).doubleValue());
            item.setProfitLossAmount(money(profitLossAmount));
            totalQuantity = totalQuantity.add(BigDecimal.valueOf(profitLossQuantity));
            totalAmount = totalAmount.add(profitLossAmount);
        }
        entity.setProfitLossQuantity(money(totalQuantity));
        entity.setProfitLossAmount(money(totalAmount));
    }

    private AppGoods resolveGoods(AppStockCheckItem item) {
        if (item == null || StringUtils.isEmpty(item.getGoodsId())) {
            throw new AppRunTimeException("请选择盘点商品");
        }
        AppGoods goods = goodsService.getById(item.getGoodsId());
        if (goods == null) {
            throw new AppRunTimeException("盘点商品不存在或已删除");
        }
        return goods;
    }

    private void refreshGoods(List<AppStockCheckItem> items) {
        Set<String> goodsIds = new HashSet<>();
        for (AppStockCheckItem item : items) {
            if (item != null && StringUtils.isNotEmpty(item.getGoodsId())) {
                goodsIds.add(item.getGoodsId());
            }
        }
        for (String goodsId : goodsIds) {
            goodsService.updateStock(goodsId);
        }
    }

    private Set<String> collectGoodsIds(List<AppStockCheckItem> first, List<AppStockCheckItem> second) {
        Set<String> goodsIds = new TreeSet<>((left, right) -> Integer.compare(
                Integer.parseInt(left), Integer.parseInt(right)));
        addGoodsIds(goodsIds, first);
        addGoodsIds(goodsIds, second);
        return goodsIds;
    }

    private void addGoodsIds(Set<String> goodsIds, List<AppStockCheckItem> items) {
        if (items == null) return;
        for (AppStockCheckItem item : items) {
            if (item != null && StringUtils.isNotBlank(item.getGoodsId())) {
                goodsIds.add(item.getGoodsId().trim());
            }
        }
    }

    private double safeDouble(Double value) {
        return value == null ? 0D : value;
    }

    private BigDecimal decimal(Number value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private Double money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
