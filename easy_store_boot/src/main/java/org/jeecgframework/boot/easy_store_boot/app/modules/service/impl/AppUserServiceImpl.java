package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppUserMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_user】的数据库操作Service实现
* @createDate 2025-06-15 14:36:06
*/
@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser>  implements IAppUserService {

    @Override
    public AppUser getByAccount(String account) {
        return getOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUserName,account).last("limit 1"));
    }

    @Override
    public AppUser getByName(String realName) {
        List<AppUser> appUserList = list(new LambdaQueryWrapper<AppUser>().eq(AppUser::getRealName,realName));
        if(appUserList.isEmpty())
            throw new RuntimeException("用户信息获取失败");
        if(appUserList.size()>1)
            throw new RuntimeException("存在同名用户");
        return appUserList.get(0);
    }


}




