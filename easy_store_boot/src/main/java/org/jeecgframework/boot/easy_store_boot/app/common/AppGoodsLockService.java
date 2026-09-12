package org.jeecgframework.boot.easy_store_boot.app.common;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.TreeSet;

/**
 * Serializes stock recalculation for the affected goods inside a transaction.
 *
 * <p>Stock-ledger rebuilding updates sale lines belonging to multiple orders.
 * Acquiring the goods rows before an order row is written gives document saves
 * and ledger rebuilds a consistent lock order.</p>
 */
@Component
public class AppGoodsLockService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseDialect databaseDialect;

    public void lockForUpdate(Collection<String> goodsIds) {
        if (!databaseDialect.isMySql() || goodsIds == null || goodsIds.isEmpty()) {
            return;
        }

        TreeSet<String> normalized = new TreeSet<>((left, right) ->
                Integer.compare(Integer.parseInt(left), Integer.parseInt(right)));
        for (String goodsId : goodsIds) {
            if (StringUtils.isNotBlank(goodsId) && StringUtils.isNumeric(goodsId.trim())) {
                normalized.add(goodsId.trim());
            }
        }
        if (normalized.isEmpty()) {
            return;
        }

        for (String goodsId : normalized) {
            jdbcTemplate.queryForList(
                    "SELECT id FROM app_goods WHERE id = ? FOR UPDATE",
                    goodsId);
        }
    }
}
