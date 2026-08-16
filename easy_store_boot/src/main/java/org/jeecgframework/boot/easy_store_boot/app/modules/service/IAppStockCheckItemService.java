package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppStockCheckItem;

import java.util.List;

public interface IAppStockCheckItemService extends IService<AppStockCheckItem> {
    List<AppStockCheckItem> listByCheckId(Integer checkId);

    void removeByCheckId(Integer checkId);

    void removeByUpdate(List<AppStockCheckItem> updateList, Integer checkId);

    Integer sumProfitLossQuantityByGoodsId(String goodsId);
}
