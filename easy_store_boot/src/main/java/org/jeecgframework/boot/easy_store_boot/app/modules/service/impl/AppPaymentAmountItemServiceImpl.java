package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.DoubleUtil;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPaymentAmountItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrder;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPaymentAmountItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppPaymentAmountItemMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
* @author Administrator
* @description 针对表【app_payment_amount_item】的数据库操作Service实现
* @createDate 2025-07-07 14:42:23
*/
@Service
public class AppPaymentAmountItemServiceImpl extends ServiceImpl<AppPaymentAmountItemMapper, AppPaymentAmountItem>
    implements IAppPaymentAmountItemService {

    @Override
    public void removeByOrderId(String orderId) {
        remove(new LambdaQueryWrapper<AppPaymentAmountItem>().eq(AppPaymentAmountItem::getOrderId, orderId));
    }

    @Override
    public List<AppPaymentAmountItem> listByOrderId(String orderId) {
        return super.list(new LambdaQueryWrapper<AppPaymentAmountItem>().eq(AppPaymentAmountItem::getOrderId, orderId).orderByAsc(AppPaymentAmountItem::getCreateTime));
    }

    @Override
    public Double sumAmountByOrderNo(String orderNo) {
        if(StringUtils.isEmpty(orderNo))
         return 0.0;

        QueryWrapper<AppPaymentAmountItem> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(amount), 0.00) as total")
                .eq("order_no", orderNo)
                .inSql("order_id", "select id from app_payment_voucher where is_del = 0 and status = 1");
        Map<String,Object> map= getMap(wrapper);
        return Double.parseDouble(map.get("total").toString());
    }


}




