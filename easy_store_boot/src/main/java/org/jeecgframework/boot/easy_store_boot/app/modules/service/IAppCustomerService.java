package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomer;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author Administrator
* @description 针对表【app_customer】的数据库操作Service
* @createDate 2025-06-23 14:50:29
*/
public interface IAppCustomerService extends IService<AppCustomer> {

    void setDefaultCategory(Integer categoryId);

    void setDefLevel(Integer levelId);

    Double updatePayable(String supplerId);
}
