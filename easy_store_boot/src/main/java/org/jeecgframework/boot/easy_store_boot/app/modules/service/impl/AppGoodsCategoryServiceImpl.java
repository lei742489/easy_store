package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.ApiQuery;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoodsCategory;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppGoodsCategoryMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppGoodsCategoryMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsCategoryService;
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
public class AppGoodsCategoryServiceImpl extends ServiceImpl<AppGoodsCategoryMapper, AppGoodsCategory>
        implements IAppGoodsCategoryService {

    @Autowired
    private AppGoodsCategoryMapper mapper;

    @Override
    public List<AppGoodsCategory> getList() {

        AppGoodsCategory root =getOne(new LambdaQueryWrapper<AppGoodsCategory>().eq(AppGoodsCategory::getRoot,1).last("limit 1"));
        if(root == null) {
            root=  new AppGoodsCategory();
            root.setParentId(-1);
            root.setId(0);
            root.setTitle("全部分类");
            root.setRoot(1);
            mapper.insertWithId(root);
        }
        List<AppGoodsCategory> rootList = new ArrayList<>();
        rootList.add(root);

        for (AppGoodsCategory child : rootList) {
            // 递归设置下级子分类
            child.setChildren(getChildrenRecursively(child.getId()));
        }

        return rootList;
    }

    @Override
    public List<AppGoodsCategory> getListByParentId(Integer parentId) {
        return super.list(new LambdaQueryWrapper<AppGoodsCategory>().orderByDesc(AppGoodsCategory::getCreateTime).eq(AppGoodsCategory::getParentId,parentId));
    }

    @Override
    public boolean hasChildren(String parentId) {
        return count(new LambdaQueryWrapper<AppGoodsCategory>().eq(AppGoodsCategory::getParentId,parentId)) > 0;
    }

    @Override
    public void checkCategoryLevel(Integer parentId) {
        if(parentId==null) parentId = 0;
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
        if(id == null)
          return "";
        AppGoodsCategory category = getById(id);
        return  category == null?"":category.getTitle();
    }




    private List<AppGoodsCategory> getChildrenRecursively(Integer parentId) {
        List<AppGoodsCategory> children = list(new LambdaQueryWrapper<AppGoodsCategory>()
                .eq(AppGoodsCategory::getParentId, parentId)
                .eq(AppGoodsCategory::getRoot,0)
                .orderByDesc(AppGoodsCategory::getCreateTime)
        );

        for (AppGoodsCategory child : children) {
            // 递归设置下级子分类
            child.setChildren(getChildrenRecursively(child.getId()));
        }

        return children;
    }

}




