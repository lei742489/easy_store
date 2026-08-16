package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPaymentSettleItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPaymentSettleItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppPaymentSettleItemMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
* @author Administrator
* @description 针对表【app_payment_settle_item】的数据库操作Service实现
* @createDate 2025-07-07 10:05:42
*/
@Service
public class AppPaymentSettleItemServiceImpl extends ServiceImpl<AppPaymentSettleItemMapper, AppPaymentSettleItem>
    implements IAppPaymentSettleItemService {

    @Override
    public List<AppPaymentSettleItem> listByPaymentId(String paymentId) {
        return super.list(new LambdaQueryWrapper<AppPaymentSettleItem>().eq(AppPaymentSettleItem::getPaymentId, paymentId).orderByAsc(AppPaymentSettleItem::getCreateTime));
    }


}




