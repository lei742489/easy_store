package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerLevel;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerLevelService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;


/**
 * 客户等级管理API接口
 */
@RestController
@RequestMapping("api/user/customer/level")
public class AppCustomLevelController extends ApiBaseController<AppCustomerLevel, IAppCustomerLevelService> {
    @Autowired
    private IAppCustomerLevelService service;
    @Autowired
    private IAppCustomerService appCustomerService;


    @PostMapping("list")
    public Result<?> list (@RequestBody JSONObject param){
        List<AppCustomerLevel> list = service.list(new LambdaQueryWrapper<AppCustomerLevel>().orderByDesc(AppCustomerLevel::getId));
        return Result.ok(list);
    }

    @PostMapping("remove")
    public Result<?> remove(@RequestBody JSONObject param) {
        Integer id = param.getInteger("id");
        if(id == null)
            throw new AppRunTimeException("参数ID不能为空");
        boolean res = service.removeById(id);
        if (!res) throw new AppRunTimeException("删除失败");

        appCustomerService.setDefLevel(id);
        return Result.ok();
    }
}
