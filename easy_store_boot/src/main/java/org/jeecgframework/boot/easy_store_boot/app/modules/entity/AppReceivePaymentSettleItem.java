package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.boot.easy_store_boot.app.common.dict.DictField;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.io.Serializable;
import java.util.Date;

/**
 * 
 * @TableName app_receive_payment_settle_item
 */
@TableName(value ="app_receive_payment_settle_item")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_receive_payment_settle_item对象", description="收款单-结算帐户")
public class AppReceivePaymentSettleItem implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    private Integer paymentId;

    @ApiModelProperty("帐户名称")
    @DictField(dictTable = "app_account_settle", dicText = "name", dicCode = "id")
    @Excel(name = "帐户名称", width = 15, dictTable = "app_account_settle", dicText = "name", dicCode = "id")
    private String settleId;

    @ApiModelProperty("金额")
    @Excel(name = "金额", width = 20,numFormat = "0.00")
    private Double amount;

    @ApiModelProperty("备注")
    @Excel(name = "备注", width = 50)
    private String note;

    private Date createTime;


}