package org.jeecgframework.boot.easy_store_boot.app.modules.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.ApiQuery;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.GoodsSearchResult;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_goods】的数据库操作Mapper
* @createDate 2025-06-27 10:35:51
* @Entity org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods
*/
public interface AppGoodsMapper extends BaseMapper<AppGoods> {

    List<GoodsSearchResult> searchByKey(@Param("key")String key, @Param("pyCode")String pyCode,
                                        @Param("pageNo") Integer pageNo, @Param("pageSize") Integer pageSize,
                                        @Param("hideZeroStock") Boolean hideZeroStock,
                                        @Param("searchType") String searchType);

    IPage<AppGoods> search(IPage page, @Param("query")ApiQuery query);
}




