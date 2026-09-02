package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppStockCheckItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppStockCheckItemMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppStockCheckItemService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AppStockCheckItemServiceImpl extends ServiceImpl<AppStockCheckItemMapper, AppStockCheckItem>
        implements IAppStockCheckItemService {

    @Override
    public List<AppStockCheckItem> listByCheckId(Integer checkId) {
        return list(new LambdaQueryWrapper<AppStockCheckItem>()
                .eq(AppStockCheckItem::getCheckId, checkId)
                .orderByAsc(AppStockCheckItem::getId));
    }

    @Override
    public void removeByCheckId(Integer checkId) {
        remove(new LambdaQueryWrapper<AppStockCheckItem>()
                .eq(AppStockCheckItem::getCheckId, checkId));
    }

    @Override
    public void removeByUpdate(List<AppStockCheckItem> updateList, Integer checkId) {
        Set<Integer> ids = new HashSet<>();
        for (AppStockCheckItem item : updateList) {
            if (item.getId() != null) {
                ids.add(item.getId());
            }
        }
        LambdaQueryWrapper<AppStockCheckItem> wrapper = new LambdaQueryWrapper<AppStockCheckItem>()
                .eq(AppStockCheckItem::getCheckId, checkId);
        if (!ids.isEmpty()) {
            wrapper.notIn(AppStockCheckItem::getId, ids);
        }
        remove(wrapper);
    }

    @Override
    public Double sumProfitLossQuantityByGoodsId(String goodsId) {
        QueryWrapper<AppStockCheckItem> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(profit_loss_quantity), 0) AS total")
                .eq("goods_id", goodsId)
                .inSql("check_id", "SELECT id FROM app_stock_check WHERE is_del = 0");
        Map<String, Object> result = getMap(wrapper);
        if (result == null || result.get("total") == null) {
            return 0D;
        }
        return new BigDecimal(result.get("total").toString()).doubleValue();
    }
}
