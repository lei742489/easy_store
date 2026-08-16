package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller;


import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.CommonUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.PasswordUtil;
import org.jeecgframework.boot.easy_store_boot.app.common.SqliteTestUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.SymmetricEncoder;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.AutoPoiDictService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerCategoryService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 前端APi接口类
 */
@RestController
@RequestMapping("api")
public class AppApiController {
    @Autowired
    private IAppUserService appUserService;
    @Autowired
    private AutoPoiDictService autoPoiDictService;

    @PostMapping("test")
    public Result<Object> test() {
        return Result.ok();
    }
    /**
     * 登录用户
     */
    @PostMapping("login")
    public Result<Object> login(@RequestBody JSONObject param){
        String userName = param.getString("username");
        String password = param.getString("password");

        if(StringUtils.isEmpty(userName) || StringUtils.isEmpty(password)){
            throw new AppRunTimeException("用户名或者密码错误");
        }
        AppUser appUser = appUserService.getByAccount(userName);
        if(appUser == null)
            throw new AppRunTimeException("用户名或者密码错误");

        if(appUser.getStatus()!=1)
            throw new AppRunTimeException("帐户被禁用。");

        if(StringUtils.isEmpty(appUser.getPassword()) || StringUtils.isEmpty(appUser.getSalt())) {
            throw new AppRunTimeException("该账号未初始化密码，请联系管理员重置后登录");
        }

        String passwordEncode = PasswordUtil.encrypt(appUser.getUserName(), password, appUser.getSalt());
        if(!StringUtils.equals(passwordEncode, appUser.getPassword()))
            throw new AppRunTimeException("用户名或者密码错误");

        appUser.setLastLoginTime(new Date());
        appUserService.updateById(appUser);
        appUser.setToken(SymmetricEncoder.createUserToken(appUser.getId() + ""));
        return Result.ok(appUser);

    }

    /**
     * 注册用户
     * @param param
     * @return
     */
    @PostMapping("register")
    public Result<Object> register( @RequestBody JSONObject param){
        AppUser appUser = JSONObject.parseObject(param.toJSONString(), AppUser.class);

        if(StringUtils.isEmpty(appUser.getUserName()))
            throw new AppRunTimeException("请输入用户名");

        if(StringUtils.isEmpty(appUser.getPassword()) || appUser.getPassword().length()<6)
            throw new AppRunTimeException("密码最少为6位");

        String salt = CommonUtils.generateCaptcha(8);
        appUser.setSalt(salt);
        String passwordEncode = PasswordUtil.encrypt(appUser.getUserName(), appUser.getPassword(), salt);
        appUser.setPassword(passwordEncode);
        appUser.setStatus(1);
        appUser.setLastLoginTime(new Date());
        appUser.setCreateTime(new Date());
        try {
            appUser.setIsRoot(1);
            if(StringUtils.isEmpty(appUser.getRealName()))
                 appUser.setRealName("Boss");
            appUserService.save(appUser);
        }catch (Exception e){
            if(e.getMessage().contains("UNIQUE"))
                throw new AppRunTimeException("该帐户已存在，请登录");
        }

        appUser.setToken(SymmetricEncoder.createUserToken(appUser.getId() + ""));
        return Result.ok(appUser);
    }



}
