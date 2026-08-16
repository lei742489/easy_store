package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPaymentAmountItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_payment_amount_item】的数据库操作Service
* @createDate 2025-07-07 14:42:23
*/
public interface IAppPaymentAmountItemService extends IService<AppPaymentAmountItem> {

    void removeByOrderId(String orderId);

    List<AppPaymentAmountItem> listByOrderId(String orderId);

    Double sumAmountByOrderNo(String orderNo);


}
