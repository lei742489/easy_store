package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.DoubleUtil;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSaleOrder;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSaleOrderItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppAccountSettleService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppReceivePaymentAmountItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSaleOrderItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSaleOrderService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUnitService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppSaleOrderMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
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

/**
* @author Administrator
* @description 针对表【app_sale_order】的数据库操作Service实现
* @createDate 2025-07-09 09:46:47
*/
@Service
public class AppSaleOrderServiceImpl extends ServiceImpl<AppSaleOrderMapper, AppSaleOrder>
    implements IAppSaleOrderService {

    @Autowired
    private IAppSaleOrderItemService appSaleOrderItemService;
    @Autowired
    private IAppGoodsService appGoodsService;
    @Autowired
    private IAppUnitService appUnitService;
    @Autowired
    private IAppReceivePaymentAmountItemService receivePaymentAmountItemService;
    @Autowired
    private IAppAccountSettleService accountSettleService;
    @Autowired
    @Lazy
    public IAppCustomerService appCustomerService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(AppSaleOrder entity){
        setUnpaidAmount(entity);
        List<PendingGoodsDraft> pendingGoodsDrafts = setItemsByEntity(entity);
        boolean flag = super.save(entity);
        entity.getItems().forEach(item -> item.setOrderId(entity.getId()));
        if(!entity.getItems().isEmpty()) {
            appSaleOrderItemService.saveBatch(entity.getItems());
        }
        refreshPendingGoods(entity.getId(), entity.getItems(), pendingGoodsDrafts, isActive(entity));

        if (isActive(entity)) {
            appSaleOrderItemService.batchUpdateGoodsStore(entity.getItems());
            updateAccountSettle(entity, 1);
            appCustomerService.updatePayable(entity.getCustomerId());
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(AppSaleOrder entity) {
        entity.setOrderNo(null);

        fillPendingGoodsNames(entity.getId(), entity.getItems());
        List<PendingGoodsDraft> pendingGoodsDrafts = setItemsByEntity(entity);
        setUnpaidAmount(entity);
        List<AppSaleOrderItem> oldItems = appSaleOrderItemService.listByOrderId(entity.getId());
        AppSaleOrder oldOrder = getById(entity.getId());
        boolean flag = super.updateById(entity);
        entity.getItems().forEach(item->{
            if(entity.getOrderType()==2){
                item.setQuantity(-Math.abs(item.getQuantity()));
            }else{
                item.setQuantity(Math.abs(item.getQuantity()));
            }
        });
        appSaleOrderItemService.removeByUpdate(entity.getItems(),entity.getId());
        if(!entity.getItems().isEmpty()){
            appSaleOrderItemService.saveOrUpdateBatch(entity.getItems());
        }
        refreshPendingGoods(entity.getId(), entity.getItems(), pendingGoodsDrafts, isActive(entity));
        appSaleOrderItemService.batchUpdateGoodsStore(oldItems);
        appSaleOrderItemService.batchUpdateGoodsStore(entity.getItems());

        if (isActive(oldOrder)) {
            updateAccountSettle(oldOrder, -1);
        }
        if (isActive(entity)) {
            updateAccountSettle(entity, 1);
        }
        appCustomerService.updatePayable(entity.getCustomerId());
        if (oldOrder != null && oldOrder.getCustomerId() != null && !oldOrder.getCustomerId().equals(entity.getCustomerId())) {
            appCustomerService.updatePayable(oldOrder.getCustomerId());
        }

        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public  boolean removeById(Serializable id){
        AppSaleOrder db = getById(id);
        boolean flag = super.removeById(id);
        if(flag && db!=null){
            appSaleOrderItemService.removeByOrderId(db.getId());
            deletePendingGoods(db.getId());
            if (isActive(db)) {
                updateAccountSettle(db, -1);
            }
            appCustomerService.updatePayable(db.getCustomerId());
        }
        return flag;
    }

    @Override
    public Double sumUnpaidAmountByCustomer(String customerId) {
        if(customerId == null)
            return 0.0;
        QueryWrapper<AppSaleOrder> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(unpaid_amount), 0.00) as total").eq("customer_id", customerId).eq("status", 1);
        Map<String,Object> map= getMap(wrapper);
        return Double.parseDouble(map.get("total").toString());

    }

    @Override
    public Double sumPayableAmountByCustomer(String customerId) {
        if(customerId == null)
            return 0.0;
        QueryWrapper<AppSaleOrder> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(payable_amount), 0.00) as total").eq("customer_id", customerId).eq("status", 1);
        Map<String,Object> map= getMap(wrapper);
        return Double.parseDouble(map.get("total").toString());
    }

    @Override
    public AppSaleOrder getByOrderNo(String orderNo) {
        return getOne(new QueryWrapper<AppSaleOrder>().eq("order_no", orderNo).last("limit 1"));
    }

    @Override
    public void updatePayAmount(String orderNo, Double payAmount) {
        AppSaleOrder order = getByOrderNo(orderNo);
        if(order==null) return;
        Double paidAmount = receivePaymentAmountItemService.sumAmountByOrderNo(orderNo);
        Double payableAmount = order.getPayableAmount() == null ? 0.0 : order.getPayableAmount();
        order.setPaidAmount(paidAmount);
        order.setUnpaidAmount(DoubleUtil.sub(payableAmount, paidAmount));
        super.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculateGrossProfit(List<Integer> ids) {
        if(ids == null || ids.isEmpty()) return;
        Set<String> goodsIds = new HashSet<>();
        for(Integer id : ids) {
            AppSaleOrder order = getById(id);
            if(order == null) continue;
            List<AppSaleOrderItem> items = appSaleOrderItemService.listByOrderId(order.getId());
            fillPendingGoodsNames(order.getId(), items);
            order.setItems(items);
            setItemsByEntity(order);
            if(!items.isEmpty()) {
                appSaleOrderItemService.updateBatchById(items);
                for(AppSaleOrderItem item : items) {
                    if(item != null && StringUtils.isNotBlank(item.getGoodsId()) && StringUtils.isNumeric(item.getGoodsId().trim())) {
                        goodsIds.add(item.getGoodsId().trim());
                    }
                }
            }
            refreshPendingGoods(order.getId(), items, new ArrayList<>(), isActive(order));
            super.updateById(order);
        }
        for(String goodsId : goodsIds) {
            appGoodsService.updateStock(goodsId);
        }
    }

    @Override
    public void fillPendingGoodsNames(Integer orderId, List<AppSaleOrderItem> items) {
        if(orderId == null || items == null || items.isEmpty()) return;
        Map<Integer, String> pendingNames = queryPendingGoodsNameMap(orderId);
        if(pendingNames.isEmpty()) return;
        for(AppSaleOrderItem item : items) {
            if(item == null || item.getId() == null) continue;
            String pendingName = pendingNames.get(item.getId());
            if(StringUtils.isNotBlank(pendingName) && StringUtils.isBlank(item.getGoodsName())) {
                item.setGoodsName(pendingName);
            }
        }
    }

    private List<PendingGoodsDraft> setItemsByEntity(AppSaleOrder entity){
        List<PendingGoodsDraft> pendingGoodsDrafts = new ArrayList<>();
        if(entity == null) return pendingGoodsDrafts;
        if( entity.getItems()==null) {
            entity.setItems(new ArrayList<>());
        }else{
            BigDecimal totalGrossProfit = BigDecimal.ZERO;
            for(AppSaleOrderItem item:entity.getItems()){
                AppGoods goods = resolveSaleOrderItemGoodsForSave(entity, item);
                if(goods == null && !isActive(entity) && StringUtils.isNotBlank(item.getGoodsName())) {
                    pendingGoodsDrafts.add(new PendingGoodsDraft(item, item.getGoodsName().trim()));
                }
                totalGrossProfit = totalGrossProfit.add(calculateItemGrossProfit(entity, item, goods));
                item.setOrderId(entity.getId());
            }
            entity.setGrossProfit(money(totalGrossProfit));
        }
        return pendingGoodsDrafts;
    }

    private AppGoods resolveSaleOrderItemGoodsForSave(AppSaleOrder entity, AppSaleOrderItem item) {
        if(item == null) return null;
        if(isEmptyCategory(item.getCategoryId())) {
            throw new AppRunTimeException("\u6240\u6709\u5546\u54c1\u5fc5\u987b\u9009\u62e9\u5206\u7c7b");
        }
        String goodsName = resolveItemGoodsName(item);
        String unitName = appUnitService.normalizeName(item.getUnit());
        item.setUnit(unitName);
        appUnitService.updateByName(unitName);

        AppGoods goods = findGoodsForSale(item.getGoodsId(), goodsName);
        if(goods == null) {
            if(StringUtils.isBlank(goodsName)) {
                throw new AppRunTimeException("\u8bf7\u8f93\u5165\u5546\u54c1\u540d\u79f0");
            }
            if(isActive(entity)) {
                goods = createGoodsFromSaleOrderItem(item, goodsName, unitName);
            } else {
                item.setGoodsId(null);
                item.setGoodsName(goodsName);
                return null;
            }
        }
        item.setGoodsId(goods.getId().toString());
        item.setGoodsName(goods.getTitle());
        return goods;
    }

    private void refreshPendingGoods(Integer orderId, List<AppSaleOrderItem> items,
                                     List<PendingGoodsDraft> pendingGoodsDrafts, boolean activeOrder) {
        if(orderId == null) return;
        if(activeOrder) {
            assertItemsLinkedToGoods(items);
            deletePendingGoods(orderId);
            return;
        }
        deletePendingGoods(orderId);
        if(pendingGoodsDrafts == null || pendingGoodsDrafts.isEmpty()) return;
        for(PendingGoodsDraft draft : pendingGoodsDrafts) {
            AppSaleOrderItem item = draft.item;
            if(item == null || item.getId() == null || StringUtils.isBlank(draft.goodsName)) continue;
            jdbcTemplate.update("INSERT INTO app_sale_pending_goods " +
                            "(order_id, order_item_id, goods_name, category_id, unit, unit_price, status, create_time, update_time, is_del) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                    orderId, item.getId(), draft.goodsName, item.getCategoryId(), item.getUnit(), item.getUnitPrice());
        }
    }

    private void deletePendingGoods(Integer orderId) {
        if(orderId == null) return;
        jdbcTemplate.update("DELETE FROM app_sale_pending_goods WHERE order_id = ?", orderId);
    }

    private void assertItemsLinkedToGoods(List<AppSaleOrderItem> items) {
        if(items == null || items.isEmpty()) return;
        for(AppSaleOrderItem item : items) {
            if(item == null) continue;
            String goodsId = item.getGoodsId();
            String goodsName = StringUtils.isBlank(item.getGoodsName()) ? "该" : item.getGoodsName().trim();
            if(StringUtils.isBlank(goodsId) || !StringUtils.isNumeric(goodsId.trim())) {
                throw new AppRunTimeException(goodsName + "商品信息未入库，请先入库");
            }
            if(appGoodsService.getById(goodsId.trim()) == null) {
                throw new AppRunTimeException(goodsName + "商品信息未入库，请先入库");
            }
        }
    }

    private Map<Integer, String> queryPendingGoodsNameMap(Integer orderId) {
        Map<Integer, String> result = new HashMap<>();
        if(orderId == null) return result;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT order_item_id, goods_name FROM app_sale_pending_goods " +
                        "WHERE order_id = ? AND COALESCE(is_del, 0) = 0 ORDER BY id",
                orderId);
        for(Map<String, Object> row : rows) {
            Object itemIdObj = row.get("order_item_id");
            Object goodsNameObj = row.get("goods_name");
            if(itemIdObj == null || goodsNameObj == null) continue;
            result.put(Integer.valueOf(itemIdObj.toString()), goodsNameObj.toString());
        }
        return result;
    }

    private static class PendingGoodsDraft {
        private final AppSaleOrderItem item;
        private final String goodsName;

        private PendingGoodsDraft(AppSaleOrderItem item, String goodsName) {
            this.item = item;
            this.goodsName = goodsName;
        }
    }

    private AppGoods resolveSaleOrderItemGoods(AppSaleOrderItem item) {
        if(item == null) return null;
        if(isEmptyCategory(item.getCategoryId())) {
            throw new AppRunTimeException("所有商品必须选择分类");
        }
        AppGoods goods = findGoods(item.getGoodsId(), item.getGoodsName());
        if(goods == null) {
            String goodsName = StringUtils.isBlank(item.getGoodsName()) ? "该" : item.getGoodsName().trim();
            throw new AppRunTimeException(goodsName + "商品信息未入库，请先入库");
        }
        item.setGoodsId(goods.getId().toString());
        item.setUnit(appUnitService.normalizeName(item.getUnit()));
        return goods;
    }

    private AppGoods findGoods(String goodsId, String goodsName) {
        if(StringUtils.isNotBlank(goodsName)) {
            return appGoodsService.getOne(new LambdaQueryWrapper<AppGoods>()
                    .eq(AppGoods::getTitle, goodsName.trim())
                    .last("limit 1"));
        }
        if(StringUtils.isNotBlank(goodsId)) {
            return appGoodsService.getById(goodsId);
        }
        return null;
    }

    private String resolveItemGoodsName(AppSaleOrderItem item) {
        if(item == null) return "";
        if(StringUtils.isNotBlank(item.getGoodsName())) {
            return item.getGoodsName().trim();
        }
        String goodsId = item.getGoodsId();
        if(StringUtils.isNotBlank(goodsId) && !StringUtils.isNumeric(goodsId.trim())) {
            item.setGoodsId(null);
            return goodsId.trim();
        }
        return "";
    }

    private AppGoods findGoodsForSale(String goodsId, String goodsName) {
        if(StringUtils.isNotBlank(goodsId) && StringUtils.isNumeric(goodsId.trim())) {
            AppGoods goods = appGoodsService.getById(goodsId.trim());
            if(goods != null) return goods;
        }
        if(StringUtils.isNotBlank(goodsName)) {
            return appGoodsService.getOne(new LambdaQueryWrapper<AppGoods>()
                    .eq(AppGoods::getTitle, goodsName.trim())
                    .last("limit 1"));
        }
        return null;
    }

    private AppGoods createGoodsFromSaleOrderItem(AppSaleOrderItem item, String goodsName, String unitName) {
        AppGoods goods = new AppGoods();
        goods.setTitle(goodsName);
        goods.setSupplierTitle(goodsName);
        goods.setCategoryId(item.getCategoryId());
        goods.setUnit(unitName);
        goods.setSalePrc(item.getUnitPrice());
        goods.setTradePrc(item.getUnitPrice());
        goods.setPurPrc(0.0);
        goods.setInitCost(0);
        goods.setStock(0);
        goods.setStatus(1);
        appGoodsService.save(goods);
        return goods;
    }

    private boolean isEmptyCategory(String categoryId) {
        return StringUtils.isBlank(categoryId) || "0".equals(categoryId.trim());
    }

    private BigDecimal calculateItemGrossProfit(AppSaleOrder entity, AppSaleOrderItem item, AppGoods goods) {
        BigDecimal discountRate = decimal(entity == null ? null : entity.getDiscountRate());
        if(discountRate.compareTo(BigDecimal.ZERO) == 0) {
            discountRate = new BigDecimal("100");
        }
        BigDecimal saleAmount = decimal(item.getTotalAmount())
                .multiply(discountRate)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal costAmount = decimal(item.getQuantity()).multiply(resolveCostPrice(goods));
        BigDecimal grossProfit = saleAmount.subtract(costAmount);
        item.setGrossProfit(money(grossProfit));
        return grossProfit;
    }

    private BigDecimal resolveCostPrice(AppGoods goods) {
        if(goods == null) return BigDecimal.ZERO;
        BigDecimal costPrice = decimal(goods.getCostPrice());
        Integer stock = goods.getStock();
        if(costPrice.compareTo(BigDecimal.ZERO) != 0 || stock == null || stock > 0) {
            return costPrice;
        }
        BigDecimal purchasePrice = decimal(goods.getPurPrc());
        if(purchasePrice.compareTo(BigDecimal.ZERO) != 0) {
            return purchasePrice;
        }
        return decimal(goods.getInitCost());
    }

    private Double money(BigDecimal value) {
        if(value == null) return 0.0;
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal decimal(Number value) {
        if(value == null) return BigDecimal.ZERO;
        return new BigDecimal(value.toString());
    }

    private void setUnpaidAmount(AppSaleOrder entity){
        if(entity == null) return;
        if(entity.getPaidAmount() ==null) entity.setPaidAmount(0.00);
        if(entity.getPayableAmount() ==null) entity.setPayableAmount(0.00);
        entity.setUnpaidAmount(DoubleUtil.sub(entity.getPayableAmount(),entity.getPaidAmount()));

    }

    private void updateAccountSettle(AppSaleOrder entity, int direction) {
        if(entity == null || entity.getSettleId() == null || entity.getPaidAmount() == null) return;
        if(Math.abs(entity.getPaidAmount()) < 0.000001) return;
        accountSettleService.updateCurPrc(entity.getSettleId(), entity.getPaidAmount() * direction);
    }

    private boolean isActive(AppSaleOrder entity) {
        return entity != null && (entity.getStatus() == null || entity.getStatus() == 1);
    }
}




