package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppHomeMenu;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppHomeMenuMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppHomeMenuService;
import org.springframework.stereotype.Service;

@Service
public class AppHomeMenuServiceImpl extends ServiceImpl<AppHomeMenuMapper, AppHomeMenu>
        implements IAppHomeMenuService {
}
