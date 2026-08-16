package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomer;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerQuote;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppCustomerQuoteMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerQuoteService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AppCustomerQuoteServiceImpl extends ServiceImpl<AppCustomerQuoteMapper, AppCustomerQuote>
        implements IAppCustomerQuoteService {

    @Autowired
    private IAppCustomerService customerService;
    @Autowired
    private IAppGoodsService goodsService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveQuote(AppCustomerQuote entity) {
        fillSnapshot(entity);
        AppCustomerQuote existing = getQuoteByCustomerAndGoods(entity);
        if (existing != null) {
            entity.setId(existing.getId());
            if (!updateById(entity)) {
                throw new AppRunTimeException("更新失败");
            }
            return;
        }
        if (!save(entity)) {
            throw new AppRunTimeException("添加失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuote(AppCustomerQuote entity) {
        fillSnapshot(entity);
        AppCustomerQuote duplicate = getDuplicate(entity);
        if (duplicate != null) {
            Integer sourceId = entity.getId();
            entity.setId(duplicate.getId());
            if (!updateById(entity)) {
                throw new AppRunTimeException("更新失败");
            }
            if (sourceId != null && !sourceId.equals(duplicate.getId())) {
                removeById(sourceId);
            }
            return;
        }
        if (!updateById(entity)) {
            throw new AppRunTimeException("更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveQuotes(List<AppCustomerQuote> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (AppCustomerQuote entity : distinctQuotes(list)) {
            saveQuote(entity);
        }
    }

    @Override
    public void batchUpdatePrice(String ids, Double quotePrice) {
        if (StringUtils.isEmpty(ids) || quotePrice == null) {
            throw new AppRunTimeException("参数错误");
        }
        update(new LambdaUpdateWrapper<AppCustomerQuote>()
                .in(AppCustomerQuote::getId, Arrays.asList(ids.split(",")))
                .set(AppCustomerQuote::getQuotePrice, quotePrice));
    }

    @Override
    public void batchRemove(String ids) {
        if (StringUtils.isEmpty(ids)) {
            throw new AppRunTimeException("参数错误");
        }
        removeByIds(Arrays.asList(ids.split(",")));
    }

    private void fillSnapshot(AppCustomerQuote entity) {
        if (entity == null || StringUtils.isEmpty(entity.getCustomerId()) || StringUtils.isEmpty(entity.getGoodsId())) {
            throw new AppRunTimeException("请选择客户和商品");
        }
        if (entity.getQuotePrice() == null) {
            throw new AppRunTimeException("请填写报价");
        }

        AppCustomer customer = customerService.getById(entity.getCustomerId());
        if (customer == null) {
            throw new AppRunTimeException("客户不存在");
        }
        AppGoods goods = goodsService.getById(entity.getGoodsId());
        if (goods == null) {
            throw new AppRunTimeException("商品不存在");
        }

        entity.setCustomerName(customer.getName());
        entity.setCustomerPyCode(customer.getPyCode());
        entity.setGoodsTitle(goods.getTitle());
        entity.setGoodsPyCode(goods.getPyCode());
        if (StringUtils.isEmpty(entity.getUnit())) {
            entity.setUnit(goods.getUnit());
        }
        entity.setSalePrc(goods.getSalePrc());
        entity.setTradePrc(goods.getTradePrc());
    }

    private AppCustomerQuote getQuoteByCustomerAndGoods(AppCustomerQuote entity) {
        return getOne(new LambdaQueryWrapper<AppCustomerQuote>()
                .eq(AppCustomerQuote::getCustomerId, entity.getCustomerId())
                .eq(AppCustomerQuote::getGoodsId, entity.getGoodsId())
                .last("limit 1"));
    }

    private AppCustomerQuote getDuplicate(AppCustomerQuote entity) {
        return getOne(new LambdaQueryWrapper<AppCustomerQuote>()
                .eq(AppCustomerQuote::getCustomerId, entity.getCustomerId())
                .eq(AppCustomerQuote::getGoodsId, entity.getGoodsId())
                .ne(entity.getId() != null, AppCustomerQuote::getId, entity.getId())
                .last("limit 1"));
    }

    private List<AppCustomerQuote> distinctQuotes(List<AppCustomerQuote> list) {
        Map<String, AppCustomerQuote> map = new LinkedHashMap<>();
        for (AppCustomerQuote entity : list) {
            if (entity == null || StringUtils.isEmpty(entity.getCustomerId()) || StringUtils.isEmpty(entity.getGoodsId())) {
                continue;
            }
            map.put(entity.getCustomerId() + "_" + entity.getGoodsId(), entity);
        }
        return new ArrayList<>(map.values());
    }
}
