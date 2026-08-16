package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppReceivePaymentSettleItem;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_receive_payment_settle_item】的数据库操作Service
* @createDate 2025-07-07 10:05:42
*/
public interface IAppReceivePaymentSettleItemService extends IService<AppReceivePaymentSettleItem> {

    List<AppReceivePaymentSettleItem> listByPaymentId(String paymentId);


}
