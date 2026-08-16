package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppRolePermission;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppRolePermissionMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRolePermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppRolePermissionServiceImpl extends ServiceImpl<AppRolePermissionMapper, AppRolePermission>
        implements IAppRolePermissionService {

    @Override
    public List<String> listPermissionCodesByRoleId(Integer roleId) {
        if (roleId == null) return Collections.emptyList();
        return list(new LambdaQueryWrapper<AppRolePermission>()
                .eq(AppRolePermission::getRoleId, roleId))
                .stream()
                .map(AppRolePermission::getPermissionCode)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRolePermissions(Integer roleId, List<String> permissionCodes) {
        if (roleId == null) return;
        remove(new LambdaQueryWrapper<AppRolePermission>().eq(AppRolePermission::getRoleId, roleId));
        if (permissionCodes == null || permissionCodes.isEmpty()) return;

        List<AppRolePermission> list = new ArrayList<>();
        for (String permissionCode : permissionCodes) {
            if (permissionCode == null || permissionCode.trim().isEmpty()) continue;
            AppRolePermission item = new AppRolePermission();
            item.setRoleId(roleId);
            item.setPermissionCode(permissionCode);
            list.add(item);
        }
        if (!list.isEmpty()) {
            saveBatch(list);
        }
    }

    @Override
    public boolean hasPermission(Integer roleId, String permissionCode) {
        if (roleId == null || permissionCode == null || permissionCode.trim().isEmpty()) return false;
        return count(new LambdaQueryWrapper<AppRolePermission>()
                .eq(AppRolePermission::getRoleId, roleId)
                .eq(AppRolePermission::getPermissionCode, permissionCode)) > 0;
    }
}
