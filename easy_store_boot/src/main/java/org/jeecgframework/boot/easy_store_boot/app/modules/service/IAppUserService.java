package org.jeecgframework.boot.easy_store_boot.app.modules.service;


import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;

/**
* @author Administrator
* @description 针对表【app_user】的数据库操作Service
* @createDate 2025-06-15 14:36:06
*/
public interface IAppUserService extends IService<AppUser> {

    AppUser getByAccount(String account);


}
