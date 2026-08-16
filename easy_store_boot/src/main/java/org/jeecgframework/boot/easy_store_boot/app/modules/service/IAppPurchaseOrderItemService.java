package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrderItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_purchase_order_item】的数据库操作Service
* @createDate 2025-07-01 08:53:50
*/
public interface IAppPurchaseOrderItemService extends IService<AppPurchaseOrderItem> {

    void removeByOrderId(Integer orderId);

    List<AppPurchaseOrderItem> listByOrderId(Integer orderId);

    void removeByUpdate(List<AppPurchaseOrderItem> updateList,Integer orderId);

    void batchUpdateGoodsStore(List<AppPurchaseOrderItem> updateList);

    Integer sumQuantityGoodsId(String goodsId);

    void insertInitStore(String goodsId,Integer store);

}
