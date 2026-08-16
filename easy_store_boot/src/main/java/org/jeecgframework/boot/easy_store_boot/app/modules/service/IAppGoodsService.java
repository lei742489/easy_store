package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.alibaba.fastjson.JSONObject;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.ApiQuery;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.GoodsSearchResult;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author Administrator
* @description 针对表【app_goods】的数据库操作Service
* @createDate 2025-06-27 10:35:51
*/
public interface IAppGoodsService extends IService<AppGoods> {

    boolean save(AppGoods entity);

    boolean updateById(AppGoods entity);

    List<GoodsSearchResult> searchByKey(Integer pageNo,String key);

    IPage<AppGoods> search(ApiQuery query);

    void setDefCategoryId(String categoryId);

    void updateStock(String goodsId);

    JSONObject getStockDetail(String goodsId, Long startTime, Long endTime);

    JSONObject getStockStatistics(String categoryId, String key, Long startTime, Long endTime,
                                  Integer current, Integer pageSize);

    String getTitleById(String goodsId);
}
