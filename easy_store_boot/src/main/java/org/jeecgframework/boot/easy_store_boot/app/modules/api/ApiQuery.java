package org.jeecgframework.boot.easy_store_boot.app.modules.api;

import lombok.Data;

@Data
public class ApiQuery {

    private String id;

    private String key;

    private String parentId;

    private String goodsId;

    private Integer pageNo;

    private Integer pageSize;

    private String column;

    private String pyCode;

    private String order;

    private Boolean zeroStock;

    public Integer getPageNo() {
        return pageNo == null ? 1 : pageNo;
    }

    public Integer getPageSize() {
        return pageSize == null ? 15 : pageSize;
    }
}
