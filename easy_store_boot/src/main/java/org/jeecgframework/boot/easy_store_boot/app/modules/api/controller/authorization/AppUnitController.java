package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUnit;

import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUnitService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/user/appUnit")
public class AppUnitController extends ApiBaseController<AppUnit, IAppUnitService> {
    /**
     * 列表
     */
    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param) {
        return Result.ok(service.list(new LambdaQueryWrapper<AppUnit>().orderByDesc(AppUnit::getId)));
    }
}
