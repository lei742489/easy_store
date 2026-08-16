package org.jeecgframework.boot.easy_store_boot.app.modules.service;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.ApiQuery;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoodsCategory;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_goods_category】的数据库操作Service
* @createDate 2025-06-22 13:42:42
*/
public interface IAppGoodsCategoryService extends IService<AppGoodsCategory> {

    List<AppGoodsCategory> getList();

    List<AppGoodsCategory> getListByParentId(Integer parentId);

    boolean hasChildren(String parentId);

    void   checkCategoryLevel(Integer parentId);

    String getTitleById(Integer id);


}
