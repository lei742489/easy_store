package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerCategory;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppCustomerCategoryMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerCategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AppCustomerCategoryServiceImpl extends ServiceImpl<AppCustomerCategoryMapper, AppCustomerCategory>
        implements IAppCustomerCategoryService {

    @Override
    public List<AppCustomerCategory> getList() {
        AppCustomerCategory root = new AppCustomerCategory();
        root.setParentId(-1);
        root.setId(0);
        root.setTitle("全部分类");
        root.setRoot(1);
        root.setChildren(new ArrayList<>());

        List<AppCustomerCategory> categories = list(new LambdaQueryWrapper<AppCustomerCategory>()
                .orderByDesc(AppCustomerCategory::getCreateTime)
                .orderByAsc(AppCustomerCategory::getId));
        Map<Integer, AppCustomerCategory> categoryMap = new HashMap<>();
        Set<Integer> persistedRootIds = new HashSet<>();

        for (AppCustomerCategory category : categories) {
            category.setChildren(new ArrayList<>());
            categoryMap.put(category.getId(), category);
            if (Integer.valueOf(1).equals(category.getRoot())) {
                persistedRootIds.add(category.getId());
                if (Integer.valueOf(0).equals(category.getId())) {
                    root.setTitle(category.getTitle());
                }
            }
        }

        for (AppCustomerCategory category : categories) {
            Integer categoryId = category.getId();
            if (categoryId == null || Integer.valueOf(0).equals(categoryId)
                    || (persistedRootIds.contains(categoryId) && !Integer.valueOf(0).equals(categoryId))) {
                continue;
            }

            Integer parentId = category.getParentId();
            AppCustomerCategory parent = parentId == null ? null : categoryMap.get(parentId);
            if (parent == null || parentId <= 0 || persistedRootIds.contains(parentId)) {
                root.getChildren().add(category);
            } else {
                parent.getChildren().add(category);
            }
        }

        List<AppCustomerCategory> rootList = new ArrayList<>();
        rootList.add(root);
        return rootList;
    }

    @Override
    public List<AppCustomerCategory> getListByParentId(Integer parentId) {
        return super.list(new LambdaQueryWrapper<AppCustomerCategory>()
                .orderByDesc(AppCustomerCategory::getCreateTime)
                .eq(AppCustomerCategory::getParentId, parentId));
    }

    @Override
    public boolean hasChildren(Integer parentId) {
        return count(new LambdaQueryWrapper<AppCustomerCategory>()
                .eq(AppCustomerCategory::getParentId, parentId)) > 0;
    }

    @Override
    public void checkCategoryLevel(Integer parentId) {
        if (parentId == null) {
            parentId = 0;
        }
        int level = 1;
        AppCustomerCategory parent = getById(parentId);
        while (parent != null && parent.getParentId() != null && parent.getParentId() > 0) {
            level++;
            parent = getById(parent.getParentId());
        }

        if (level >= 2) {
            throw new AppRunTimeException("最多只能添加两级分类");
        }
    }
}
