package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;


import com.alibaba.fastjson.JSONObject;
import org.jeecgframework.boot.easy_store_boot.app.common.NullUtil;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoodsCategory;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsCategoryService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * 货品分类管理API
 */
@RestController
@RequestMapping("api/user/goods/category")
public class AppGoodsCategoryController {

    @Autowired
    private IAppGoodsCategoryService appGoodsCategoryService;
    @Autowired
    private IAppGoodsService appGoodsService;

    /**
     * 列表
     */
    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param) {
        return Result.ok(appGoodsCategoryService.getList());
    }

    /**
     * 添加
     */
    @PostMapping("add")
    public Result<?> add(@RequestBody JSONObject param) {
        AppGoodsCategory appGoodsCategory = JSONObject.toJavaObject(param, AppGoodsCategory.class);
        NullUtil.check(appGoodsCategory.getTitle(),"请输入标题");

        // 判断层级
        Integer parentId = appGoodsCategory.getParentId();
        appGoodsCategoryService.checkCategoryLevel(parentId);

        appGoodsCategory.setCreateTime(new Date());
        appGoodsCategoryService.save(appGoodsCategory);
        return Result.ok();
    }

    /**
     * 编辑
     */
    @PostMapping("edit")
    public Result<?> edit(@RequestBody JSONObject param) {
        AppGoodsCategory data = JSONObject.toJavaObject(param, AppGoodsCategory.class);

        // 判断层级
        Integer parentId = data.getParentId();
        appGoodsCategoryService.checkCategoryLevel(parentId);

        boolean res = appGoodsCategoryService.updateById(data);
        if(!res)
            throw new AppRunTimeException("更新失败");
        return Result.ok();
    }

    /**
     * 删除
     */
    @PostMapping("remove")
    public Result<?> remove(@RequestBody JSONObject param) {
        String id = param.getString("id");

        if(appGoodsCategoryService.hasChildren(id))
            throw new AppRunTimeException("删除失败，存在下级分类");

        boolean res = appGoodsCategoryService.removeById(id);
        if(!res)
            throw new AppRunTimeException("删除失败");

        appGoodsService.setDefCategoryId(id);
        return Result.ok();
    }

}
