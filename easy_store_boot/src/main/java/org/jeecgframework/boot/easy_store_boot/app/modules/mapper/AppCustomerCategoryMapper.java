package org.jeecgframework.boot.easy_store_boot.app.modules.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerCategory;

/**
* @author Administrator
* @description 针对表【app_customer_category】的数据库操作Mapper
* @createDate 2025-06-22 13:42:42
* @Entity generator.entity.AppCustomerCategory
*/
public interface AppCustomerCategoryMapper extends BaseMapper<AppCustomerCategory> {

    @Insert("INSERT INTO app_customer_category (id, title,root,parent_id) VALUES (#{id}, #{title},#{root},#{parentId})")
    int insertWithId(AppCustomerCategory category);
}




