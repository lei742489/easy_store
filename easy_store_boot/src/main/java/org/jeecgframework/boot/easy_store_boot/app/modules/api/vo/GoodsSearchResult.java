package org.jeecgframework.boot.easy_store_boot.app.modules.api.vo;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

@Data
public class GoodsSearchResult {

    private String value;

    private String label;

    private Integer goodsId;

    private Integer  categoryId;

    private String  categoryName;

    private String unit;

    private Double purPrc;

    private Double salePrc;

    private Double tradePrc;

    private Double costPrice;

    private Integer stock;


}
