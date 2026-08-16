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
import org.jeecgframework.poi.excel.annotation.Excel;

/**
 * 
 * @TableName app_supplier
 */
@TableName(value ="app_supplier")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_supplier对象", description="供应商管理")
public class AppSupplier implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("供应商名称")
    @Required
    @Excel(name = "供应商名称", width = 50)
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

    @ApiModelProperty("详细地址")
    @Excel(name = "详细地址", width = 15)
    private String address;

    @ApiModelProperty("QQ号")
    @Excel(name = "QQ号", width = 15)
    private String qq;

    @ApiModelProperty("备注")
    @Excel(name = "备注", width = 15)
    private String note;

    @ApiModelProperty("状态")
    private Integer status;

    @ApiModelProperty("初期应付款")
    @Excel(name = "初期应付款", width = 15)
    private Double defPayable;

    @ApiModelProperty("当前应付款")
    @Excel(name = "当前应付款", width = 15,numFormat = "0.00")
    private Double payable;

    @TableLogic
    private Integer isDel;


    @ApiModelProperty("建立时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}