package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;


import com.alibaba.fastjson.JSONObject;
import org.jeecgframework.boot.easy_store_boot.app.common.NullUtil;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerCategory;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerCategoryService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * 客户分类管理API
 */
@RestController
@RequestMapping("api/user/customer/category")
public class AppCustomCategoryController {

    @Autowired
    private IAppCustomerCategoryService appCustomerCategoryService;
    @Autowired
    private IAppCustomerService appCustomerService;

    /**
     * 列表
     */
    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param) {
        return Result.ok(appCustomerCategoryService.getList());
    }

    /**
     * 添加
     */
    @PostMapping("add")
    public Result<?> add(@RequestBody JSONObject param) {
        AppCustomerCategory appCustomerCategory = JSONObject.toJavaObject(param, AppCustomerCategory.class);
        NullUtil.check(appCustomerCategory.getTitle(),"请输入标题");

        // 判断层级
        Integer parentId = appCustomerCategory.getParentId();
        appCustomerCategoryService.checkCategoryLevel(parentId);

        appCustomerCategory.setCreateTime(new Date());
        appCustomerCategoryService.save(appCustomerCategory);
        return Result.ok();
    }

    /**
     * 编辑
     */
    @PostMapping("edit")
    public Result<?> edit(@RequestBody JSONObject param) {
        AppCustomerCategory data = JSONObject.toJavaObject(param, AppCustomerCategory.class);

        // 判断层级
        Integer parentId = data.getParentId();
        appCustomerCategoryService.checkCategoryLevel(parentId);

        boolean res = appCustomerCategoryService.updateById(data);
        if(!res)
            throw new AppRunTimeException("更新失败");
        return Result.ok();
    }

    /**
     * 删除
     */
    @PostMapping("remove")
    public Result<?> remove(@RequestBody JSONObject param) {
        Integer id = param.getInteger("id");
        if(id == null)
            throw new AppRunTimeException("参数ID不能为空");

        if(appCustomerCategoryService.hasChildren(id))
            throw new AppRunTimeException("删除失败，存在下级分类");

        boolean res = appCustomerCategoryService.removeById(id);
        if(!res)
            throw new AppRunTimeException("删除失败");

        appCustomerService.setDefaultCategory(id);

        return Result.ok();
    }

}
