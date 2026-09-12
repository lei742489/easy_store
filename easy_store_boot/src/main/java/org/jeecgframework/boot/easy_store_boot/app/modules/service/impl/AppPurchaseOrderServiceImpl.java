package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.DoubleUtil;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrder;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrderItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPurchaseOrderItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPurchaseOrderService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppPurchaseOrderMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppAccountSettleService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPaymentAmountItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSupplierService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* @author Administrator
* @description 针对表【app_purchase_order】的数据库操作Service实现
* @createDate 2025-06-30 13:38:41
*/
@Service
@EnableTransactionManagement
public class AppPurchaseOrderServiceImpl extends ServiceImpl<AppPurchaseOrderMapper, AppPurchaseOrder>
    implements IAppPurchaseOrderService {

    @Autowired
    private IAppPurchaseOrderItemService appPurchaseOrderItemService;
    @Autowired
    private IAppGoodsService appGoodsService;
    @Autowired
    private IAppUnitService appUnitService;
    @Autowired
    private IAppPaymentAmountItemService paymentAmountItemService;
    @Autowired
    private IAppAccountSettleService accountSettleService;
    @Autowired
    @Lazy
    public IAppSupplierService appSupplierService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AppBusinessDailySummaryService dailySummaryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(AppPurchaseOrder entity){
        setUnpaidAmount(entity);
        setItemsByEntity(entity);
        appGoodsService.lockGoodsForUpdate(collectGoodsIds(entity.getItems(), null));
        boolean flag = super.save(entity);
        entity.getItems().forEach(item -> item.setOrderId(entity.getId()));
        if(!entity.getItems().isEmpty())
             appPurchaseOrderItemService.saveBatch(entity.getItems());

        if (isActive(entity)) {
            appPurchaseOrderItemService.batchUpdateGoodsStore(entity.getItems());
            updateAccountSettle(entity, -1);
            appSupplierService.updatePayable(entity.getSupplierId());
        }
        AppPurchaseOrder savedOrder = entity.getCreateTime() == null ? getById(entity.getId()) : entity;
        dailySummaryService.refreshPurchaseDate(savedOrder == null ? null : savedOrder.getCreateTime());
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(AppPurchaseOrder entity){
        entity.setOrderNo(null);

        List<AppPurchaseOrderItem> oldItems = appPurchaseOrderItemService.listByOrderId(entity.getId());
        AppPurchaseOrder oldOrder = getById(entity.getId());
        setItemsByEntity(entity);
        setUnpaidAmount(entity);
        appGoodsService.lockGoodsForUpdate(collectGoodsIds(oldItems, entity.getItems()));
        boolean flag = super.updateById(entity);
        entity.getItems().forEach(item->{
            if(entity.getOrderType()==2){
                item.setQuantity(-Math.abs(item.getQuantity()));
            }else{
                item.setQuantity(Math.abs(item.getQuantity()));
            }
        });
        appPurchaseOrderItemService.removeByUpdate(entity.getItems(),entity.getId());
        if(!entity.getItems().isEmpty()){
            appPurchaseOrderItemService.saveOrUpdateBatch(entity.getItems());
        }
        List<AppPurchaseOrderItem> affectedItems = new ArrayList<>(oldItems);
        affectedItems.addAll(entity.getItems());
        appPurchaseOrderItemService.batchUpdateGoodsStore(affectedItems);

        if (isActive(oldOrder)) {
            updateAccountSettle(oldOrder, 1);
        }
        if (isActive(entity)) {
            updateAccountSettle(entity, -1);
        }
        appSupplierService.updatePayable(entity.getSupplierId());
        if (oldOrder != null && oldOrder.getSupplierId() != null && !oldOrder.getSupplierId().equals(entity.getSupplierId())) {
            appSupplierService.updatePayable(oldOrder.getSupplierId());
        }

        AppPurchaseOrder currentOrder = getById(entity.getId());
        dailySummaryService.refreshDates(
                oldOrder == null ? null : oldOrder.getCreateTime(),
                currentOrder == null ? entity.getCreateTime() : currentOrder.getCreateTime());
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id){
        AppPurchaseOrder db = getById(id);
        List<AppPurchaseOrderItem> oldItems = db == null ? new ArrayList<>() : appPurchaseOrderItemService.listByOrderId(db.getId());
        appGoodsService.lockGoodsForUpdate(collectGoodsIds(oldItems, null));
        boolean flag = super.removeById(id);
        if(flag && db!=null){
            appPurchaseOrderItemService.removeByOrderId(db.getId());
            if (isActive(db)) {
                updateAccountSettle(db, 1);
            }
            appSupplierService.updatePayable(db.getSupplierId());
            dailySummaryService.refreshPurchaseDate(db.getCreateTime());
        }
        return flag;
    }

    private Set<String> collectGoodsIds(List<AppPurchaseOrderItem> first, List<AppPurchaseOrderItem> second) {
        Set<String> goodsIds = new HashSet<>();
        addGoodsIds(goodsIds, first);
        addGoodsIds(goodsIds, second);
        return goodsIds;
    }

    private void addGoodsIds(Set<String> goodsIds, List<AppPurchaseOrderItem> items) {
        if (items == null) return;
        for (AppPurchaseOrderItem item : items) {
            if (item != null && StringUtils.isNotBlank(item.getGoodsId())) {
                goodsIds.add(item.getGoodsId().trim());
            }
        }
    }

    @Override
    public Double sumUnpaidAmountBySupplier(String supplierId) {
        if(supplierId == null)
            return 0.0;

        QueryWrapper<AppPurchaseOrder> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(unpaid_amount), 0.00) as total").eq("supplier_id", supplierId).eq("status", 1);
        Map<String,Object> map= getMap(wrapper);
        return Double.parseDouble(map.get("total").toString());
    }

    @Override
    public Double sumPayableAmountBySupplier(String supplierId) {
        if(supplierId == null)
            return 0.0;
        String debtAmountSql =
                "CASE WHEN COALESCE(payment_item.linked_amount, 0) > 0 " +
                        "THEN COALESCE(app_purchase_order.payable_amount, 0) " +
                        "ELSE COALESCE(app_purchase_order.unpaid_amount, " +
                        "COALESCE(app_purchase_order.payable_amount, 0) - " +
                        "COALESCE(app_purchase_order.paid_amount, 0)) END";
        String sql = "SELECT COALESCE(SUM(" + debtAmountSql + "), 0) " +
                "FROM app_purchase_order " +
                "LEFT JOIN (SELECT order_no, SUM(COALESCE(amount, 0)) AS linked_amount " +
                "FROM app_payment_amount_item " +
                "WHERE order_id IN (SELECT id FROM app_payment_voucher " +
                "WHERE status = 1 AND COALESCE(is_del, 0) = 0) GROUP BY order_no) payment_item " +
                "ON app_purchase_order.order_no = payment_item.order_no " +
                "WHERE app_purchase_order.supplier_id = ? " +
                "AND app_purchase_order.status = 1 " +
                "AND COALESCE(app_purchase_order.is_del, 0) = 0 " +
                "AND (COALESCE(payment_item.linked_amount, 0) > 0 " +
                "OR ABS(COALESCE(app_purchase_order.payable_amount, 0) - " +
                "COALESCE(app_purchase_order.paid_amount, 0)) >= 0.005) " +
                "AND ABS(" + debtAmountSql + ") >= 0.005";
        Number total = jdbcTemplate.queryForObject(sql, Number.class, supplierId);
        return total == null ? 0.0 : total.doubleValue();
    }

    @Override
    public AppPurchaseOrder getByOrderNo(String orderNo) {
        return getOne(new QueryWrapper<AppPurchaseOrder>().eq("order_no", orderNo).last("limit 1"));
    }


    private void  setItemsByEntity(AppPurchaseOrder entity){
        if(entity == null) return;
        if( entity.getItems()==null) {
            entity.setItems(new ArrayList<>());
        }else{
            for(AppPurchaseOrderItem item:entity.getItems()){
                resolvePurchaseOrderItemGoods(entity, item);
                item.setOrderId(entity.getId());
            }
        }

    }

    private void resolvePurchaseOrderItemGoods(AppPurchaseOrder entity, AppPurchaseOrderItem item) {
        if(item == null) return;
        if(isEmptyCategory(item.getCategoryId())) {
            throw new AppRunTimeException("所有商品必须选择分类");
        }
        AppGoods goods = findGoods(item.getGoodsId(), item.getGoodsName(), item.getGoodsCode());
        if(goods == null) {
            goods = createGoodsFromPurchaseItem(entity, item);
        }
        syncGoodsCode(goods, item.getGoodsCode());
        item.setGoodsId(goods.getId().toString());
        item.setUnit(appUnitService.normalizeName(item.getUnit()));
    }

    private AppGoods findGoods(String goodsId, String goodsName, String goodsCode) {
        if(StringUtils.isNotBlank(goodsCode)) {
            AppGoods goods = appGoodsService.getOne(new LambdaQueryWrapper<AppGoods>()
                    .eq(AppGoods::getGoodsCode, goodsCode.trim())
                    .last("limit 1"));
            if(goods != null) return goods;
        }
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

    private AppGoods createGoodsFromPurchaseItem(AppPurchaseOrder entity, AppPurchaseOrderItem item) {
        if(StringUtils.isBlank(item.getGoodsName())) {
            throw new AppRunTimeException("请输入商品名称");
        }
        AppGoods goods = new AppGoods();
        String unitName = appUnitService.normalizeName(item.getUnit());
        goods.setTitle(item.getGoodsName().trim());
        goods.setSupplierTitle(item.getGoodsName().trim());
        goods.setGoodsCode(StringUtils.trimToNull(item.getGoodsCode()));
        goods.setCategoryId(item.getCategoryId());
        goods.setUnit(unitName);
        goods.setPurPrc(item.getUnitPrice());
        //goods.setTradePrc(item.getUnitPrice());
        goods.setSupplierId(entity == null ? null : entity.getSupplierId());
        goods.setStatus(1);
        appGoodsService.save(goods);
        item.setUnit(unitName);
        return goods;
    }

    private void syncGoodsCode(AppGoods goods, String goodsCode) {
        if(goods == null || StringUtils.isBlank(goodsCode)) return;
        String normalizedCode = goodsCode.trim();
        if(StringUtils.equals(normalizedCode, goods.getGoodsCode())) return;
        goods.setGoodsCode(normalizedCode);
        appGoodsService.updateById(goods);
    }

    private boolean isEmptyCategory(String categoryId) {
        return StringUtils.isBlank(categoryId) || "0".equals(categoryId.trim());
    }

    @Override
    public void updatePayAmount(String orderNo, Double payAmount) {
        AppPurchaseOrder purchaseOrder = getByOrderNo(orderNo);
        if(purchaseOrder==null) return;
        Double paidAmount = paymentAmountItemService.sumAmountByOrderNo(orderNo);
        Double payableAmount = purchaseOrder.getPayableAmount() == null ? 0.0 : purchaseOrder.getPayableAmount();
        purchaseOrder.setPaidAmount(paidAmount);
        purchaseOrder.setUnpaidAmount(DoubleUtil.sub(payableAmount, paidAmount));
        super.updateById(purchaseOrder);
    }

    private void setUnpaidAmount(AppPurchaseOrder entity){
        if(entity == null) return;
        if(entity.getPaidAmount() ==null) entity.setPaidAmount(0.00);
        if(entity.getPayableAmount() ==null) entity.setPayableAmount(0.00);
        entity.setUnpaidAmount(DoubleUtil.sub(entity.getPayableAmount(),entity.getPaidAmount()));

    }

    private void updateAccountSettle(AppPurchaseOrder entity, int direction) {
        if(entity == null || entity.getSettleId() == null || entity.getPaidAmount() == null) return;
        if(Math.abs(entity.getPaidAmount()) < 0.000001) return;
        accountSettleService.updateCurPrc(entity.getSettleId(), entity.getPaidAmount() * direction);
    }

    private boolean isActive(AppPurchaseOrder entity) {
        return entity != null && (entity.getStatus() == null || entity.getStatus() == 1);
    }
}
