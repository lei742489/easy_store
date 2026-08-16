package org.jeecgframework.boot.easy_store_boot.app.modules.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.DictModel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.SysDict;

/**
 * <p>
 * 字典表 Mapper 接口
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-28
 */
public interface SysDictMapper extends BaseMapper<SysDict> {

	 List<DictModel> queryDictItemsByCode(@Param("code") String code);

	 List<DictModel> queryTableDictItemsByCode(@Param("table") String table,@Param("text") String text,@Param("code") String code);


}
