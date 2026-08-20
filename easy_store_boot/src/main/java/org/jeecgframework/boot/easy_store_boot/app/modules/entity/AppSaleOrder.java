package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.boot.easy_store_boot.app.common.anno.Required;
import org.jeecgframework.boot.easy_store_boot.app.common.dict.DictField;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.jeecgframework.poi.excel.annotation.ExcelCollection;

/**
 * 
 * @TableName app_sale_order
 */
@TableName(value ="app_sale_order")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_sale_order对象", description="销售单")
public class AppSaleOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("单号")
    @Excel(name = "单号", width = 20,needMerge = true)
    @Required
    private String orderNo;

    @ApiModelProperty("客户名称")
    @DictField(dictTable = "app_customer", dicText = "name", dicCode = "id")
    @Excel(name = "客户名称", width = 20, dictTable = "app_customer", dicText = "name", dicCode = "id",needMerge = true)
    private String customerId;

    @TableField(exist = false)
    @ExcelCollection(name="货品详情")
    private List<AppSaleOrderItem> items;

    @ApiModelProperty("订单类型：1出货，2退货")
    private Integer orderType;

    @ApiModelProperty("结算账户" )
    @DictField(dictTable = "app_account_settle", dicText = "name", dicCode = "id")
    @Excel(name = "结算账户", width = 20, dictTable = "app_account_settle", dicText = "name", dicCode = "id" ,needMerge = true)
    private String settleId;

    @ApiModelProperty("营业员")
    @DictField(dictTable = "app_user", dicText = "real_name", dicCode = "id")
    @Excel(name = "营业员", width = 20, dictTable = "app_user", dicText = "real_name", dicCode = "id",needMerge = true)
    private String cashierId;

    @ApiModelProperty("营业员姓名")
    @Excel(name = "营业员姓名", width = 20, needMerge = true)
    private String cashierName;

    @ApiModelProperty("Status: 0 pending, 1 active")
    private Integer status;

    @ApiModelProperty("总金额")
    @Excel(name = "总金额", width = 20,numFormat = "0.00",needMerge = true)
    private Double totalAmount;

    @ApiModelProperty("应收金额")
    @Excel(name = "应付金额", width = 20,numFormat = "0.00",needMerge = true)
    private Double payableAmount;

    @ApiModelProperty("实收金额")
    @Excel(name = "实付金额", width = 20,numFormat = "0.00",needMerge = true)
    private Double paidAmount;

    @ApiModelProperty("未收金额")
    @Excel(name = "未付金额", width = 20,numFormat = "0.00",needMerge = true)
    private Double unpaidAmount;

    @ApiModelProperty("总销售毛利")
    @Excel(name = "总销售毛利", width = 20,numFormat = "0.00",needMerge = true)
    private Double grossProfit;

    @ApiModelProperty("折后金额")
    @Excel(name = "折后金额", width = 20,numFormat = "0.00",needMerge = true)
    private Double discountedAmount;

    @ApiModelProperty("运费")
    @Excel(name = "运费", width = 20,numFormat = "0.00",needMerge = true)
    private Double freightAmount;

    @ApiModelProperty("折扣率")
    @Excel(name = "折扣率", width = 20,numFormat = "0.00",needMerge = true)
    private Double discountRate;

    @ApiModelProperty("备注")
    @Excel(name = "备注", width = 50,needMerge = true)
    private String note;

    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty("建立时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd")
    @Excel(name = "建立时间", width = 30, format = "yyyy-MM-dd HH:mm:ss",needMerge = true)
    private Date createTime;

    @TableField(fill = FieldFill.UPDATE)
    @ApiModelProperty("修改时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String updateBy;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableLogic
    private Integer isDel;

    @TableField(exist = false)
    private String totalAmountChinese;

    /**
     * 扩展返回参数，无实际逻辑处理
     */
    @TableField(exist = false)
    private Double amount;
}
