package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPaymentVoucher;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppReceivePaymentVoucher;

import java.io.Serializable;

/**
* @author Administrator
* @description 针对表【app_receive_payment_voucher】的数据库操作Service
* @createDate 2025-07-07 10:00:26
*/
public interface IAppReceivePaymentVoucherService extends IService<AppReceivePaymentVoucher> {

    boolean save(AppReceivePaymentVoucher entity);

    boolean updateById(AppReceivePaymentVoucher entity);

    boolean removeById(Serializable id);

    Double sumPaidAmountByCustomerId(String customerId);
}
