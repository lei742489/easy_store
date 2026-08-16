package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUnit;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author Administrator
* @description 针对表【app_unit】的数据库操作Service
* @createDate 2025-06-27 15:39:22
*/
public interface IAppUnitService extends IService<AppUnit> {

    void updateByName(String name);

    String normalizeName(String value);

}
