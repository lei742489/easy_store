package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSupplier;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_supplier】的数据库操作Service
* @createDate 2025-06-26 14:29:14
*/
public interface IAppSupplierService extends IService<AppSupplier> {

    boolean save(AppSupplier entity);

    Double updatePayable(String supplerId);

    int refreshAllPayable();

    List<AppSupplier> searchByName(String name);


}
