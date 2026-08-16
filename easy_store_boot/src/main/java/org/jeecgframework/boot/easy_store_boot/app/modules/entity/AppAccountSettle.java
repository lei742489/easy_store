package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.boot.easy_store_boot.app.common.dict.DictField;
import org.jeecgframework.poi.excel.annotation.Excel;

/**
 * 
 * @TableName app_account_settle
 */
@TableName(value ="app_account_settle")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_account_settle对象", description="结算帐户")
public class AppAccountSettle implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("帐户名称")
    @Excel(name = "帐户名称", width = 20)
    private String name;

    @ApiModelProperty("帐户分类")
    @DictField(dictTable = "app_account_settle_type", dicText = "title", dicCode = "id")
    @Excel(name = "帐户分类", width = 15, dictTable = "app_account_settle_type", dicText = "title", dicCode = "id")
    private String typeId;

    @ApiModelProperty("银行名称")
    @Excel(name = "银行名称", width = 20)
    private String bankName;

    @ApiModelProperty("银行卡号")
    @Excel(name = "银行卡号", width = 20)
    private String bankCard;

    @ApiModelProperty("初始余额")
    @Excel(name = "初始余额", width = 20, numFormat = "0.00")
    private Double initPrc;

    @ApiModelProperty("当前余额")
    @Excel(name = "当前余额", width = 20, numFormat = "0.00")
    private Double curPrc;

    @ApiModelProperty("备注")
    @Excel(name = "备注", width = 20)
    private String note;

    @TableLogic
    private Integer isDel;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;


}