package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.common.DoubleUtil;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.*;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.*;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppPaymentVoucherMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.*;

/**
* @author Administrator
* @description 针对表【app_payment_voucher】的数据库操作Service实现
* @createDate 2025-07-07 10:00:26
*/
@Service
@EnableTransactionManagement
public class AppPaymentVoucherServiceImpl extends ServiceImpl<AppPaymentVoucherMapper, AppPaymentVoucher>
    implements IAppPaymentVoucherService {

    @Autowired
    private IAppPaymentSettleItemService settleItemService;
    @Autowired
    private IAppAccountSettleService accountSettleService;
    @Autowired
    private IAppSupplierService appSupplierService;
    @Autowired
    private IAppPaymentAmountItemService paymentAmountItemService;
    @Autowired
    private IAppPurchaseOrderService appPurchaseOrderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public  boolean save(AppPaymentVoucher entity){
        boolean flag= super.save(entity);
        if (entity.getSettleItems() == null) {
            entity.setSettleItems(Collections.emptyList());
        }
        entity.getSettleItems().forEach(item->{
            item.setPaymentId(entity.getId());
            item.setCreateTime(new Date());
        });

        updateAmountItem(entity,false);
        settleItemService.saveBatch(entity.getSettleItems());
        for(AppPaymentSettleItem settle : entity.getSettleItems()){
            if (isActive(entity)) {
                updateAccountSettle(settle, -1);
            }
        }

        appSupplierService.updatePayable(entity.getSupplierId());
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(AppPaymentVoucher entity){
        AppPaymentVoucher oldVoucher = getById(entity.getId());
        boolean oldActive = isActive(oldVoucher);
        boolean newActive = isActive(entity);
        boolean flag= super.updateById(entity);
        if (entity.getSettleItems() == null) {
            entity.setSettleItems(Collections.emptyList());
        }
        Set<Integer> settleIds = new HashSet<>();
        for(AppPaymentSettleItem settle : entity.getSettleItems()){
            if(settle.getId()==null){
                settle.setPaymentId(entity.getId());
                settle.setCreateTime(new Date());
                settleItemService.save(settle);
                if (newActive) {
                    updateAccountSettle(settle, -1);
                }
            }else{
                AppPaymentSettleItem db = settleItemService.getById(settle.getId());
                if(db!=null){
                    if (oldActive) {
                        updateAccountSettle(db, 1);
                    }
                    if (newActive) {
                        updateAccountSettle(settle, -1);
                    }
                }
                settleItemService.updateById(settle);
            }
            settleIds.add(settle.getId());
        }

        LambdaQueryWrapper<AppPaymentSettleItem> wrapper = new LambdaQueryWrapper<AppPaymentSettleItem>()
                .eq(AppPaymentSettleItem::getPaymentId,entity.getId());
        if(!settleIds.isEmpty()){
            wrapper.notIn(AppPaymentSettleItem::getId,settleIds);
        }
        List<AppPaymentSettleItem> removeList = settleItemService.list(wrapper);

        for(AppPaymentSettleItem settle : removeList){
            if (oldActive) {
                updateAccountSettle(settle, 1);
            }
            settleItemService.removeById(settle.getId());
        }

        updateAmountItem(entity,true);
        appSupplierService.updatePayable(entity.getSupplierId());
        if (oldVoucher != null && oldVoucher.getSupplierId() != null && !oldVoucher.getSupplierId().equals(entity.getSupplierId())) {
            appSupplierService.updatePayable(oldVoucher.getSupplierId());
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id){
        AppPaymentVoucher db = getById(id);
        boolean flag= super.removeById(id);
        if(flag){
            List<AppPaymentSettleItem> items = settleItemService.listByPaymentId(id.toString());
            for(AppPaymentSettleItem settle : items){
                if (isActive(db)) {
                    updateAccountSettle(settle, 1);
                }
            }
            List<AppPaymentAmountItem> amountItems = paymentAmountItemService.listByOrderId(id.toString());
            paymentAmountItemService.removeByOrderId(id.toString());
            for(AppPaymentAmountItem amountItem : amountItems){
                appPurchaseOrderService.updatePayAmount(amountItem.getOrderNo(),0.00);
            }

            if(db!=null){
                appSupplierService.updatePayable(db.getSupplierId());
            }
        }

        return flag;
    }

    @Override
    public Double sumPaidAmountBySupplier(String supplierId) {
        if(supplierId == null)
            return 0.0;

        QueryWrapper<AppPaymentVoucher> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(amount), 0.00) as total").eq("supplier_id", supplierId).eq("status", 1);
        Map<String,Object> map= getMap(wrapper);
        return Double.parseDouble(map.get("total").toString());
    }


    private void updateAmountItem(AppPaymentVoucher entity,boolean isUpdate){
        Set<String> affectedOrderNos = new HashSet<>();
        Set<Integer> currentIds = new HashSet<>();
        List<AppPaymentAmountItem> dbItems = isUpdate
                ? paymentAmountItemService.listByOrderId(entity.getId().toString())
                : Collections.emptyList();
        Map<Integer, AppPaymentAmountItem> dbItemMap = new HashMap<>();

        for(AppPaymentAmountItem item : dbItems){
            dbItemMap.put(item.getId(), item);
            if(item.getOrderNo()!=null){
                affectedOrderNos.add(item.getOrderNo());
            }
        }

        if(entity.getAmountItems()!=null && !entity.getAmountItems().isEmpty()){
            for(AppPaymentAmountItem item : entity.getAmountItems()){
                if(item.getAmount() == null) continue;
                item.setOrderId(entity.getId());
                AppPaymentAmountItem dbItem = item.getId() == null ? null : dbItemMap.get(item.getId());
                if(dbItem == null){
                    item.setId(null);
                    if(item.getCreateTime() == null){
                        item.setCreateTime(new Date());
                    }
                    paymentAmountItemService.save(item);
                }else{
                    if(dbItem.getOrderNo()!=null){
                        affectedOrderNos.add(dbItem.getOrderNo());
                    }
                    if(item.getCreateTime() == null){
                        item.setCreateTime(dbItem.getCreateTime());
                    }
                    paymentAmountItemService.updateById(item);
                }
                currentIds.add(item.getId());
                if(item.getOrderNo()!=null){
                    affectedOrderNos.add(item.getOrderNo());
                }
            }
        }

        if(isUpdate && !dbItems.isEmpty()){
            List<Integer> removeIds = new ArrayList<>();
            for(AppPaymentAmountItem item : dbItems){
                if(!currentIds.contains(item.getId())){
                    removeIds.add(item.getId());
                }
            }
            if(!removeIds.isEmpty()){
                paymentAmountItemService.removeByIds(removeIds);
            }
        }

        for(String orderNo : affectedOrderNos){
            appPurchaseOrderService.updatePayAmount(orderNo,0.00);
        }
    }

    private void updateAccountSettle(AppPaymentSettleItem settle, int direction) {
        if(settle == null || settle.getSettleId() == null || settle.getAmount() == null) return;
        if(Math.abs(settle.getAmount()) < 0.000001) return;
        accountSettleService.updateCurPrc(settle.getSettleId(), settle.getAmount() * direction);
    }

    private boolean isActive(AppPaymentVoucher entity) {
        return entity != null && (entity.getStatus() == null || entity.getStatus() == 1);
    }

}




