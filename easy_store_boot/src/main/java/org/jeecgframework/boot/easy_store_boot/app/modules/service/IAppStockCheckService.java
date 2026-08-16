package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppStockCheck;

import java.io.Serializable;

public interface IAppStockCheckService extends IService<AppStockCheck> {
    boolean save(AppStockCheck entity);

    boolean updateById(AppStockCheck entity);

    boolean removeById(Serializable id);
}
