package org.jeecgframework.boot.easy_store_boot.app.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomerQuote;

import java.util.List;

public interface IAppCustomerQuoteService extends IService<AppCustomerQuote> {

    void saveQuote(AppCustomerQuote entity);

    void updateQuote(AppCustomerQuote entity);

    void saveQuotes(List<AppCustomerQuote> list);

    void batchUpdatePrice(String ids, Double quotePrice);

    void batchRemove(String ids);
}
