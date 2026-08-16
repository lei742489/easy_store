package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.query.QueryGenerator;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.ApiQuery;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.permission.AppPermissionDefinition;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRolePermissionService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;


/**
 * 货品管理API接口
 */
@RestController
@RequestMapping("api/user/appGoods")
public class AppGoodsController extends ApiBaseController<AppGoods,IAppGoodsService> {

    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        return Result.ok(queryGoodsPage(param));
    }

    @PostMapping("listQuotePage")
    public Result<?> listQuotePage(@RequestBody JSONObject param) {
        IPage<AppGoods> page = queryGoodsPage(param);
        hideUnauthorizedQuotePrices(page.getRecords(), param.getString("userId"));
        return Result.ok(page);
    }

    private IPage<AppGoods> queryGoodsPage(JSONObject param) {
        AppGoods entity = JSONObject.toJavaObject(param, getEntityClass());
        Integer current = param.getInteger("current");
        Integer pageSize = param.getInteger("pageSize");
        if (current == null) current = 1;
        if (pageSize == null) pageSize = 15;

        if(StringUtils.isEmpty(entity.getTitle())){
            String categoryId = entity.getCategoryId();
            entity.setCategoryId(null);
            QueryWrapper<AppGoods> queryWrapper = QueryGenerator.initQueryWrapper(entity, param);
            if (categoryId!=null && !categoryId.equals("0")) {
                queryWrapper.eq("category_id", categoryId);
            }

            Page<AppGoods> page = new Page<>(current, pageSize);
            return service.page(page, queryWrapper);
        }else{
            ApiQuery query = JSONObject.toJavaObject(param, ApiQuery.class);
            query.setKey(entity.getTitle());
            return service.search(query);
        }
    }

    @Autowired
    private IAppUserService userService;
    @Autowired
    private IAppRolePermissionService rolePermissionService;

    private void hideUnauthorizedQuotePrices(List<AppGoods> goodsList, String userId) {
        if (goodsList == null || goodsList.isEmpty()) return;
        AppUser user = userService.getById(userId);
        if (user == null || (user.getIsRoot() != null && user.getIsRoot() == 1)) return;

        List<String> permissionCodes = rolePermissionService.listPermissionCodesByRoleId(user.getRoleId());
        boolean showCostPrice = permissionCodes.contains(AppPermissionDefinition.DATA_VIEW_COST_PRICE);
        boolean showPurchasePrice = permissionCodes.contains(AppPermissionDefinition.DATA_VIEW_PURCHASE_PRICE);
        boolean showTradePrice = permissionCodes.contains(AppPermissionDefinition.DATA_VIEW_TRADE_PRICE);
        boolean showSalePrice = permissionCodes.contains(AppPermissionDefinition.DATA_VIEW_SALE_PRICE);
        for (AppGoods goods : goodsList) {
            if (!showCostPrice) goods.setCostPrice(null);
            if (!showPurchasePrice) goods.setPurPrc(null);
            if (!showTradePrice) goods.setTradePrc(null);
            if (!showSalePrice) goods.setSalePrc(null);
        }
    }

    @PostMapping("searchKey")
    public Result<?> searchKey(@RequestBody JSONObject param) {
        return Result.ok(service.searchByKey(param.getInteger("pageNo"),param.getString("key")));
    }



    @PostMapping("batchUpdateCategory")
    public Result<?> batchUpdateCategory(@RequestBody JSONObject param) {
        String categoryId = param.getString("categoryId");
        String ids = param.getString("ids");
        if(StringUtils.isEmpty(categoryId) || StringUtils.isEmpty(ids))
            throw new AppRunTimeException("参数错误");
        LambdaUpdateWrapper<AppGoods> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(AppGoods::getId, Arrays.asList(ids.split(",")));
        updateWrapper.set(AppGoods::getCategoryId, categoryId);
        service.update(updateWrapper);
        return Result.ok();

    }

    @PostMapping("batchRemove")
    public Result<?> batchRemove(@RequestBody JSONObject param) {
        String ids = param.getString("ids");
        if(StringUtils.isEmpty(ids))
            throw new AppRunTimeException("参数错误");

        LambdaQueryWrapper<AppGoods> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AppGoods::getId, Arrays.asList(ids.split(",")));
        service.remove(wrapper);
        return Result.ok();
    }

    @PostMapping("batchChangeStatus")
    public Result<?> batchChangeStatus(@RequestBody JSONObject param) {
        String ids = param.getString("ids");
        Integer status = param.getInteger("status");
        if(status == null || StringUtils.isEmpty(ids))
            throw new AppRunTimeException("参数错误");

        LambdaUpdateWrapper<AppGoods> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(AppGoods::getId, Arrays.asList(ids.split(",")));
        updateWrapper.set(AppGoods::getStatus, status);
        service.update(updateWrapper);
        return Result.ok();

    }
}
