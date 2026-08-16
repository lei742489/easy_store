package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.common.DoubleUtil;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomer;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSupplier;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppCustomerMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppReceivePaymentVoucherService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSaleOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
* @author Administrator
* @description 针对表【app_customer】的数据库操作Service实现
* @createDate 2025-06-23 14:50:29
*/
@Service
public class AppCustomerServiceImpl extends ServiceImpl<AppCustomerMapper, AppCustomer>
    implements IAppCustomerService {
    @Autowired
    private IAppSaleOrderService appSaleOrderService;
    @Autowired
    @Lazy
    private IAppReceivePaymentVoucherService receivePaymentVoucherService;

    @Override
    @Async("myExecutor")
    public void setDefaultCategory(Integer categoryId) {
        List<AppCustomer> list = super.list(new LambdaQueryWrapper<AppCustomer>().eq(AppCustomer::getCategoryId, categoryId).select(AppCustomer::getId));
        if(!list.isEmpty()){
            Set<Integer> ids = new HashSet<>();
            for(AppCustomer appCustomer : list){
                ids.add(appCustomer.getId());
            }
            LambdaUpdateWrapper<AppCustomer> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.set(AppCustomer::getCategoryId,0);
            updateWrapper.in(AppCustomer::getId,ids);
            this.update(updateWrapper);
        }
    }

    @Override
    @Async("myExecutor")
    public void setDefLevel(Integer levelId) {
        List<AppCustomer> list = super.list(new LambdaQueryWrapper<AppCustomer>().eq(AppCustomer::getLevelId, levelId).select(AppCustomer::getId));
        if(!list.isEmpty()){
            Set<Integer> ids = new HashSet<>();
            for(AppCustomer appCustomer : list){
                ids.add(appCustomer.getId());
            }
            LambdaUpdateWrapper<AppCustomer> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.set(AppCustomer::getLevelId,null);
            updateWrapper.in(AppCustomer::getId,ids);
            this.update(updateWrapper);
        }
    }

    @Override
    public Double updatePayable(String customerId) {
        AppCustomer appCustomer = this.getById(customerId);
        if (appCustomer == null) return 0.00;
        Double d = appSaleOrderService.sumPayableAmountByCustomer(customerId);
        Double p = receivePaymentVoucherService.sumPaidAmountByCustomerId(customerId);
        Double defPayable = appCustomer.getDefPayable() == null ? 0.0 : appCustomer.getDefPayable();
        Double f1 = DoubleUtil.add(d,defPayable);
        Double f2 = DoubleUtil.sub(f1,p);
        appCustomer.setPayable(f2);
        updateById(appCustomer);
        return f2;
    }
}




