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
import org.jeecgframework.poi.excel.annotation.Excel;

/**
 * 用户信息
 * @TableName app_user
 */
@TableName(value ="app_user")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_user", description="用户管理")
public class AppUser implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("名称")
    @Excel(name = "名称", width = 20)
    private String userName;

    /**
     *密码
     */
    private String password;

    /**
     * 头像
     */
    private String avatar;

    /**
     *加密盐
     */
    private String salt;

    /**
     *真实姓名
     */
    private String realName;

    /**
     *邮件
     */
    private String email;

    /**
     *备注
     */
    @ApiModelProperty("备注")
    @Excel(name = "备注", width = 60)
    private String remarks;

    /**
     * 状态：0禁用，1正常
     */
    private Integer status;

    /**
     * 主帐号：1是，0否
     */
    private Integer isRoot;

    /**
     * 角色
     */
    private Integer roleId;

    /**
     *手机号
     */
    @ApiModelProperty("手机号")
    @Excel(name = "手机号", width = 40)
    private String mobile;

    @ApiModelProperty("提成比例（百分比）")
    @Excel(name = "提成比例(%)", width = 15, numFormat = "0.00")
    private Double commissionRate;

    /**
     *上次登录时间
     */
    private Date lastLoginTime;

    /**
     *上次登录IP
     */
    private String lastLoginIp;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 地址
     */
    private String address;

    /**
     *账户过期时间
     */
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    /**
     *建立日期
     */
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @TableField(exist = false)
    private String token;

    @TableField(exist = false)
    private String roleId_dictText;

    @TableField(exist = false)
    private List<String> permissionCodes;

    @TableLogic
    private Integer isDel;


}
