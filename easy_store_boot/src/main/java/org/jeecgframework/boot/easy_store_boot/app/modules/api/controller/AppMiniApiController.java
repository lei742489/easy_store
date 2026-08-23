package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.common.query.QueryGenerator;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.ApiQuery;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomer;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSaleOrder;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSaleOrderItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSaleOrderService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSaleOrderItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 小程序类接口
 */
@RestController
@RequestMapping("api/mini")
public class AppMiniApiController {

    @Autowired
    private IAppGoodsService appGoodsService;

    @Autowired
    private IAppSaleOrderService appSaleOrderService;

    @Autowired
    private IAppSaleOrderItemService appSaleOrderItemService;

    @Autowired
    private IAppCustomerService appCustomerService;

    @Autowired
    private IAppUserService appUserService;

    @PostMapping("queryGoods")
    public Result<?> queryGoods(@RequestBody JSONObject param) {

        AppGoods entity = JSONObject.toJavaObject(param, AppGoods.class);
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
            return Result.ok(appGoodsService.page(page, queryWrapper));
        }else{
            ApiQuery query = JSONObject.toJavaObject(param, ApiQuery.class);
            query.setKey(entity.getTitle());
            query.setZeroStock(param.getBoolean("zeroStock"));
            return Result.ok(appGoodsService.search(query));
        }
    }

    @PostMapping("searchKey")
    public Result<?> searchKey(@RequestBody JSONObject param) {

        return Result.ok(appGoodsService.searchByKey(1,param.getString("key")));
    }

    /**
     * 接收其它项目的销售单数据并转换为当前进销存销售单。
     *
     * 外部字段：
     * customer、payableMount、userId、goodsList。
     * 当前系统固定使用客户 ID 2，营业员通过 realName 查询。
     */
    @PostMapping("saleOrder/save")
    public Result<?> saveSaleOrder(@RequestBody JSONObject param) {
        AppSaleOrder order = convertSaleOrder(param);
        appSaleOrderService.save(order);
        return Result.ok(order);
    }

    /**
     * 查询销售单并转换为另一套项目的 AppSaleOrder 返回结构。
     */
    @PostMapping("saleOrder/list")
    public Result<?> listSaleOrder(@RequestBody JSONObject param) {
        AppUser queryUser = resolveRealNameUser(param);
        boolean rootUser = queryUser.getIsRoot() != null && queryUser.getIsRoot() == 1;

        Integer current = param.getInteger("pageNo");
        Integer pageSize = param.getInteger("pageSize");
        current = current == null || current < 1 ? 1 : current;
        pageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;

        QueryWrapper<AppSaleOrder> wrapper = new QueryWrapper<>();
        //wrapper.eq("status", 1);
        if (!rootUser) {
            wrapper.eq("cashier_id", queryUser.getId());
        }

        applySaleOrderDateFilter(wrapper, param.getString("start"), param.getString("end"));
        applySaleOrderKeywordFilter(wrapper, param.getString("key"));
        wrapper.orderByDesc("create_time").orderByDesc("id");

        IPage<AppSaleOrder> page = appSaleOrderService.page(
                new Page<>(current, pageSize), wrapper);
        List<JSONObject> records = new ArrayList<>();
        for (AppSaleOrder order : page.getRecords()) {
            records.add(toExternalSaleOrder(order, rootUser));
        }

        Page<JSONObject> resultPage = new Page<>(current, pageSize, page.getTotal());
        resultPage.setRecords(records);
        return Result.ok(resultPage);
    }

    /**
     * 查询销售单详情并转换为另一套项目的订单结构。
     * 详情查询不校验营业员和 ROOT 权限，由调用方根据订单 ID 查询。
     */
    @PostMapping("saleOrder/info")
    public Result<?> saleOrderInfo(@RequestBody JSONObject param) {
        if (param == null || StringUtils.isBlank(param.getString("id"))) {
            throw new AppRunTimeException("销售单 ID 不能为空");
        }
        AppSaleOrder order = appSaleOrderService.getById(param.getString("id").trim());
        if (order == null) {
            throw new AppRunTimeException("销售单不存在");
        }
        return Result.ok(toExternalSaleOrder(order, true));
    }

    private AppUser resolveRealNameUser(JSONObject param) {
        String realName = param == null ? null : param.getString("realName");
        if (StringUtils.isBlank(realName)) {
            throw new AppRunTimeException("营业员姓名不能为空");
        }
        AppUser user = appUserService.getByName(realName.trim());
        if (user == null || user.getId() == null) {
            throw new AppRunTimeException("未找到营业员：" + realName);
        }
        return user;
    }

    private void applySaleOrderKeywordFilter(QueryWrapper<AppSaleOrder> wrapper, String key) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        String keyword = key.trim();
        List<AppCustomer> customerList = appCustomerService.list(
                new LambdaQueryWrapper<AppCustomer>()
                        .select(AppCustomer::getId)
                        .like(AppCustomer::getName, keyword));
        Set<String> customerIds = new LinkedHashSet<>();
        for (AppCustomer customer : customerList) {
            if (customer.getId() != null) {
                customerIds.add(String.valueOf(customer.getId()));
            }
        }

        List<AppGoods> goodsList = appGoodsService.list(
                new LambdaQueryWrapper<AppGoods>()
                        .select(AppGoods::getId)
                        .like(AppGoods::getTitle, keyword));
        Set<String> goodsIds = new LinkedHashSet<>();
        for (AppGoods goods : goodsList) {
            if (goods.getId() != null) {
                goodsIds.add(String.valueOf(goods.getId()));
            }
        }

        List<Integer> orderIds = new ArrayList<>();
        if (!goodsIds.isEmpty()) {
            List<AppSaleOrderItem> itemList = appSaleOrderItemService.list(
                    new LambdaQueryWrapper<AppSaleOrderItem>()
                            .select(AppSaleOrderItem::getOrderId)
                            .in(AppSaleOrderItem::getGoodsId, goodsIds));
            for (AppSaleOrderItem item : itemList) {
                if (item.getOrderId() != null) {
                    orderIds.add(item.getOrderId());
                }
            }
        }

        wrapper.and(query -> {
            query.like("order_no", keyword);
            if (!customerIds.isEmpty()) {
                query.or().in("customer_id", customerIds);
            }
            if (!orderIds.isEmpty()) {
                query.or().in("id", orderIds);
            }
            return query;
        });
    }

    private void applySaleOrderDateFilter(QueryWrapper<AppSaleOrder> wrapper,
                                          String startValue, String endValue) {
        Date start = parseExternalDate(startValue, false);
        Date end = parseExternalDate(endValue, true);
        if (start != null) {
            wrapper.ge("create_time", start);
        }
        if (end != null) {
            wrapper.le("create_time", end);
        }
    }

    private Date parseExternalDate(String value, boolean endOfDay) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String text = value.trim();
        List<String> patterns = Arrays.asList(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd");
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern);
                format.setLenient(false);
                Date date = format.parse(text);
                if (endOfDay && "yyyy-MM-dd".equals(pattern)) {
                    date = new Date(date.getTime() + 24 * 60 * 60 * 1000L - 1);
                }
                return date;
            } catch (ParseException ignored) {
                // Try the next supported date format.
            }
        }
        throw new AppRunTimeException("日期格式错误：" + value);
    }

    private JSONObject toExternalSaleOrder(AppSaleOrder order, boolean rootUser) {
        JSONObject result = new JSONObject();
        result.put("id", order.getId() == null ? null : String.valueOf(order.getId()));
        result.put("orderNo", order.getOrderNo());
        result.put("orderType", order.getOrderType());
        result.put("payableMount", order.getPayableAmount());
        result.put("paidAmount", order.getPaidAmount());
        result.put("profit", rootUser ? order.getGrossProfit() : null);
        result.put("settleId", order.getSettleId());
        result.put("userId", order.getCashierId());
        result.put("userName", order.getCashierName());
        result.put("note", order.getNote());
        result.put("createTime", formatOrderDate(order.getCreateTime()));

        AppCustomer customer = StringUtils.isBlank(order.getCustomerId())
                ? null
                : appCustomerService.getById(order.getCustomerId());
        result.put("customer", customer == null ? "" : customer.getName());

        List<JSONObject> goodsList = new ArrayList<>();
        List<AppSaleOrderItem> items = appSaleOrderItemService.listByOrderId(order.getId());
        appSaleOrderService.fillPendingGoodsNames(order.getId(), items);
        for (AppSaleOrderItem item : items) {
            JSONObject goods = new JSONObject();
            goods.put("id", item.getId() == null ? null : String.valueOf(item.getId()));
            goods.put("orderId", order.getId() == null ? null : String.valueOf(order.getId()));
            goods.put("num", item.getQuantity() == null ? 0 : Math.abs(item.getQuantity()));
            goods.put("price", item.getUnitPrice());
            goods.put("profit", rootUser ? item.getGrossProfit() : null);
            goods.put("note", item.getNote());
            goods.put("createTime", item.getCreateTime());
            goods.put("updateTime", item.getUpdateTime());

            AppGoods itemGoods = StringUtils.isBlank(item.getGoodsId())
                    ? null
                    : appGoodsService.getById(item.getGoodsId());
            if (itemGoods != null) {
                goods.put("goodCode", itemGoods.getGoodsCode());
                goods.put("title", itemGoods.getTitle());
            } else {
                goods.put("goodCode", null);
                goods.put("title", item.getGoodsName());
            }
            goodsList.add(goods);
        }
        result.put("goodsList", goodsList);
        return result;
    }

    private AppSaleOrder convertSaleOrder(JSONObject param) {
        if (param == null) {
            throw new AppRunTimeException("销售单数据不能为空");
        }

        String orderNo = param.getString("orderNo");
        if (StringUtils.isBlank(orderNo)) {
            throw new AppRunTimeException("销售单号不能为空");
        }

        String realName = param.getString("realName");
        if (StringUtils.isBlank(realName)) {
            throw new AppRunTimeException("营业员姓名不能为空");
        }
        AppUser cashier = appUserService.getByName(realName.trim());
        if (cashier == null || cashier.getId() == null) {
            throw new AppRunTimeException("未找到营业员：" + realName);
        }

        Integer orderType = param.getInteger("orderType");
        if (orderType == null) {
            orderType = 1;
        }

        AppSaleOrder order = new AppSaleOrder();
        order.setOrderNo(orderNo);
        order.setCustomerId("2");
        order.setOrderType(orderType);
        order.setSettleId(param.getString("settleId"));
        order.setCashierId(String.valueOf(cashier.getId()));
        order.setCashierName(cashier.getRealName());
        order.setStatus(0);
        order.setPaidAmount(defaultDouble(param.getDouble("paidAmount")));
        order.setFreightAmount(defaultDouble(param.getDouble("freightAmount")));
        order.setNote(param.getString("note"));
        Date createTime = param.getDate("createTime");
        if (createTime != null) {
            order.setCreateTime(createTime);
        }

        List<AppSaleOrderItem> items = convertSaleOrderItems(param, orderType);
        if (items.isEmpty()) {
            throw new AppRunTimeException("销售单至少需要一条商品明细");
        }
        order.setItems(items);

        double totalAmount = defaultDouble(param.getDouble("totalAmount"));
        if (totalAmount == 0D) {
            totalAmount = items.stream()
                    .mapToDouble(item -> Math.abs(defaultDouble(item.getTotalAmount())))
                    .sum();
        }
        Double payableAmount = param.getDouble("payableMount");
        if (payableAmount == null) {
            payableAmount = totalAmount;
        }
        order.setTotalAmount(money(totalAmount));
        order.setPayableAmount(money(payableAmount));
        order.setDiscountedAmount(money(payableAmount));
        order.setDiscountRate(totalAmount == 0D
                ? 100D
                : money(BigDecimal.valueOf(payableAmount)
                .multiply(BigDecimal.valueOf(100D))
                .divide(BigDecimal.valueOf(totalAmount), 4, RoundingMode.HALF_UP)));

        return order;
    }

    private List<AppSaleOrderItem> convertSaleOrderItems(JSONObject param, Integer orderType) {
        List<AppSaleOrderItem> items = new ArrayList<>();
        if (param.getJSONArray("goodsList") == null) {
            return items;
        }

        for (Object value : param.getJSONArray("goodsList")) {
            JSONObject sourceItem = value instanceof JSONObject
                    ? (JSONObject) value
                    : JSONObject.parseObject(JSONObject.toJSONString(value));
            String title = sourceItem.getString("title");
            String goodsCode = sourceItem.getString("goodCode");
            String sourceGoodsId = firstNotBlank(sourceItem.getString("goodsId"), sourceItem.getString("id"));
            AppGoods goods = findGoods(sourceGoodsId, goodsCode, title);
            String displayGoodsName = goods == null ? title : goods.getTitle();
            Integer sourceQuantity = sourceItem.getInteger("num");
            if (sourceQuantity == null || sourceQuantity == 0) {
                throw new AppRunTimeException("商品数量不能为空：" + displayGoodsName);
            }
            int quantity = Math.abs(sourceQuantity);
            if (orderType != null && orderType == 2) {
                quantity = -quantity;
            }

            Double unitPrice = sourceItem.getDouble("price");
            if (unitPrice == null) {
                unitPrice = 0D;
            }
            Double totalAmount = sourceItem.getDouble("totalAmount");
            if (totalAmount == null) {
                totalAmount = Math.abs(sourceQuantity) * unitPrice;
            }

            AppSaleOrderItem item = new AppSaleOrderItem();
            if (goods != null) {
                item.setGoodsId(String.valueOf(goods.getId()));
                item.setGoodsName(goods.getTitle());
                item.setCategoryId(goods.getCategoryId());
                item.setUnit(StringUtils.isBlank(sourceItem.getString("unit"))
                        ? goods.getUnit()
                        : sourceItem.getString("unit"));
            } else {
                if (StringUtils.isBlank(title)) {
                    throw new AppRunTimeException("请输入商品名称");
                }
                item.setGoodsId(null);
                item.setGoodsName(title.trim());
                item.setCategoryId(firstNotBlank(sourceItem.getString("categoryId"), "1"));
                item.setUnit(sourceItem.getString("unit"));
            }
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setTotalAmount(totalAmount);
            item.setNote(sourceItem.getString("note"));
            items.add(item);
        }
        return items;
    }

    private AppGoods findGoods(String goodsId, String goodsCode, String title) {
        if (StringUtils.isNotBlank(goodsId) && StringUtils.isNumeric(goodsId.trim())) {
            AppGoods goods = appGoodsService.getById(goodsId.trim());
            if (goods != null) {
                return goods;
            }
        }
        if (StringUtils.isNotBlank(goodsCode)) {
            AppGoods goods = appGoodsService.getOne(new LambdaQueryWrapper<AppGoods>()
                    .eq(AppGoods::getGoodsCode, goodsCode.trim())
                    .last("limit 1"));
            if (goods != null) {
                return goods;
            }
        }
        if (StringUtils.isNotBlank(title)) {
            return appGoodsService.getOne(new LambdaQueryWrapper<AppGoods>()
                    .eq(AppGoods::getTitle, title.trim())
                    .last("limit 1"));
        }
        return null;
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Double defaultDouble(Double value) {
        return value == null ? 0D : value;
    }

    private String formatOrderDate(Date date) {
        return date == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    private Double money(double value) {
        return money(BigDecimal.valueOf(value));
    }

    private Double money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
