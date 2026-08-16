package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPaymentSettleItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_payment_settle_item】的数据库操作Service
* @createDate 2025-07-07 10:05:42
*/
public interface IAppPaymentSettleItemService extends IService<AppPaymentSettleItem> {

    List<AppPaymentSettleItem> listByPaymentId(String paymentId);
}
