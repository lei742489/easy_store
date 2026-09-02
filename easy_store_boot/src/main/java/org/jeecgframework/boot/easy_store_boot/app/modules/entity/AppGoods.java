package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.boot.easy_store_boot.app.common.anno.Letters;
import org.jeecgframework.boot.easy_store_boot.app.common.anno.Required;
import org.jeecgframework.boot.easy_store_boot.app.common.dict.DictField;
import org.jeecgframework.poi.excel.annotation.Excel;

/**
 * 
 * @TableName app_goods
 */
@TableName(value ="app_goods")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_goods", description="货品管理")
public class AppGoods implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("货品名称")
    @Excel(name = "货品名称", width = 60)
    @Letters(code="pyCode")
    private String title;

    private String pyCode;

    @ApiModelProperty("供应商货品名")
    @Excel(name = "供应商货品名", width = 15)
    @Required
    private String supplierTitle;

    @ApiModelProperty("货品分类")
    @DictField(dictTable = "app_goods_category", dicText = "title", dicCode = "id")
    @Excel(name = "货品分类", width = 15, dictTable = "app_goods_category", dicText = "title", dicCode = "id")
    private String categoryId;

    @ApiModelProperty("货品代码")
    @Excel(name = "货品代码", width = 15)
    private String goodsCode;

    @ApiModelProperty("缩略图")
    @Excel(name = "缩略图", width = 15)
    private String imgUrl;

    @ApiModelProperty("初始成本")
    @Excel(name = "初始成本", width = 15)
    private Integer initCost;

    @ApiModelProperty("初始库存")
    @Excel(name = "初始库存", width = 15)
    private Double initStock;

    @ApiModelProperty("当前库存")
    @Excel(name = "当前库存", width = 15)
    private Double stock;

    @ApiModelProperty("库存总成本")
    @Excel(name = "库存总成本", width = 15,numFormat = "0.00")
    private Double stockCost;

    @ApiModelProperty("当前平均成本价")
    @Excel(name = "成本价", width = 15,numFormat = "0.00")
    private Double costPrice;

    @ApiModelProperty("单位")
    @Excel(name = "单位", width = 15)
    private String unit;

    @ApiModelProperty("零售价")
    @Excel(name = "零售价", width = 15,numFormat = "0.00")
    private Double salePrc;

    @ApiModelProperty("批发价")
    @Excel(name = "批发价", width = 15,numFormat = "0.00")
    private Double tradePrc;

    @ApiModelProperty("进货价")
    @Excel(name = "进货价", width = 15,numFormat = "0.00")
    private Double purPrc;

    @ApiModelProperty("最大库存")
    @Excel(name = "最大库存", width = 15)
    private Double maxStock;

    @ApiModelProperty("最小库存")
    @Excel(name = "最小库存", width = 15)
    private Double minStock;

    @ApiModelProperty("供应商")
    @DictField(dictTable = "app_supplier", dicText = "name", dicCode = "id")
    @Excel(name = "供应商", width = 15, dictTable = "app_supplier", dicText = "name", dicCode = "id")
    private String supplierId;

    @ApiModelProperty("备注")
    @Excel(name = "备注", width = 100)
    private String note;

    @ApiModelProperty("状态")
    private Integer status;

    @TableLogic
    private Integer isDel;

    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty("建立时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @TableField(fill = FieldFill.UPDATE)
    @ApiModelProperty("修改时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;


}
