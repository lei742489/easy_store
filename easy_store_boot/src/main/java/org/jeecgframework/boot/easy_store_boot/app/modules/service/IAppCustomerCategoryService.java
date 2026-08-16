package org.jeecgframework.boot.easy_store_boot.app.modules.service;


import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerCategory;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_customer_category】的数据库操作Service
* @createDate 2025-06-22 13:42:42
*/
public interface IAppCustomerCategoryService extends IService<AppCustomerCategory> {

    List<AppCustomerCategory> getList();

    List<AppCustomerCategory> getListByParentId(Integer parentId);

    boolean hasChildren(Integer parentId);

    void   checkCategoryLevel(Integer parentId);

}
