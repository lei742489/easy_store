package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppAccountSettle;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author Administrator
* @description 针对表【app_account_settle】的数据库操作Service
* @createDate 2025-06-29 13:53:25
*/
public interface IAppAccountSettleService extends IService<AppAccountSettle> {

    boolean save(AppAccountSettle entity);

    Double updateCurPrc(String settleId,Double price);

    String getNameById(String id);
}
