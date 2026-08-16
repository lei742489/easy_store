package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSaleOrderItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSaleOrderItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppSaleOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* @author Administrator
* @description 针对表【app_sale_order_item】的数据库操作Service实现
* @createDate 2025-07-09 09:45:18
*/
@Service
public class AppSaleOrderItemServiceImpl extends ServiceImpl<AppSaleOrderItemMapper, AppSaleOrderItem>
    implements IAppSaleOrderItemService {

    @Autowired
    private IAppGoodsService appGoodsService;

    @Override
    public void removeByOrderId(Integer orderId) {
        List<AppSaleOrderItem> list = listByOrderId(orderId);
        LambdaQueryWrapper<AppSaleOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppSaleOrderItem::getOrderId, orderId);
        this.remove(wrapper);
        for(AppSaleOrderItem item:list){
            item.setQuantity(item.getQuantity() * -1);
        }
        batchUpdateGoodsStore(list);
    }

    @Override
    public List<AppSaleOrderItem> listByOrderId(Integer orderId) {
        return super.list(new LambdaQueryWrapper<AppSaleOrderItem>()
                .eq(AppSaleOrderItem::getOrderId, orderId).orderByAsc(AppSaleOrderItem::getId));
    }

    @Override
    public void removeByUpdate(List<AppSaleOrderItem> updateList,Integer orderId) {
        Set<Integer> ids = new HashSet<>();
        for(AppSaleOrderItem item:updateList){
            if(item.getId()!=null){
                ids.add(item.getId());
            }
        }
        LambdaQueryWrapper<AppSaleOrderItem> wrapper = new LambdaQueryWrapper<AppSaleOrderItem>()
                .eq(AppSaleOrderItem::getOrderId, orderId);
        if(!ids.isEmpty()){
            wrapper.notIn(AppSaleOrderItem::getId, ids);
        }
        List<AppSaleOrderItem> list = this.list(wrapper);
        Set<Integer> removeIds = new HashSet<>();
        for(AppSaleOrderItem item:list){
            removeIds.add(item.getId());
        }
        if(!removeIds.isEmpty()){
            removeByIds(removeIds);
        }
    }


    @Override
    public   void batchUpdateGoodsStore(List<AppSaleOrderItem> updateList){
        if(!updateList.isEmpty()){
            for(AppSaleOrderItem item:updateList){
                appGoodsService.updateStock(item.getGoodsId());
            }
        }
    }

    @Override
    public Integer sumQuantityGoodsId(String goodsId) {
        if(goodsId == null)
            return 0;
        QueryWrapper<AppSaleOrderItem> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(quantity), 0) as total")
                .eq("goods_id", goodsId)
                .inSql("order_id", "select id from app_sale_order where is_del = 0 and status = 1");
        Map<String,Object> map= getMap(wrapper);
        return Integer.parseInt(map.get("total").toString());
    }

}




