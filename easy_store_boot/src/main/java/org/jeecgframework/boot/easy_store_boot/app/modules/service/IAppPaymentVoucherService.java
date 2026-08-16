package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPaymentVoucher;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.Serializable;

/**
* @author Administrator
* @description 针对表【app_payment_voucher】的数据库操作Service
* @createDate 2025-07-07 10:00:26
*/
public interface IAppPaymentVoucherService extends IService<AppPaymentVoucher> {

    boolean save(AppPaymentVoucher entity);

    boolean updateById(AppPaymentVoucher entity);

    boolean removeById(Serializable id);

    Double sumPaidAmountBySupplier(String supplierId);
}
