package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.*;
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

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 
 * @TableName app_receive_payment_voucher
 */
@TableName(value ="app_receive_payment_voucher")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_receive_payment_voucher对象", description="收款单")
public class AppReceivePaymentVoucher implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("订单号")
    @Excel(name = "订单号", width = 30,needMerge = true)
    @Required
    private String orderNo;

    @ApiModelProperty("客户名称")
    @DictField(dictTable = "app_customer", dicText = "name", dicCode = "id")
    @Excel(name = "客户名称", width = 20, dictTable = "app_customer", dicText = "name", dicCode = "id",needMerge = true)
    private String customerId;

    @ApiModelProperty("营业员")
    @DictField(dictTable = "app_user", dicText = "real_name", dicCode = "id")
    @Excel(name = "营业员", width = 20, dictTable = "app_user", dicText = "real_name", dicCode = "id",needMerge = true)
    private String cashierId;

    @ApiModelProperty("营业员姓名")
    @Excel(name = "营业员姓名", width = 20, needMerge = true)
    private String cashierName;

    @ApiModelProperty("Status: 0 pending, 1 active")
    private Integer status;

    @ApiModelProperty("收款金额")
    @Excel(name = "收款金额", width = 20,numFormat = "0.00",needMerge = true)
    private Double amount;

    @ApiModelProperty("备注")
    @Excel(name = "备注", width = 30,needMerge = true)
    private String note;

    @TableField(exist = false)
    @ExcelCollection(name="帐户详情")
    private List<AppReceivePaymentSettleItem> settleItems;

    @TableField(exist = false)
    @ExcelCollection(name="结算订单")
    private List<AppReceivePaymentAmountItem> amountItems;

    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty("建立时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd")
    @Excel(name = "建立时间", width = 30, format = "yyyy-MM-dd HH:mm:ss",needMerge = true)
    private Date createTime;

    @TableLogic
    private Integer isDel;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableField(exist = false)
    private String totalAmountChinese;


}
