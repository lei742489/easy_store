package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppAccountSettleType;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppAccountSettleTypeService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppAccountSettleTypeMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
* @author Administrator
* @description 针对表【app_account_settle_type】的数据库操作Service实现
* @createDate 2025-06-29 13:50:17
*/
@Service
public class AppAccountSettleTypeServiceImpl extends ServiceImpl<AppAccountSettleTypeMapper, AppAccountSettleType>
    implements IAppAccountSettleTypeService {

    @Override
    public List<AppAccountSettleType> getList() {
        return super.list(new LambdaQueryWrapper<AppAccountSettleType>().orderByAsc(AppAccountSettleType::getId));
    }
}




