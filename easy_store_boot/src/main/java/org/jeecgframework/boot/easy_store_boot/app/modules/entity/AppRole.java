package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@TableName(value ="app_role")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_role", description="角色管理")
public class AppRole implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    @ApiModelProperty("角色名称")
    private String name;

    @ApiModelProperty("备注")
    private String remarks;

    @ApiModelProperty("状态")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @TableLogic
    private Integer isDel;

    @TableField(exist = false)
    private List<Integer> menuIds;

    @TableField(exist = false)
    private List<String> permissionCodes;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
