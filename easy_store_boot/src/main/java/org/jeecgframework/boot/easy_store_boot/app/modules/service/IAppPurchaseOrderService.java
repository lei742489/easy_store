package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrder;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.Serializable;

/**
* @author Administrator
* @description 针对表【app_purchase_order】的数据库操作Service
* @createDate 2025-06-30 13:38:41
*/
public interface IAppPurchaseOrderService extends IService<AppPurchaseOrder> {

    boolean save(AppPurchaseOrder entity);

    boolean updateById(AppPurchaseOrder entity);

    boolean removeById(Serializable id);

    Double sumUnpaidAmountBySupplier(String supplierId);

    Double sumPayableAmountBySupplier(String supplierId);

    AppPurchaseOrder getByOrderNo(String orderNo);

    void updatePayAmount(String orderNo, Double payAmount);


}
