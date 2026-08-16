package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "app_role_permission")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class AppRolePermission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer roleId;

    private String permissionCode;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
