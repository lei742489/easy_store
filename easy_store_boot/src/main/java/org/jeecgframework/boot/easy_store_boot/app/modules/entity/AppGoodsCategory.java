package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.boot.easy_store_boot.app.common.anno.Letters;

/**
 * 
 * @TableName app_goods_category
 */
@TableName(value ="app_goods_category")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class AppGoodsCategory implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Letters(code="pyCode")
    private String title;

    private String pyCode;

    private Integer parentId;

    private Date createTime;

    private Integer root;

    @TableLogic
    private Integer isDel;


    @TableField(exist = false)
    private List<AppGoodsCategory> children;
}