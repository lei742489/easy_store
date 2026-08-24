package org.jeecgframework.boot.easy_store_boot.app.modules.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.boot.easy_store_boot.app.common.anno.Required;
import org.jeecgframework.boot.easy_store_boot.app.common.dict.DictField;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@TableName("app_stock_check")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value = "app_stock_check对象", description = "库存盘点单")
public class AppStockCheck implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Required
    @ApiModelProperty("单据编号")
    @Excel(name = "单据编号", width = 20, needMerge = true)
    private String orderNo;

    @TableField(exist = false)
    private List<AppStockCheckItem> items;

    @ApiModelProperty("营业员")
    @DictField(dictTable = "app_user", dicText = "real_name", dicCode = "id")
    @Excel(name = "营业员", width = 15, dictTable = "app_user", dicText = "real_name", dicCode = "id", needMerge = true)
    private String cashierId;

    @ApiModelProperty("营业员姓名")
    @Excel(name = "营业员姓名", width = 15, needMerge = true)
    private String cashierName;

    @ApiModelProperty("盈亏数量")
    @Excel(name = "盈亏数量", width = 15, numFormat = "0.00", needMerge = true)
    private Double profitLossQuantity;

    @ApiModelProperty("盈亏金额")
    @Excel(name = "盈亏金额", width = 15, numFormat = "0.00", needMerge = true)
    private Double profitLossAmount;

    @ApiModelProperty("说明")
    @Excel(name = "说明", width = 40, needMerge = true)
    private String note;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "单据日期", width = 15, format = "yyyy-MM-dd HH:mm:ss", needMerge = true)
    private Date createTime;

    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    @TableLogic
    private Integer isDel;
}
