package org.jeecgframework.boot.easy_store_boot.app.modules.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSaleOrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.Map;

/**
* @author Administrator
* @description 针对表【app_sale_order_item】的数据库操作Mapper
* @createDate 2025-07-09 09:45:18
* @Entity org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSaleOrderItem
*/
public interface AppSaleOrderItemMapper extends BaseMapper<AppSaleOrderItem> {

    @Select("SELECT\n" +
            "  i.goods_id,\n" +
            "  SUM( i.total_amount ) AS total_amount_sum,\n" +
            "  SUM( i.quantity ) AS quantity_sum,\n" +
            "  ROUND( SUM( i.total_amount ) / NULLIF( SUM( i.quantity ), 0 ), 2 ) AS salePrc \n" +
            "FROM\n" +
            "  app_sale_order_item i \n" +
            "  INNER JOIN app_sale_order o ON o.id = i.order_id AND o.is_del = 0 AND o.status = 1 \n" +
            "WHERE i.is_del = 0 and " +
            "  i.goods_id = #{goodsId} \n" +
            "GROUP BY\n" +
            "  i.goods_id;")
    Map<String,Double> sumPurchaseGoodsSalePrc(@Param("goodsId")String goodsId);
}




