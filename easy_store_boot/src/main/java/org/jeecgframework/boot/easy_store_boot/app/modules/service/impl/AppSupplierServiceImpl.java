package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;


import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.DoubleUtil;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSupplier;

import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPaymentVoucherService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPurchaseOrderService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSupplierService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppSupplierMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
* @author Administrator
* @description 针对表【app_supplier】的数据库操作Service实现
* @createDate 2025-06-26 14:29:14
*/
@Service
public class AppSupplierServiceImpl extends ServiceImpl<AppSupplierMapper, AppSupplier>
    implements IAppSupplierService {

    @Autowired
    @Lazy
    private IAppPurchaseOrderService purchaseOrderService;
    @Autowired
    @Lazy
    private IAppPaymentVoucherService paymentVoucherService;

    @Override
    public boolean save(AppSupplier entity){
        entity.setDefPayable(entity.getPayable());
        return super.save(entity);
    }

    @Override
    public Double updatePayable(String supplerId) {
        AppSupplier appSupplier = this.getById(supplerId);
        if (appSupplier == null) return 0.00;
        Double d = purchaseOrderService.sumPayableAmountBySupplier(supplerId);
        Double p = paymentVoucherService.sumPaidAmountBySupplier(supplerId);

        Double defPayable = appSupplier.getDefPayable() == null ? 0.0 : appSupplier.getDefPayable();
        Double f1 = DoubleUtil.add(d,defPayable);
        Double f2 = DoubleUtil.sub(f1,p);
        appSupplier.setPayable(f2);
        updateById(appSupplier);
        return f2;
    }

    @Override
    public List<AppSupplier> searchByName(String name) {
        if(StringUtils.isEmpty(name))
            return Collections.emptyList();

        return super.list(new LambdaQueryWrapper<AppSupplier>().like(AppSupplier::getName, name).eq(AppSupplier::getStatus,1));
    }


}




