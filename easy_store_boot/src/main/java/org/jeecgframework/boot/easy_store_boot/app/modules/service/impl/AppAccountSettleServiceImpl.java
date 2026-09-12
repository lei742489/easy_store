package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppAccountSettle;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppAccountSettleService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppAccountSettleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【app_account_settle】的数据库操作Service实现
* @createDate 2025-06-29 13:53:25
*/
@Service
public class AppAccountSettleServiceImpl extends ServiceImpl<AppAccountSettleMapper, AppAccountSettle>
    implements IAppAccountSettleService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        int updated = jdbcTemplate.update(
                "UPDATE app_account_settle " +
                        "SET cur_prc = COALESCE(cur_prc, 0) + ? " +
                        "WHERE id = ? AND COALESCE(is_del, 0) = 0",
                price, settleId);
        if(updated == 0) return 0.0;
        Number current = jdbcTemplate.queryForObject(
                "SELECT COALESCE(cur_prc, 0) FROM app_account_settle " +
                        "WHERE id = ? AND COALESCE(is_del, 0) = 0",
                Number.class, settleId);
        return current == null ? 0.0 : current.doubleValue();
    }

    @Override
    public String getNameById(String id) {
        AppAccountSettle settle = getById(id);
        if (settle == null)
            return "";
        return settle.getName();
    }
}




