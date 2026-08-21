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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;


/**
 * 货品管理API接口
 */
@RestController
@RequestMapping("api/user/appGoods")
public class AppGoodsController extends ApiBaseController<AppGoods,IAppGoodsService> {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
            if (!Boolean.TRUE.equals(param.getBoolean("zeroStock"))) {
                queryWrapper.apply("COALESCE(stock, 0) <> 0");
            }

            Page<AppGoods> page = new Page<>(current, pageSize);
            return service.page(page, queryWrapper);
        }else{
            ApiQuery query = JSONObject.toJavaObject(param, ApiQuery.class);
            query.setKey(entity.getTitle());
            query.setZeroStock(param.getBoolean("zeroStock"));
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
        return Result.ok(service.searchByKey(param.getInteger("pageNo"),
                param.getString("key"), param.getBoolean("hideZeroStock")));
    }

    @PostMapping("stockDetail")
    public Result<?> stockDetail(@RequestBody JSONObject param) {
        String goodsId = param.getString("goodsId");
        if (StringUtils.isEmpty(goodsId)) {
            throw new AppRunTimeException("请选择货品");
        }
        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        if (startTime != null && endTime != null && startTime > endTime) {
            throw new AppRunTimeException("开始日期不能晚于结束日期");
        }
        JSONObject result = service.getStockDetail(goodsId, startTime, endTime);
        if (result == null) {
            throw new AppRunTimeException("货品不存在或已删除");
        }
        return Result.ok(result);
    }

    @PostMapping("stockStatistics")
    public Result<?> stockStatistics(@RequestBody JSONObject param) {
        Long startTime = parseStartTime(param.getString("startDate"));
        Long endTime = parseEndTime(param.getString("endDate"));
        if (startTime != null && endTime != null && startTime > endTime) {
            throw new AppRunTimeException("开始日期不能晚于结束日期");
        }
        return Result.ok(service.getStockStatistics(param.getString("categoryId"),
                param.getString("key"), startTime, endTime,
                param.getInteger("current"), param.getInteger("pageSize")));
    }

    @PostMapping("stockWarningPage")
    public Result<?> stockWarningPage(@RequestBody JSONObject param) {
        int current = param.getInteger("current") == null ? 1 : param.getInteger("current");
        int pageSize = param.getInteger("pageSize") == null ? 50 : param.getInteger("pageSize");
        QueryWrapper<AppGoods> wrapper = new QueryWrapper<>();
        String supplierId = param.getString("supplierId");
        if (StringUtils.isNotEmpty(supplierId)) {
            wrapper.eq("supplier_id", supplierId);
        }
        wrapper.and(item -> item.apply("COALESCE(stock, 0) < COALESCE(min_stock, 0)")
                .or()
                .apply("COALESCE(max_stock, 0) > 0 AND COALESCE(stock, 0) > COALESCE(max_stock, 0)"));
        wrapper.orderByAsc("title").orderByAsc("id");
        return Result.ok(service.page(new Page<AppGoods>(current, pageSize), wrapper));
    }

    private Long parseStartTime(String value) {
        if (StringUtils.isEmpty(value)) return null;
        try {
            return LocalDate.parse(value, DATE_FORMATTER).atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        } catch (Exception e) {
            throw new AppRunTimeException("开始日期格式错误");
        }
    }

    private Long parseEndTime(String value) {
        if (StringUtils.isEmpty(value)) return null;
        try {
            return LocalDate.parse(value, DATE_FORMATTER).plusDays(1)
                    .atStartOfDay(ZONE_ID).toInstant().toEpochMilli() - 1;
        } catch (Exception e) {
            throw new AppRunTimeException("结束日期格式错误");
        }
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
