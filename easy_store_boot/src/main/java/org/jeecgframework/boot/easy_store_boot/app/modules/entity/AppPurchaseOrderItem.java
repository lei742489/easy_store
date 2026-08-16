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
import org.jeecgframework.boot.easy_store_boot.app.common.dict.DictField;
import org.jeecgframework.poi.excel.annotation.Excel;

/**
 * 
 * @TableName app_purchase_order_item
 */
@TableName(value ="app_purchase_order_item")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_purchase_order_item对象", description="进货单-货品对象")
public class AppPurchaseOrderItem implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("货品名称")
    @DictField(dictTable = "app_goods", dicText = "title", dicCode = "id")
    @Excel(name = "货品名称", width = 50, dictTable = "app_goods", dicText = "title", dicCode = "id")
    private String goodsId;

    @TableField(exist = false)
    private String goodsName;

    @ApiModelProperty("货品分类")
    @DictField(dictTable = "app_goods_category", dicText = "title", dicCode = "id")
    @Excel(name = "货品分类", width = 15, dictTable = "app_goods_category", dicText = "title", dicCode = "id")
    private String categoryId;

    @ApiModelProperty("单位")
    @Excel(name = "单位", width = 15)
    private String unit;

    @ApiModelProperty("数量")
    @Excel(name = "数量", width = 15)
    private Integer quantity;

    @ApiModelProperty("单价")
    @Excel(name = "单价", width = 15,numFormat = "0.00")
    private Double unitPrice;

    @ApiModelProperty("总价")
    @Excel(name = "总价", width = 15,numFormat = "0.00")
    private Double totalAmount;

    @ApiModelProperty("单号")
    private Integer orderId;

    @ApiModelProperty("货品备注")
    @Excel(name = "货品备注", width = 50)
    private String note;

    private Integer isInit;

    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty("建立时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @TableField(fill = FieldFill.UPDATE)
    @ApiModelProperty("修改时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    @TableLogic
    private Integer isDel;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;


}
