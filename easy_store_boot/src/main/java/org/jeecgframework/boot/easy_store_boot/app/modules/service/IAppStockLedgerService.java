package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppStockLedger;

import java.util.Collection;

public interface IAppStockLedgerService extends IService<AppStockLedger> {
    void rebuildGoodsLedger(String goodsId);

    void rebuildGoodsLedgers(Collection<String> goodsIds);
}
