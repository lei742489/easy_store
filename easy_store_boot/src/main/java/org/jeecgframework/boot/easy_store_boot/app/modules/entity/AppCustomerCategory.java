package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 
 * @TableName app_customer_category
 */
@TableName(value ="app_customer_category")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class AppCustomerCategory implements Serializable {
    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Integer id;


    private String title;


    private Integer parentId;

    private Date createTime;

    private Integer root;

    @TableLogic
    private Integer isDel;


    @TableField(exist = false)
    private List<AppCustomerCategory> children;
}