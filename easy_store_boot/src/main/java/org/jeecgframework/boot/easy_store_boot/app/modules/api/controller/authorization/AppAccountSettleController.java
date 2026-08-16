package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppAccountSettle;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppAccountSettleService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppAccountSettleTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/user/appAccountSettle")
public class AppAccountSettleController extends ApiBaseController<AppAccountSettle, IAppAccountSettleService> {
    @Autowired
    private IAppAccountSettleTypeService appAccountSettleTypeService;


    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param){
        return Result.ok(service.list(new LambdaQueryWrapper<AppAccountSettle>().orderByAsc(AppAccountSettle::getId)));
    }

    @PostMapping("typeList")
    public Result<?> typeList(@RequestBody JSONObject param){
        return Result.ok(appAccountSettleTypeService.getList());
    }
}
