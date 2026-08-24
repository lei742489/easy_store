package org.jeecgframework.boot.easy_store_boot.app.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomer;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSupplier;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class CustomerSupplierKeywordResolver {

    @Autowired
    private IAppCustomerService appCustomerService;
    @Autowired
    private IAppSupplierService appSupplierService;

    public List<String> resolveCustomerIds(String rawValue) {
        return resolveIds(rawValue, true);
    }

    public List<String> resolveSupplierIds(String rawValue) {
        return resolveIds(rawValue, false);
    }

    private List<String> resolveIds(String rawValue, boolean customer) {
        String keyword = normalize(rawValue);
        if (StringUtils.isBlank(keyword)) {
            return new ArrayList<>();
        }

        Set<String> ids = new LinkedHashSet<>();
        if (keyword.matches("^\\d+$") && existsById(keyword, customer)) {
            ids.add(keyword);
            return new ArrayList<>(ids);
        }

        if (customer) {
            List<AppCustomer> list = appCustomerService.list(buildCustomerWrapper(keyword));
            for (AppCustomer item : list) {
                if (item != null && item.getId() != null) {
                    ids.add(String.valueOf(item.getId()));
                }
            }
        } else {
            List<AppSupplier> list = appSupplierService.list(buildSupplierWrapper(keyword));
            for (AppSupplier item : list) {
                if (item != null && item.getId() != null) {
                    ids.add(String.valueOf(item.getId()));
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private boolean existsById(String id, boolean customer) {
        if (customer) {
            return appCustomerService.getById(Integer.valueOf(id)) != null;
        }
        return appSupplierService.getById(Integer.valueOf(id)) != null;
    }

    private String normalize(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String keyword = rawValue.trim();
        if ("null".equalsIgnoreCase(keyword) || "undefined".equalsIgnoreCase(keyword)) {
            return null;
        }
        return keyword;
    }

    private LambdaQueryWrapper<AppCustomer> buildCustomerWrapper(String keyword) {
        return new LambdaQueryWrapper<AppCustomer>()
                .select(AppCustomer::getId)
                .and(wrapper -> wrapper.like(AppCustomer::getName, keyword)
                        .or().like(AppCustomer::getContactName, keyword)
                        .or().like(AppCustomer::getMobile, keyword)
                        .or().like(AppCustomer::getPhone, keyword)
                        .or().like(AppCustomer::getPyCode, keyword));
    }

    private LambdaQueryWrapper<AppSupplier> buildSupplierWrapper(String keyword) {
        return new LambdaQueryWrapper<AppSupplier>()
                .select(AppSupplier::getId)
                .and(wrapper -> wrapper.like(AppSupplier::getName, keyword)
                        .or().like(AppSupplier::getContactName, keyword)
                        .or().like(AppSupplier::getMobile, keyword)
                        .or().like(AppSupplier::getPhone, keyword)
                        .or().like(AppSupplier::getPyCode, keyword));
    }
}
