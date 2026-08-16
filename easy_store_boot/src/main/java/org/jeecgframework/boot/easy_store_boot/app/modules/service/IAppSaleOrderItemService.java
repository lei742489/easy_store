package org.jeecgframework.boot.easy_store_boot.app.modules.service;



import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSaleOrderItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_sale_order_item】的数据库操作Service
* @createDate 2025-07-09 09:45:18
*/
public interface IAppSaleOrderItemService extends IService<AppSaleOrderItem> {

    void removeByOrderId(Integer orderId);

    List<AppSaleOrderItem> listByOrderId(Integer orderId);

    void batchUpdateGoodsStore(List<AppSaleOrderItem> updateList);

    void removeByUpdate(List<AppSaleOrderItem> updateList,Integer orderId);

    Integer sumQuantityGoodsId(String goodsId);


}
