package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrderItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPurchaseOrderItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppPurchaseOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
* @author Administrator
* @description 针对表【app_purchase_order_item】的数据库操作Service实现
* @createDate 2025-07-01 08:53:50
*/
@Service
public class AppPurchaseOrderItemServiceImpl extends ServiceImpl<AppPurchaseOrderItemMapper, AppPurchaseOrderItem>
    implements IAppPurchaseOrderItemService {
    @Autowired
    private IAppGoodsService appGoodsService;

    @Override
    public void removeByOrderId(Integer orderId) {
        List<AppPurchaseOrderItem> list = listByOrderId(orderId);
        LambdaQueryWrapper<AppPurchaseOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppPurchaseOrderItem::getOrderId, orderId);
        this.remove(wrapper);
        for(AppPurchaseOrderItem item:list){
            item.setQuantity(item.getQuantity() * -1);
        }
        batchUpdateGoodsStore(list);
    }

    @Override
    public List<AppPurchaseOrderItem> listByOrderId(Integer orderId) {
        return super.list(new LambdaQueryWrapper<AppPurchaseOrderItem>()
                .eq(AppPurchaseOrderItem::getOrderId, orderId).orderByAsc(AppPurchaseOrderItem::getId));
    }

    @Override
    public void removeByUpdate(List<AppPurchaseOrderItem> updateList,Integer orderId) {
        Set<Integer> ids = new HashSet<>();
        for(AppPurchaseOrderItem item:updateList){
            if(item.getId()!=null){
                ids.add(item.getId());
            }
        }
        LambdaQueryWrapper<AppPurchaseOrderItem> wrapper = new LambdaQueryWrapper<AppPurchaseOrderItem>()
                .eq(AppPurchaseOrderItem::getOrderId, orderId);
        if(!ids.isEmpty()){
            wrapper.notIn(AppPurchaseOrderItem::getId, ids);
        }
        List<AppPurchaseOrderItem> list = this.list(wrapper);
        Set<Integer> removeIds = new HashSet<>();
        for(AppPurchaseOrderItem item:list){
            removeIds.add(item.getId());
        }
        if(!removeIds.isEmpty()){
            removeByIds(removeIds);
        }
    }


    @Override
    public   void batchUpdateGoodsStore(List<AppPurchaseOrderItem> updateList){
        if(updateList == null || updateList.isEmpty()){
            return;
        }
        // Rebuild each affected goods once and use a stable lock order.
        Set<Integer> goodsIds = new TreeSet<>();
        for(AppPurchaseOrderItem item:updateList){
            if(item == null || item.getGoodsId() == null){
                continue;
            }
            String goodsId = item.getGoodsId().trim();
            if(goodsId.matches("\\d+")){
                goodsIds.add(Integer.valueOf(goodsId));
            }
        }
        for(Integer goodsId : goodsIds){
            appGoodsService.updateStock(String.valueOf(goodsId));
        }
    }

    @Override
    public Double sumQuantityGoodsId(String goodsId) {
        if(goodsId == null)
         return 0D;
        QueryWrapper<AppPurchaseOrderItem> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(quantity), 0) as total")
                .eq("goods_id", goodsId)
                .and(w -> w.isNull("order_id")
                        .or()
                        .inSql("order_id", "select id from app_purchase_order where is_del = 0 and status = 1"));
        Map<String,Object> map= getMap(wrapper);
        return Double.parseDouble(map.get("total").toString());
    }

    @Override
    public void insertInitStore(String goodsId, Double store) {
        if(goodsId== null || store == null) return;
        AppPurchaseOrderItem orderItem  = getOne(new LambdaQueryWrapper<AppPurchaseOrderItem>().eq(AppPurchaseOrderItem::getGoodsId, goodsId).eq(AppPurchaseOrderItem::getIsInit,1).last("limit 1"));
        if(orderItem == null ) orderItem = new AppPurchaseOrderItem();

        orderItem.setGoodsId(goodsId);
        orderItem.setQuantity(store);
        orderItem.setIsInit(1);
        saveOrUpdate(orderItem);
    }
}




