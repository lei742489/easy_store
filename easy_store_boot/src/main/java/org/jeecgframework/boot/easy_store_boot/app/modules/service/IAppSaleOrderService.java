package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSaleOrder;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.Serializable;

/**
* @author Administrator
* @description 针对表【app_sale_order】的数据库操作Service
* @createDate 2025-07-09 09:46:47
*/
public interface IAppSaleOrderService extends IService<AppSaleOrder> {

    boolean save(AppSaleOrder entity);

    boolean updateById(AppSaleOrder entity);

    boolean removeById(Serializable id);

    Double sumUnpaidAmountByCustomer(String customerId);

    Double sumPayableAmountByCustomer(String customerId);

    AppSaleOrder getByOrderNo(String orderNo);

    void updatePayAmount(String orderNo, Double payAmount);
}
