package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "app_stock_ledger")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value = "app_stock_ledger对象", description = "库存成本流水")
public class AppStockLedger implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("商品ID")
    private String goodsId;

    @ApiModelProperty("商品内流水顺序")
    private Integer seqNo;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date businessTime;

    @ApiModelProperty("业务类型")
    private String businessType;

    @ApiModelProperty("业务单号")
    private String businessNo;

    @ApiModelProperty("来源表")
    private String sourceTable;

    @ApiModelProperty("来源主表ID")
    private Integer sourceId;

    @ApiModelProperty("来源明细ID")
    private Integer sourceItemId;

    private Double inQty;
    private Double inPrice;
    private Double inAmount;
    private Double outQty;
    private Double outPrice;
    private Double outAmount;
    private Double costPrice;
    private Double costAmount;
    private Double beforeQty;
    private Double beforeAmount;
    private Double afterQty;
    private Double afterAmount;
    private Double afterCostPrice;

    @ApiModelProperty("是否已重算")
    private Integer isCalc;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    @TableLogic
    private Integer isDel;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
