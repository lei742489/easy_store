package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.permission.AppPermissionDefinition;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerQuote;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerQuoteService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/user/appCustomerQuote")
public class AppCustomerQuoteController extends ApiBaseController<AppCustomerQuote, IAppCustomerQuoteService> {

    @Override
    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        Integer current = param.getInteger("current");
        Integer pageSize = param.getInteger("pageSize");
        if (current == null) current = 1;
        if (pageSize == null) pageSize = 50;

        QueryWrapper<AppCustomerQuote> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_del", 0);

        String customerId = param.getString("customerId");
        if (StringUtils.isNotEmpty(customerId)) {
            queryWrapper.eq("customer_id", customerId);
        }

        String goodsId = param.getString("goodsId");
        if (StringUtils.isNotEmpty(goodsId)) {
            queryWrapper.eq("goods_id", goodsId);
        }

        addKeywordQuery(queryWrapper, "customer", param.getString("customerKey"));
        addKeywordQuery(queryWrapper, "goods", param.getString("goodsKey"));

        String column = param.getString("column");
        String order = param.getString("order");
        if (StringUtils.isNotEmpty(column)) {
            String dbColumn = camelToUnderline(column);
            if (StringUtils.isNotEmpty(order) && order.toLowerCase().contains("asc")) {
                queryWrapper.orderByAsc(dbColumn);
            } else {
                queryWrapper.orderByDesc(dbColumn);
            }
        } else {
            queryWrapper.orderByDesc("id");
        }

        Page<AppCustomerQuote> page = new Page<>(current, pageSize);
        IPage<AppCustomerQuote> pageList = service.page(page, queryWrapper);
        maskQuotes(pageList.getRecords(), param);
        return Result.ok(pageList);
    }

    @Override
    @PostMapping("add")
    public Result<?> add(@RequestBody JSONObject param) {
        service.saveQuote(JSONObject.toJavaObject(param, AppCustomerQuote.class));
        return Result.ok();
    }

    @Override
    @PostMapping("edit")
    public Result<?> edit(@RequestBody JSONObject param) {
        service.updateQuote(JSONObject.toJavaObject(param, AppCustomerQuote.class));
        return Result.ok();
    }

    @PostMapping("batchAdd")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> batchAdd(@RequestBody JSONObject param) {
        JSONArray rows = param.getJSONArray("rows");
        if (rows == null || rows.isEmpty()) {
            throw new AppRunTimeException("请录入报价明细");
        }
        List<AppCustomerQuote> list = rows.toJavaList(AppCustomerQuote.class);
        service.saveQuotes(list);
        return Result.ok();
    }

    @PostMapping("batchUpdatePrice")
    public Result<?> batchUpdatePrice(@RequestBody JSONObject param) {
        service.batchUpdatePrice(param.getString("ids"), param.getDouble("quotePrice"));
        return Result.ok();
    }

    @PostMapping("getQuoteByCustomerAndGoods")
    public Result<?> getQuoteByCustomerAndGoods(@RequestBody JSONObject param) {
        String customerId = param.getString("customerId");
        String goodsId = param.getString("goodsId");
        if (StringUtils.isEmpty(customerId) || StringUtils.isEmpty(goodsId)) {
            return Result.ok(null);
        }
        AppCustomerQuote quote = service.getOne(new QueryWrapper<AppCustomerQuote>()
                .eq("is_del", 0)
                .eq("customer_id", customerId)
                .eq("goods_id", goodsId)
                .orderByDesc("id")
                .last("limit 1"));
        if (quote != null) {
            maskQuotes(java.util.Collections.singletonList(quote), param);
        }
        return Result.ok(quote);
    }

    @PostMapping("batchRemove")
    public Result<?> batchRemove(@RequestBody JSONObject param) {
        service.batchRemove(param.getString("ids"));
        return Result.ok();
    }

    private void addKeywordQuery(QueryWrapper<AppCustomerQuote> queryWrapper, String prefix, String key) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        String pyColumn = prefix + "_py_code";
        if ("goods".equals(prefix)) {
            queryWrapper.and(item -> item.like("goods_title", key).or().like(pyColumn, key));
        } else {
            queryWrapper.and(item -> item.like("customer_name", key).or().like(pyColumn, key));
        }
    }

    private String camelToUnderline(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void maskQuotes(List<AppCustomerQuote> quotes, JSONObject param) {
        if (quotes == null || quotes.isEmpty()) return;
        boolean showSalePrice = hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_SALE_PRICE);
        boolean showTradePrice = hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_TRADE_PRICE);
        for (AppCustomerQuote quote : quotes) {
            if (quote == null) continue;
            if (!showSalePrice) quote.setSalePrc(0D);
            if (!showTradePrice) {
                quote.setTradePrc(0D);
                quote.setQuotePrice(0D);
            }
        }
    }
}
