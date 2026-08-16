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
import org.jeecgframework.poi.excel.annotation.Excel;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "app_customer_quote")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value = "app_customer_quote", description = "大客户报价")
public class AppCustomerQuote implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("客户")
    private String customerId;

    @Excel(name = "客户名称", width = 25)
    private String customerName;

    private String customerPyCode;

    @ApiModelProperty("商品")
    private String goodsId;

    @Excel(name = "商品名称", width = 35)
    private String goodsTitle;

    private String goodsPyCode;

    @Excel(name = "单位", width = 12)
    private String unit;

    @Excel(name = "零售价", width = 15, numFormat = "0.00")
    private Double salePrc;

    @Excel(name = "批发价", width = 15, numFormat = "0.00")
    private Double tradePrc;

    @Excel(name = "大客户价", width = 15, numFormat = "0.00")
    private Double quotePrice;

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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
