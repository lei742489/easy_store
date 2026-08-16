package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.io.Serializable;
import java.util.Date;

/**
 * 
 * @TableName app_receive_payment_amount_item
 */
@TableName(value ="app_receive_payment_amount_item")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_receive_payment_amount_item对象", description="收款单-结算记录")
public class AppReceivePaymentAmountItem implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("销售单号")
    @Excel(name = "销售单号", width = 20)
    private String orderNo;

    @TableField(exist = false)
    @Excel(name = "应收金额", width = 20,numFormat = "0.00")
    private Double payableAmount;

    @TableField(exist = false)
    @Excel(name = "已收金额", width = 20,numFormat = "0.00")
    private Double paidAmount;

    @TableField(exist = false)
    @Excel(name = "未收金额", width = 20,numFormat = "0.00")
    private Double unpaidAmount;

    @ApiModelProperty("本次收款金额")
    @Excel(name = "本次收款金额", width = 20,numFormat = "0.00")
    private Double amount;

    @ApiModelProperty("备注")
    @Excel(name = "备注", width = 50)
    private String note;

    private Date createTime;

    private Integer orderId;


    @TableField(exist = false)
    private static final long serialVersionUID = 1L;


}