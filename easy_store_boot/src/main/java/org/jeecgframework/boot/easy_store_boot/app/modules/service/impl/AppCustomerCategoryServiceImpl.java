package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerCategory;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerCategory;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerCategory;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppCustomerCategoryMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Administrator
 * @description 针对表【app_customer_category】的数据库操作Service实现
 * @createDate 2025-06-22 13:42:42
 */
@Service
public class AppCustomerCategoryServiceImpl extends ServiceImpl<AppCustomerCategoryMapper, AppCustomerCategory>
        implements IAppCustomerCategoryService {

    @Autowired
    private AppCustomerCategoryMapper mapper;

    @Override
    public List<AppCustomerCategory> getList() {
        AppCustomerCategory root =getOne(new LambdaQueryWrapper<AppCustomerCategory>().eq(AppCustomerCategory::getRoot,1).last("limit 1"));
        if(root == null) {
            root=  new AppCustomerCategory();
            root.setParentId(-1);
            root.setId(0);
            root.setTitle("全部分类");
            root.setRoot(1);
            mapper.insertWithId(root);
        }
        List<AppCustomerCategory> rootList = new ArrayList<>();
        rootList.add(root);

        for (AppCustomerCategory child : rootList) {
            // 递归设置下级子分类
            child.setChildren(getChildrenRecursively(child.getId()));
        }

        return rootList;
    }

    @Override
    public List<AppCustomerCategory> getListByParentId(Integer parentId) {
        return super.list(new LambdaQueryWrapper<AppCustomerCategory>().orderByDesc(AppCustomerCategory::getCreateTime).eq(AppCustomerCategory::getParentId,parentId));
    }

    @Override
    public boolean hasChildren(Integer parentId) {
        return count(new LambdaQueryWrapper<AppCustomerCategory>().eq(AppCustomerCategory::getParentId,parentId)) > 0;
    }

    @Override
    public void checkCategoryLevel(Integer parentId) {
        if(parentId==null) parentId = 0;
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

    private List<AppCustomerCategory> getChildrenRecursively(Integer parentId) {
        List<AppCustomerCategory> children = list(new LambdaQueryWrapper<AppCustomerCategory>()
                .eq(AppCustomerCategory::getParentId, parentId)
                .eq(AppCustomerCategory::getRoot,0)
                .orderByDesc(AppCustomerCategory::getCreateTime)
        );

        for (AppCustomerCategory child : children) {
            // 递归设置下级子分类
            child.setChildren(getChildrenRecursively(child.getId()));
        }

        return children;
    }


}




