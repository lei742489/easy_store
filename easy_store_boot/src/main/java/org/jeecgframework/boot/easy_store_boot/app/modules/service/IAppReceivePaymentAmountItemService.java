package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppReceivePaymentAmountItem;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_receive_payment_amount_item】的数据库操作Service
* @createDate 2025-07-07 14:42:23
*/
public interface IAppReceivePaymentAmountItemService extends IService<AppReceivePaymentAmountItem> {

    void removeByOrderId(String orderId);

    List<AppReceivePaymentAmountItem> listByOrderId(String orderId);

    Double sumAmountByOrderNo(String orderNo);


}
