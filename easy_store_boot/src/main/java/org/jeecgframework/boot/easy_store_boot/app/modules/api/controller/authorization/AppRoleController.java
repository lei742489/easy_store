package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppRole;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRoleMenuService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRolePermissionService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRoleService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.List;

@RestController
@RequestMapping("api/user/appRole")
public class AppRoleController extends ApiBaseController<AppRole, IAppRoleService> {

    @Autowired
    private IAppRoleMenuService roleMenuService;
    @Autowired
    private IAppRolePermissionService rolePermissionService;
    @Autowired
    private IAppUserService userService;

    @Override
    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        assertRoot(param);
        return super.listPage(param);
    }

    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param) {
        assertRoot(param);
        return Result.ok(service.list(new LambdaQueryWrapper<AppRole>()
                .eq(AppRole::getStatus, 1)
                .orderByDesc(AppRole::getId)));
    }

    @PostMapping("menuIds")
    public Result<?> menuIds(@RequestBody JSONObject param) {
        assertRoot(param);
        return Result.ok(roleMenuService.listMenuIdsByRoleId(param.getInteger("roleId")));
    }

    @PostMapping("permissionCodes")
    public Result<?> permissionCodes(@RequestBody JSONObject param) {
        assertRoot(param);
        return Result.ok(rolePermissionService.listPermissionCodesByRoleId(param.getInteger("roleId")));
    }

    @Override
    @PostMapping("add")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> add(@RequestBody JSONObject param) {
        assertRoot(param);
        AppRole entity = JSONObject.toJavaObject(param, AppRole.class);
        if(entity.getStatus() == null) entity.setStatus(1);
        boolean res = service.save(entity);
        if (!res) throw new AppRunTimeException("添加失败");
        roleMenuService.saveRoleMenus(entity.getId(), entity.getMenuIds());
        rolePermissionService.saveRolePermissions(entity.getId(), entity.getPermissionCodes());
        return Result.ok();
    }

    @Override
    @PostMapping("edit")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> edit(@RequestBody JSONObject param) {
        assertRoot(param);
        AppRole entity = JSONObject.toJavaObject(param, AppRole.class);
        boolean res = service.updateById(entity);
        if (!res) throw new AppRunTimeException("更新失败");
        roleMenuService.saveRoleMenus(entity.getId(), entity.getMenuIds());
        rolePermissionService.saveRolePermissions(entity.getId(), entity.getPermissionCodes());
        return Result.ok();
    }

    @Override
    @PostMapping("remove")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> remove(@RequestBody JSONObject param) {
        assertRoot(param);
        Serializable id = param.getInteger("id");
        boolean res = service.removeById(id);
        if (!res) throw new AppRunTimeException("删除失败");
        roleMenuService.saveRoleMenus(param.getInteger("id"), null);
        rolePermissionService.saveRolePermissions(param.getInteger("id"), null);
        return Result.ok();
    }

    private void assertRoot(JSONObject param) {
        AppUser user = userService.getById(param.getInteger("userId"));
        if(user == null || user.getIsRoot() == null || user.getIsRoot() != 1){
            throw new AppRunTimeException("只有老板账号可以管理角色");
        }
    }
}
