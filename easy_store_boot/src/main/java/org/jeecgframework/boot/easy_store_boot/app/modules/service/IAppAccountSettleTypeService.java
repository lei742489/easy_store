package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppAccountSettleType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_account_settle_type】的数据库操作Service
* @createDate 2025-06-29 13:50:17
*/
public interface IAppAccountSettleTypeService extends IService<AppAccountSettleType> {

    List<AppAccountSettleType> getList();
}
