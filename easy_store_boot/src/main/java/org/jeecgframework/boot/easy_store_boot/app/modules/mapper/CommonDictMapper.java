package org.jeecgframework.boot.easy_store_boot.app.modules.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommonDictMapper {
    @Select("SELECT ${dicText} FROM ${dictTable} WHERE ${dicCode} = #{codeValue}")
    String selectDictText(
        @Param("dictTable") String dictTable,
        @Param("dicText") String dicText,
        @Param("dicCode") String dicCode,
        @Param("codeValue") Object codeValue
    );
}