package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUnit;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUnitService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppUnitMapper;
import org.springframework.stereotype.Service;


/**
* @author Administrator
* @description 针对表【app_unit】的数据库操作Service实现
* @createDate 2025-06-27 15:39:22
*/
@Service
public class AppUnitServiceImpl extends ServiceImpl<AppUnitMapper, AppUnit>
    implements IAppUnitService {

    @Override
    public void updateByName(String name) {
        if(StringUtils.isEmpty(name) || count(new LambdaQueryWrapper<AppUnit>().eq(AppUnit::getName,name))>0) return;
        AppUnit appUnit = new AppUnit();
        appUnit.setName(name);
        save(appUnit);
    }

    @Override
    public String normalizeName(String value) {
        if(StringUtils.isEmpty(value)) return value;
        if(!value.matches("^\\d+$")) return value;
        AppUnit appUnit = getById(value);
        return appUnit == null ? value : appUnit.getName();
    }


}




