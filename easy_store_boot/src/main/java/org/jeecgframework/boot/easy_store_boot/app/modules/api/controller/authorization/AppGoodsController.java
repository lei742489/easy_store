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
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.GoodsSearchResult;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRolePermissionService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
        IPage<AppGoods> page = queryGoodsPage(param);
        maskGoodsPrices(page.getRecords(), param, param.getString("quoteScene"));
        return Result.ok(page);
    }

    @PostMapping("listQuotePage")
    public Result<?> listQuotePage(@RequestBody JSONObject param) {
        IPage<AppGoods> page = queryGoodsPage(param);
        maskGoodsPrices(page.getRecords(), param, param.getString("quoteScene"));
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
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private void maskGoodsPrices(List<AppGoods> goodsList, JSONObject param, String quoteScene) {
        if (goodsList == null || goodsList.isEmpty()) return;
        boolean rootUser = isRootUser(param);
        boolean showCostPrice = hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_COST_PRICE);
        boolean showPurchasePrice = hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_PURCHASE_PRICE);
        boolean showTradePrice = hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_TRADE_PRICE);
        boolean showSalePrice = hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_SALE_PRICE);
        if ("businessOrder".equals(quoteScene) && !rootUser) {
            showCostPrice = false;
            showPurchasePrice = false;
        }
        for (AppGoods goods : goodsList) {
            if (!showCostPrice) {
                goods.setInitCost(0);
                goods.setStockCost(0D);
                goods.setCostPrice(0D);
            }
            if (!showPurchasePrice) goods.setPurPrc(0D);
            if (!showTradePrice) goods.setTradePrc(0D);
            if (!showSalePrice) goods.setSalePrc(0D);
        }
    }

    @PostMapping("searchKey")
    public Result<?> searchKey(@RequestBody JSONObject param) {
        List<GoodsSearchResult> results = service.searchByKey(param.getInteger("pageNo"),
                param.getString("key"), param.getBoolean("hideZeroStock"),
                param.getString("searchType"));
        boolean showCostPrice = hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_COST_PRICE);
        boolean showPurchasePrice = hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_PURCHASE_PRICE);
        boolean showTradePrice = hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_TRADE_PRICE);
        boolean showSalePrice = hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_SALE_PRICE);
        if ("businessOrder".equals(param.getString("quoteScene")) && !isRootUser(param)) {
            showCostPrice = false;
            showPurchasePrice = false;
        }
        for (GoodsSearchResult item : results) {
            if (!showCostPrice) item.setCostPrice(0D);
            if (!showPurchasePrice) item.setPurPrc(0D);
            if (!showTradePrice) item.setTradePrc(0D);
            if (!showSalePrice) item.setSalePrc(0D);
        }
        return Result.ok(results);
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
        if (!hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_COST_PRICE)) {
            maskStockDetail(result);
        }
        return Result.ok(result);
    }

    @PostMapping("rebuildStockLedger")
    public Result<?> rebuildStockLedger(@RequestBody JSONObject param) {
        String goodsId = param.getString("goodsId");
        if (StringUtils.isEmpty(goodsId)) {
            throw new AppRunTimeException("璇烽€夋嫨璐у搧");
        }
        service.updateStock(goodsId);
        return Result.ok();
    }

    @PostMapping("rebuildAllStockLedger")
    public Result<?> rebuildAllStockLedger() {
        List<AppGoods> goodsList = service.list();
        if (goodsList != null && !goodsList.isEmpty()) {
            for (AppGoods goods : goodsList) {
                if (goods.getId() != null) {
                    service.updateStock(String.valueOf(goods.getId()));
                }
            }
        }
        refreshAllSaleOrderGrossProfits();
        return Result.ok();
    }

    private void refreshAllSaleOrderGrossProfits() {
        jdbcTemplate.update("UPDATE app_sale_order o SET gross_profit = (" +
                "SELECT COALESCE(SUM(i.gross_profit), 0) FROM app_sale_order_item i " +
                "WHERE i.order_id = o.id AND COALESCE(i.is_del, 0) = 0) " +
                "WHERE COALESCE(o.is_del, 0) = 0");
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
        IPage<AppGoods> page = service.page(new Page<AppGoods>(current, pageSize), wrapper);
        maskGoodsPrices(page.getRecords(), param, null);
        return Result.ok(page);
    }

    private void maskStockDetail(JSONObject result) {
        String[] amountKeys = {
                "openingCostPrice", "openingAmount", "inTotal", "outTotal",
                "endingCostPrice", "endingAmount"
        };
        for (String key : amountKeys) {
            result.put(key, 0D);
        }
        Object records = result.get("records");
        if (!(records instanceof List)) {
            return;
        }
        for (Object row : (List<?>) records) {
            if (row instanceof JSONObject) {
                JSONObject item = (JSONObject) row;
                item.put("inUnitPrice", 0D);
                item.put("inAmount", 0D);
                item.put("outCostPrice", 0D);
                item.put("outAmount", 0D);
                item.put("endingCostPrice", 0D);
                item.put("endingAmount", 0D);
            }
        }
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
