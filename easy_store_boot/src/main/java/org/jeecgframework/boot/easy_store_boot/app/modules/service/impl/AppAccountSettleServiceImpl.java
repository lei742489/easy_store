package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.common.DoubleUtil;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppAccountSettle;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppAccountSettleService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppAccountSettleMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【app_account_settle】的数据库操作Service实现
* @createDate 2025-06-29 13:53:25
*/
@Service
public class AppAccountSettleServiceImpl extends ServiceImpl<AppAccountSettleMapper, AppAccountSettle>
    implements IAppAccountSettleService {

    @Override
    public boolean save(AppAccountSettle entity){
      if(entity.getCurPrc()!=null)
          entity.setInitPrc(entity.getCurPrc());
      return super.save(entity);
    };
    @Override
    public Double updateCurPrc(String settleId, Double price) {

        if (settleId == null || price == null)
            return 0.0;
        AppAccountSettle settle = getById(settleId);
        if(settle == null) return 0.0;

        Double d = settle.getCurPrc();
        Double last = DoubleUtil.add(d,price);
        settle.setCurPrc(last);
        updateById(settle);

        return last;
    }

    @Override
    public String getNameById(String id) {
        AppAccountSettle settle = getById(id);
        if (settle == null)
            return "";
        return settle.getName();
    }
}




