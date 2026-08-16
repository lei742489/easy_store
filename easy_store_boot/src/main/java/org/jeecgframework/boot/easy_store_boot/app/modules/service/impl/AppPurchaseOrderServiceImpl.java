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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(AppPurchaseOrder entity){
        setUnpaidAmount(entity);
        boolean flag = super.save(entity);
        setItemsByEntity(entity);
        if(!entity.getItems().isEmpty())
             appPurchaseOrderItemService.saveBatch(entity.getItems());

        if (isActive(entity)) {
            appPurchaseOrderItemService.batchUpdateGoodsStore(entity.getItems());
            updateAccountSettle(entity, -1);
            appSupplierService.updatePayable(entity.getSupplierId());
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(AppPurchaseOrder entity){
        entity.setOrderNo(null);

        setItemsByEntity(entity);
        setUnpaidAmount(entity);
        List<AppPurchaseOrderItem> oldItems = appPurchaseOrderItemService.listByOrderId(entity.getId());
        AppPurchaseOrder oldOrder = getById(entity.getId());
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
        appPurchaseOrderItemService.batchUpdateGoodsStore(oldItems);
        appPurchaseOrderItemService.batchUpdateGoodsStore(entity.getItems());

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

        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id){
        AppPurchaseOrder db = getById(id);
        boolean flag = super.removeById(id);
        if(flag && db!=null){
            appPurchaseOrderItemService.removeByOrderId(db.getId());
            if (isActive(db)) {
                updateAccountSettle(db, 1);
            }
            appSupplierService.updatePayable(db.getSupplierId());
        }
        return flag;
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

        QueryWrapper<AppPurchaseOrder> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(payable_amount), 0.00) as total").eq("supplier_id", supplierId).eq("status", 1);
        Map<String,Object> map= getMap(wrapper);
        return Double.parseDouble(map.get("total").toString());
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
        AppGoods goods = findGoods(item.getGoodsId(), item.getGoodsName());
        if(goods == null) {
            goods = createGoodsFromPurchaseItem(entity, item);
        }
        item.setGoodsId(goods.getId().toString());
        item.setUnit(appUnitService.normalizeName(item.getUnit()));
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

    private AppGoods createGoodsFromPurchaseItem(AppPurchaseOrder entity, AppPurchaseOrderItem item) {
        if(StringUtils.isBlank(item.getGoodsName())) {
            throw new AppRunTimeException("请输入商品名称");
        }
        AppGoods goods = new AppGoods();
        String unitName = appUnitService.normalizeName(item.getUnit());
        goods.setTitle(item.getGoodsName().trim());
        goods.setSupplierTitle(item.getGoodsName().trim());
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




