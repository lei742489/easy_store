package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppRolePermission;

import java.util.List;

public interface IAppRolePermissionService extends IService<AppRolePermission> {

    List<String> listPermissionCodesByRoleId(Integer roleId);

    void saveRolePermissions(Integer roleId, List<String> permissionCodes);

    boolean hasPermission(Integer roleId, String permissionCode);
}
