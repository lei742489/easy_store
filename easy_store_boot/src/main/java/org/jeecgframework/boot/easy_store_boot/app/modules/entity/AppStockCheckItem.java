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
import org.jeecgframework.boot.easy_store_boot.app.common.dict.DictField;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.io.Serializable;
import java.util.Date;

@TableName("app_stock_check_item")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value = "app_stock_check_item对象", description = "库存盘点明细")
public class AppStockCheckItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty("货品")
    @DictField(dictTable = "app_goods", dicText = "title", dicCode = "id")
    @Excel(name = "品名规格", width = 30, dictTable = "app_goods", dicText = "title", dicCode = "id")
    private String goodsId;

    @TableField(exist = false)
    private String goodsName;

    @ApiModelProperty("单位")
    @Excel(name = "单位", width = 10)
    private String unit;

    @ApiModelProperty("账存数量")
    @Excel(name = "账存数量", width = 12, numFormat = "0.00")
    private Double bookQuantity;

    @ApiModelProperty("实际数量")
    @Excel(name = "实际数量", width = 12, numFormat = "0.00")
    private Double actualQuantity;

    @ApiModelProperty("盈亏数量")
    @Excel(name = "盈亏数量", width = 12, numFormat = "0.00")
    private Double profitLossQuantity;

    @ApiModelProperty("成本单价")
    @Excel(name = "单价", width = 12, numFormat = "0.00")
    private Double unitPrice;

    @ApiModelProperty("盈亏金额")
    @Excel(name = "盈亏金额", width = 12, numFormat = "0.00")
    private Double profitLossAmount;

    @ApiModelProperty("盘点单")
    private Integer checkId;

    @ApiModelProperty("备注")
    @Excel(name = "备注", width = 30)
    private String note;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    @TableLogic
    private Integer isDel;
}
