package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppRoleMenu;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppRoleMenuMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRoleMenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppRoleMenuServiceImpl extends ServiceImpl<AppRoleMenuMapper, AppRoleMenu>
        implements IAppRoleMenuService {

    @Override
    public List<Integer> listMenuIdsByRoleId(Integer roleId) {
        if(roleId == null) return Collections.emptyList();
        return list(new LambdaQueryWrapper<AppRoleMenu>()
                .eq(AppRoleMenu::getRoleId, roleId))
                .stream()
                .map(AppRoleMenu::getMenuId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleMenus(Integer roleId, List<Integer> menuIds) {
        if(roleId == null) return;
        remove(new LambdaQueryWrapper<AppRoleMenu>().eq(AppRoleMenu::getRoleId, roleId));
        if(menuIds == null || menuIds.isEmpty()) return;

        List<AppRoleMenu> list = new ArrayList<>();
        for(Integer menuId : menuIds){
            if(menuId == null) continue;
            AppRoleMenu item = new AppRoleMenu();
            item.setRoleId(roleId);
            item.setMenuId(menuId);
            list.add(item);
        }
        if(!list.isEmpty()){
            saveBatch(list);
        }
    }
}
