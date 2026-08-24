package org.jeecgframework.boot.easy_store_boot.app.modules.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.Map;

/**
* @author Administrator
* @description 针对表【app_purchase_order_item】的数据库操作Mapper
* @createDate 2025-07-01 08:53:50
* @Entity org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrderItem
*/
public interface AppPurchaseOrderItemMapper extends BaseMapper<AppPurchaseOrderItem> {

    @Select("SELECT\n" +
            "  i.goods_id,\n" +
            "  SUM( i.total_amount ) AS total_amount_sum,\n" +
            "  SUM( i.quantity ) AS quantity_sum,\n" +
            "  ROUND( SUM( i.total_amount ) / NULLIF( SUM( i.quantity ), 0 ), 2 ) AS purPrc \n" +
            "FROM\n" +
            "  app_purchase_order_item i \n" +
            "  LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0 \n" +
            "WHERE i.is_del = 0 and " +
            "  i.goods_id = #{goodsId} \n" +
            "  AND (i.order_id IS NULL OR o.status = 1) \n" +
            "GROUP BY\n" +
            "  i.goods_id;")
     Map<String,Double> sumPurchaseGoodsPrices(@Param("goodsId")String goodsId);

    @Select("SELECT i.unit_price AS purPrc \n" +
            "FROM app_purchase_order_item i \n" +
            "LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0 \n" +
            "WHERE i.is_del = 0 \n" +
            "  AND i.goods_id = #{goodsId} \n" +
            "  AND COALESCE(i.is_init, 0) = 0 \n" +
            "  AND (i.order_id IS NULL OR o.status = 1) \n" +
            "ORDER BY COALESCE(o.create_time, i.create_time) DESC, i.id DESC \n" +
            "LIMIT 1")
    Double findLatestPurchaseUnitPrice(@Param("goodsId") String goodsId);
}




