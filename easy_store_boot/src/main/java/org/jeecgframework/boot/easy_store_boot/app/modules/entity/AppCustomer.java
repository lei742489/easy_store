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
 * @TableName app_customer
 */
@TableName(value ="app_customer")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_customer对象", description="客户管理")
public class AppCustomer implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("客户名称")
    @Required
    @Excel(name = "客户名称", width = 15)
    @Letters(code="pyCode")
    private String name;

    private String pyCode;

    @ApiModelProperty("联系人")
    @Excel(name = "联系人", width = 15)
    private String contactName;

    @ApiModelProperty("手机")
    @Excel(name = "手机", width = 15)
    private String mobile;

    @ApiModelProperty("电话")
    @Excel(name = "电话", width = 15)
    private String phone;

    @ApiModelProperty("邮件")
    @Excel(name = "邮件", width = 15)
    private String mail;

    @ApiModelProperty("邮编")
    @Excel(name = "邮编", width = 15)
    private String postal;

    @ApiModelProperty("生日")
    @Excel(name = "生日", width = 15)
    private String birthday;

    @ApiModelProperty("详细地址")
    @Excel(name = "详细地址", width = 15)
    private String address;

    @ApiModelProperty("QQ号")
    @Excel(name = "QQ号", width = 15)
    private String qq;

    @ApiModelProperty("备注")
    @Excel(name = "备注", width = 15)
    private String note;

    @ApiModelProperty("分类")
    @DictField(dictTable = "app_customer_category", dicText = "title", dicCode = "id")
    @Excel(name = "分类", width = 15, dictTable = "app_customer_category", dicText = "title", dicCode = "id")
    private String categoryId;

    @DictField(dictTable = "app_customer_level", dicText = "title", dicCode = "id")
    @Excel(name = "等级分类", width = 15, dictTable = "app_customer_level", dicText = "title", dicCode = "id")
    @ApiModelProperty("等级分类")
    private String levelId;

    @ApiModelProperty("折扣")
    @Excel(name = "折扣", width = 15,numFormat = "0.00")
    private Double discount;

    @ApiModelProperty("初期欠款")
    @Excel(name = "初期欠款", width = 15,numFormat = "0.00",needMerge = true)
    private Double defPayable;

    @ApiModelProperty("当前欠款")
    @Excel(name = "当前欠款", width = 20,numFormat = "0.00",needMerge = true)
    private Double payable;

    @ApiModelProperty("状态")
    private Integer status;

    @TableLogic
    private Integer isDel;


    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty("建立时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;




}