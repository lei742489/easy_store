package org.jeecgframework.boot.easy_store_boot.app.modules.mapper;

import org.apache.ibatis.annotations.Insert;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoodsCategory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author Administrator
* @description 针对表【app_goods_category】的数据库操作Mapper
* @createDate 2025-06-26 13:23:50
* @Entity org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoodsCategory
*/
public interface AppGoodsCategoryMapper extends BaseMapper<AppGoodsCategory> {

    @Insert("INSERT INTO app_goods_category (id, title,py_code,root,parent_id) VALUES (#{id}, #{title},#{pyCode}, #{root},#{parentId})")
    int insertWithId(AppGoodsCategory category);
}




