package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppRole;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppRoleMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRoleService;
import org.springframework.stereotype.Service;

@Service
public class AppRoleServiceImpl extends ServiceImpl<AppRoleMapper, AppRole>
        implements IAppRoleService {
}
