package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.CommonUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.PasswordUtil;
import org.jeecgframework.boot.easy_store_boot.app.common.query.QueryGenerator;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.permission.AppPermissionDefinition;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppRole;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRoleService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRolePermissionService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("api/user")
public class AppUserController extends ApiBaseController<AppUser, IAppUserService> {

    @Autowired
    private IAppRoleService roleService;
    @Autowired
    private IAppRolePermissionService rolePermissionService;

    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        assertRoot(param);
        AppUser entity = JSONObject.toJavaObject(param, getEntityClass());
        Integer current = param.getInteger("current");
        Integer pageSize = param.getInteger("pageSize");
        if (current == null) current = 1;
        if (pageSize == null) pageSize = 15;
        entity.setToken(null);
        QueryWrapper<AppUser> queryWrapper = QueryGenerator.initQueryWrapper(entity, param);
        queryWrapper.orderByDesc("id");
        Page<AppUser> page = new Page<>(current, pageSize);
        IPage<AppUser> pageList = service.page(page, queryWrapper);
        fillRoleName(pageList.getRecords());
        return Result.ok(pageList);
    }

    @PostMapping("getInfo")
    public Result<Object> getUserInfo(@RequestBody JSONObject param) {
        String userId = param.getString("userId");
        AppUser appUser = service.getById(userId);
        if (appUser == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }

        fillRoleName(Collections.singletonList(appUser));
        fillPermissionCodes(appUser);
        return Result.ok(appUser);
    }

    @PostMapping("list")
    public Result<Object> list(@RequestBody JSONObject param) {
        List<AppUser> list = service.list();
        for (AppUser appUser : list) {
            if (appUser.getIsRoot() != null && appUser.getIsRoot() == 1) {
                appUser.setUserName("老板");
            }
        }
        fillRoleName(list);
        return Result.ok(list);
    }

    @PostMapping("add")
    public Result<?> add(@RequestBody JSONObject param) {
        assertRoot(param);
        AppUser entity = JSONObject.toJavaObject(param, getEntityClass());
        if (StringUtils.isEmpty(entity.getUserName())) {
            throw new AppRunTimeException("请输入用户名");
        }
        if (entity.getRoleId() == null) {
            throw new AppRunTimeException("请选择角色");
        }
        entity.setIsRoot(0);
        if (entity.getStatus() == null) entity.setStatus(1);
        if (StringUtils.isEmpty(entity.getPassword()) || entity.getPassword().length() < 6) {
            throw new AppRunTimeException("密码最少输入6位");
        }

        String salt = CommonUtils.generateCaptcha(8);
        entity.setSalt(salt);
        entity.setPassword(PasswordUtil.encrypt(entity.getUserName(), entity.getPassword(), salt));
        entity.setCreateTime(new Date());
        boolean res = service.save(entity);
        if (!res) throw new AppRunTimeException("添加失败");
        return Result.ok();
    }

    @Override
    @PostMapping("edit")
    public Result<?> edit(@RequestBody JSONObject param) {
        assertRoot(param);
        AppUser entity = JSONObject.toJavaObject(param, getEntityClass());
        AppUser oldUser = service.getById(entity.getId());
        if (oldUser == null) {
            throw new AppRunTimeException("用户数据不存在");
        }
        if (entity.getRoleId() == null) {
            throw new AppRunTimeException("请选择角色");
        }
        entity.setUserName(oldUser.getUserName());
        entity.setPassword(oldUser.getPassword());
        entity.setSalt(oldUser.getSalt());
        boolean res = service.updateById(entity);
        if (!res) throw new AppRunTimeException("更新失败");
        return Result.ok();
    }

    @PostMapping("checkPwd")
    public Result<?> checkPwd(@RequestBody JSONObject param) {
        String userId = param.getString("userId");
        String pwd = param.getString("pwd");
        if (StringUtils.isEmpty(pwd)) {
            throw new AppRunTimeException("密码验证失败");
        }

        AppUser appUser = service.getById(userId);
        String passwordEncode = PasswordUtil.encrypt(appUser.getUserName(), pwd, appUser.getSalt());
        if (!StringUtils.equals(appUser.getPassword(), passwordEncode)) {
            throw new AppRunTimeException("密码验证失败");
        }

        return Result.ok();
    }

    @PostMapping("updatePwd")
    public Result<?> updatePwd(@RequestBody JSONObject param) {
        String pwd = param.getString("pwd");

        if (StringUtils.isEmpty(pwd) || pwd.length() < 6) {
            throw new AppRunTimeException("密码最少输入6位");
        }
        AppUser appUser = service.getById(param.getInteger("userId"));

        if (appUser == null) {
            throw new AppRunTimeException("用户数据不存在");
        }

        String passwordEncode = PasswordUtil.encrypt(appUser.getUserName(), pwd, appUser.getSalt());
        appUser.setPassword(passwordEncode);
        service.updateById(appUser);
        return Result.ok();
    }

    @PostMapping("resetPwd")
    public Result<?> resetPwd(@RequestBody JSONObject param) {
        assertRoot(param);
        AppUser appUser = service.getById(param.getInteger("id"));
        if (appUser == null) {
            throw new AppRunTimeException("用户数据不存在");
        }
        if (StringUtils.isEmpty(appUser.getUserName())) {
            throw new AppRunTimeException("用户名不能为空");
        }
        String salt = CommonUtils.generateCaptcha(8);
        String password = PasswordUtil.encrypt(appUser.getUserName(), "123456", salt);
        appUser.setSalt(salt);
        appUser.setPassword(password);
        service.updateById(appUser);
        return Result.ok("密码已重置为123456");
    }

    @PostMapping("logout")
    public Result<?> logout(@RequestBody JSONObject param) {
        AppUser appUser = service.getById(param.getInteger("userId"));
        if (appUser != null) {
            System.err.println("用户-" + appUser.getUserName() + "退出成功！");
        }

        return Result.ok();
    }

    private void fillRoleName(List<AppUser> users) {
        if (users == null || users.isEmpty()) return;
        for (AppUser user : users) {
            if (user.getRoleId() == null) continue;
            AppRole role = roleService.getById(user.getRoleId());
            if (role != null) user.setRoleId_dictText(role.getName());
        }
    }

    private void fillPermissionCodes(AppUser user) {
        if (user == null) return;
        if (user.getIsRoot() != null && user.getIsRoot() == 1) {
            user.setPermissionCodes(AppPermissionDefinition.getDataViewPermissions());
            return;
        }
        user.setPermissionCodes(rolePermissionService.listPermissionCodesByRoleId(user.getRoleId()));
    }

    private void assertRoot(JSONObject param) {
        AppUser user = service.getById(param.getInteger("userId"));
        if (user == null || user.getIsRoot() == null || user.getIsRoot() != 1) {
            throw new AppRunTimeException("只有老板账号可以操作员工管理");
        }
    }
}
