package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppRoleMenu;

import java.util.List;

public interface IAppRoleMenuService extends IService<AppRoleMenu> {

    List<Integer> listMenuIdsByRoleId(Integer roleId);

    void saveRoleMenus(Integer roleId, List<Integer> menuIds);
}
