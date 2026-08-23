package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoodsCategory;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppGoodsCategoryMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsCategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AppGoodsCategoryServiceImpl extends ServiceImpl<AppGoodsCategoryMapper, AppGoodsCategory>
        implements IAppGoodsCategoryService {

    @Override
    public List<AppGoodsCategory> getList() {
        AppGoodsCategory root = new AppGoodsCategory();
        root.setParentId(-1);
        root.setId(0);
        root.setTitle("全部分类");
        root.setRoot(1);
        root.setChildren(new ArrayList<>());

        List<AppGoodsCategory> categories = list(new LambdaQueryWrapper<AppGoodsCategory>()
                .orderByDesc(AppGoodsCategory::getCreateTime)
                .orderByAsc(AppGoodsCategory::getId));
        Map<Integer, AppGoodsCategory> categoryMap = new HashMap<>();
        Set<Integer> persistedRootIds = new HashSet<>();

        for (AppGoodsCategory category : categories) {
            category.setChildren(new ArrayList<>());
            categoryMap.put(category.getId(), category);
            if (Integer.valueOf(1).equals(category.getRoot())) {
                persistedRootIds.add(category.getId());
                if (Integer.valueOf(0).equals(category.getId())) {
                    root.setTitle(category.getTitle());
                }
            }
        }

        // The database has used both id=0 and non-zero persisted root nodes.
        // Always return one virtual id=0 root so all front-end category selectors agree.
        for (AppGoodsCategory category : categories) {
            Integer categoryId = category.getId();
            if (categoryId == null || Integer.valueOf(0).equals(categoryId)
                    || (persistedRootIds.contains(categoryId) && !Integer.valueOf(0).equals(categoryId))) {
                continue;
            }

            Integer parentId = category.getParentId();
            AppGoodsCategory parent = parentId == null ? null : categoryMap.get(parentId);
            if (parent == null || parentId <= 0 || persistedRootIds.contains(parentId)) {
                root.getChildren().add(category);
            } else {
                parent.getChildren().add(category);
            }
        }

        List<AppGoodsCategory> rootList = new ArrayList<>();
        rootList.add(root);
        return rootList;
    }

    @Override
    public List<AppGoodsCategory> getListByParentId(Integer parentId) {
        return super.list(new LambdaQueryWrapper<AppGoodsCategory>()
                .orderByDesc(AppGoodsCategory::getCreateTime)
                .eq(AppGoodsCategory::getParentId, parentId));
    }

    @Override
    public boolean hasChildren(String parentId) {
        return count(new LambdaQueryWrapper<AppGoodsCategory>()
                .eq(AppGoodsCategory::getParentId, parentId)) > 0;
    }

    @Override
    public void checkCategoryLevel(Integer parentId) {
        if (parentId == null) {
            parentId = 0;
        }
        int level = 1;
        AppGoodsCategory parent = getById(parentId);
        while (parent != null && parent.getParentId() != null && parent.getParentId() > 0) {
            level++;
            parent = getById(parent.getParentId());
        }

        if (level >= 3) {
            throw new AppRunTimeException("最多只能添加3级分类");
        }
    }

    @Override
    public String getTitleById(Integer id) {
        if (id == null) {
            return "";
        }
        AppGoodsCategory category = getById(id);
        return category == null ? "" : category.getTitle();
    }
}
