package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

@TableName(value ="app_home_menu")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="app_home_menu", description="首页菜单")
public class AppHomeMenu implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键")
    private Integer id;

    private String code;

    private String groupCode;

    private String groupTitle;

    private String name;

    private String icon;

    private String url;

    private String action;

    private Integer sortNo;

    private Integer status;

    private Integer rootOnly;

    private String createTime;

    @TableLogic
    private Integer isDel;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
