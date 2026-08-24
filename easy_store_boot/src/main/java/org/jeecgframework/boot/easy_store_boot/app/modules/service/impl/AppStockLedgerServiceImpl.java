package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppStockLedger;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppStockLedgerMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppStockLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AppStockLedgerServiceImpl extends ServiceImpl<AppStockLedgerMapper, AppStockLedger>
        implements IAppStockLedgerService {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String TYPE_INIT = "INIT";
    private static final String TYPE_PURCHASE_IN = "PURCHASE_IN";
    private static final String TYPE_PURCHASE_RETURN = "PURCHASE_RETURN";
    private static final String TYPE_SALE_OUT = "SALE_OUT";
    private static final String TYPE_SALE_RETURN = "SALE_RETURN";
    private static final String TYPE_STOCK_CHECK_IN = "STOCK_CHECK_IN";
    private static final String TYPE_STOCK_CHECK_OUT = "STOCK_CHECK_OUT";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildGoodsLedger(String goodsId) {
        if (StringUtils.isBlank(goodsId) || !StringUtils.isNumeric(goodsId.trim())) {
            return;
        }
        goodsId = goodsId.trim();
        Map<String, Object> goods = queryGoods(goodsId);
        if (goods == null) {
            return;
        }

        jdbcTemplate.update("DELETE FROM app_stock_ledger WHERE goods_id = ?", goodsId);
        List<Map<String, Object>> movements = queryMovements(goodsId);
        movements.sort(Comparator
                .comparingLong((Map<String, Object> row) -> timeValue(row.get("business_time")))
                .thenComparing(row -> stringValue(row.get("business_no")))
                .thenComparing(row -> integer(row.get("source_item_id")), Comparator.nullsLast(Integer::compareTo))
                .thenComparing(row -> integer(row.get("bill_type")), Comparator.nullsLast(Integer::compareTo)));

        StockState state = new StockState(resolveFallbackCostPrice(goods));
        Map<Integer, SaleCostUpdate> saleUpdates = new HashMap<>();
        Set<Integer> saleOrderIds = new HashSet<>();
        List<AppStockLedger> ledgers = new ArrayList<>();
        int seqNo = 1;

        for (Map<String, Object> movement : movements) {
            AppStockLedger ledger = applyMovement(goodsId, goods, movement, state, seqNo, saleUpdates, saleOrderIds);
            if (ledger != null) {
                ledgers.add(ledger);
                seqNo++;
            }
        }

        if (!ledgers.isEmpty()) {
            saveBatch(ledgers);
        }
        normalizeEndingState(state);
        updateGoodsSnapshot(goodsId, state);
        refreshGoodsPrices(goodsId);
        flushSaleCostUpdates(saleUpdates);
        refreshSaleOrderGrossProfits(saleOrderIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildGoodsLedgers(Collection<String> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return;
        }
        Set<String> normalized = new HashSet<>();
        for (String goodsId : goodsIds) {
            if (StringUtils.isNotBlank(goodsId) && StringUtils.isNumeric(goodsId.trim())) {
                normalized.add(goodsId.trim());
            }
        }
        for (String goodsId : normalized) {
            rebuildGoodsLedger(goodsId);
        }
    }

    private AppStockLedger applyMovement(String goodsId, Map<String, Object> goods, Map<String, Object> movement,
                                         StockState state, int seqNo,
                                         Map<Integer, SaleCostUpdate> saleUpdates,
                                         Set<Integer> saleOrderIds) {
        BigDecimal quantity = decimal(movement.get("quantity"));
        BigDecimal beforeQty = state.stockQty;
        BigDecimal beforeAmount = state.stockAmount;
        Integer billType = integer(movement.get("bill_type"));
        Integer orderType = integer(movement.get("order_type"));
        Integer isInit = integer(movement.get("is_init"));

        AppStockLedger ledger = baseLedger(goodsId, movement, seqNo, beforeQty, beforeAmount);
        if (isInit != null && isInit == 1) {
            ledger.setBusinessType(TYPE_INIT);
        } else if (billType != null && billType == 1) {
            ledger.setBusinessType(isReturnMovement(orderType, quantity) ? TYPE_PURCHASE_RETURN : TYPE_PURCHASE_IN);
        } else if (billType != null && billType == 2) {
            ledger.setBusinessType(isReturnMovement(orderType, quantity) ? TYPE_SALE_RETURN : TYPE_SALE_OUT);
        } else if (quantity.compareTo(BigDecimal.ZERO) >= 0) {
            ledger.setBusinessType(TYPE_STOCK_CHECK_IN);
        } else {
            ledger.setBusinessType(TYPE_STOCK_CHECK_OUT);
        }

        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            state.costPrice = estimateCostPrice(state, resolvePurchaseUnitPrice(goods, movement, BigDecimal.ONE));
            fillAfter(ledger, state);
            return ledger;
        }

        if (TYPE_PURCHASE_IN.equals(ledger.getBusinessType()) || TYPE_INIT.equals(ledger.getBusinessType())) {
            applyInbound(goods, movement, state, ledger, quantity.abs(),
                    resolvePurchaseUnitPrice(goods, movement, quantity));
        } else if (TYPE_PURCHASE_RETURN.equals(ledger.getBusinessType())) {
            BigDecimal price = resolvePurchaseUnitPrice(goods, movement, quantity);
            applyOutbound(state, ledger, quantity.abs(), price, price, false);
        } else if (TYPE_SALE_RETURN.equals(ledger.getBusinessType())) {
            BigDecimal returnQty = quantity.abs();
            BigDecimal costPrice = estimateCostPrice(state, decimal(movement.get("unit_price")));
            applyInboundAmount(state, ledger, returnQty, costPrice, returnQty.multiply(costPrice));
            updateSaleCost(movement, saleUpdates, saleOrderIds, returnQty.multiply(costPrice).negate());
        } else if (TYPE_SALE_OUT.equals(ledger.getBusinessType())) {
            BigDecimal saleQty = quantity.abs();
            BigDecimal salePrice = decimal(movement.get("unit_price"));
            BigDecimal costPrice = estimateCostPrice(state, salePrice);
            SaleCostUpdate update = registerSaleCost(movement, saleUpdates, saleOrderIds, BigDecimal.ZERO);
            BigDecimal costAmount = applySaleOutbound(state, ledger, saleQty, salePrice, costPrice, update);
            if (update != null) {
                update.costAmount = costAmount;
            }
        } else if (TYPE_STOCK_CHECK_IN.equals(ledger.getBusinessType())) {
            BigDecimal unitPrice = resolveCheckUnitPrice(state, movement);
            applyInbound(goods, movement, state, ledger, quantity.abs(), unitPrice);
        } else {
            BigDecimal unitPrice = resolveCheckUnitPrice(state, movement);
            applyOutbound(state, ledger, quantity.abs(), unitPrice, unitPrice, true);
        }
        fillAfter(ledger, state);
        return ledger;
    }

    private boolean isReturnMovement(Integer orderType, BigDecimal quantity) {
        return (orderType != null && orderType == 2)
                || (quantity != null && quantity.compareTo(BigDecimal.ZERO) < 0);
    }

    private AppStockLedger baseLedger(String goodsId, Map<String, Object> row, int seqNo,
                                      BigDecimal beforeQty, BigDecimal beforeAmount) {
        AppStockLedger ledger = new AppStockLedger();
        ledger.setGoodsId(goodsId);
        ledger.setSeqNo(seqNo);
        ledger.setBusinessTime(toDate(row.get("business_time")));
        ledger.setBusinessNo(stringValue(row.get("business_no")));
        ledger.setSourceTable(stringValue(row.get("source_table")));
        ledger.setSourceId(integer(row.get("source_id")));
        ledger.setSourceItemId(integer(row.get("source_item_id")));
        ledger.setBeforeQty(number(beforeQty, 4));
        ledger.setBeforeAmount(money(beforeAmount));
        ledger.setInQty(0.0);
        ledger.setInPrice(0.0);
        ledger.setInAmount(0.0);
        ledger.setOutQty(0.0);
        ledger.setOutPrice(0.0);
        ledger.setOutAmount(0.0);
        ledger.setCostPrice(0.0);
        ledger.setCostAmount(0.0);
        ledger.setIsCalc(1);
        ledger.setIsDel(0);
        return ledger;
    }

    private void applyInbound(Map<String, Object> goods, Map<String, Object> movement, StockState state,
                              AppStockLedger ledger, BigDecimal qty, BigDecimal unitPrice) {
        BigDecimal amount = decimal(movement.get("total_amount"));
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            amount = qty.multiply(unitPrice);
        } else {
            amount = amount.abs();
        }
        ledger.setInQty(number(qty, 4));
        ledger.setInPrice(number(unitPrice, 4));
        ledger.setInAmount(money(amount));

        BigDecimal remainingQty = qty;
        if (state.stockQty.compareTo(BigDecimal.ZERO) < 0 && !state.pendingSales.isEmpty()) {
            BigDecimal coverQty = remainingQty.min(state.stockQty.abs());
            coverNegativeSales(state, coverQty, unitPrice);
            ledger.setBeforeAmount(money(state.stockAmount));
            state.stockQty = state.stockQty.add(coverQty);
            state.stockAmount = state.stockAmount.add(coverQty.multiply(unitPrice));
            remainingQty = remainingQty.subtract(coverQty);
            if (state.stockQty.compareTo(BigDecimal.ZERO) == 0) {
                state.stockAmount = BigDecimal.ZERO;
                state.costPrice = state.lastCostPrice;
            }
        }

        if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainingAmount = amount;
            if (remainingQty.compareTo(qty) != 0 && qty.compareTo(BigDecimal.ZERO) != 0) {
                remainingAmount = amount.multiply(remainingQty).divide(qty, 4, RoundingMode.HALF_UP);
            }
            state.stockQty = state.stockQty.add(remainingQty);
            state.stockAmount = state.stockAmount.add(remainingAmount);
        }

        refreshCostPrice(state, unitPrice);
        if (state.stockQty.compareTo(BigDecimal.ZERO) == 0 && state.stockAmount.compareTo(BigDecimal.ZERO) == 0
                && state.costPrice.compareTo(BigDecimal.ZERO) == 0) {
            state.costPrice = resolveFallbackCostPrice(goods);
        }
    }

    private void applyInboundAmount(StockState state, AppStockLedger ledger, BigDecimal qty,
                                    BigDecimal unitPrice, BigDecimal amount) {
        ledger.setInQty(number(qty, 4));
        ledger.setInPrice(number(unitPrice, 4));
        ledger.setInAmount(money(amount));
        state.stockQty = state.stockQty.add(qty);
        state.stockAmount = state.stockAmount.add(amount);
        refreshCostPrice(state, unitPrice);
    }

    private BigDecimal applyOutbound(StockState state, AppStockLedger ledger, BigDecimal qty,
                                     BigDecimal outPrice, BigDecimal costPrice, boolean useCostForAmount) {
        BigDecimal costAmount = qty.multiply(costPrice);
        BigDecimal outAmount = useCostForAmount ? qty.multiply(outPrice) : costAmount;
        ledger.setOutQty(number(qty, 4));
        ledger.setOutPrice(number(outPrice, 4));
        ledger.setOutAmount(money(outAmount));
        ledger.setCostPrice(number(costPrice, 4));
        ledger.setCostAmount(money(costAmount));
        state.stockQty = state.stockQty.subtract(qty);
        state.stockAmount = state.stockAmount.subtract(costAmount);
        refreshCostPrice(state, costPrice);
        return costAmount;
    }

    private BigDecimal applySaleOutbound(StockState state, AppStockLedger ledger, BigDecimal qty,
                                         BigDecimal outPrice, BigDecimal costPrice, SaleCostUpdate update) {
        BigDecimal availableQty = state.stockQty.compareTo(BigDecimal.ZERO) > 0
                ? state.stockQty.min(qty)
                : BigDecimal.ZERO;
        BigDecimal temporaryQty = qty.subtract(availableQty);
        BigDecimal costAmount = qty.multiply(costPrice);
        ledger.setOutQty(number(qty, 4));
        ledger.setOutPrice(number(outPrice, 4));
        ledger.setOutAmount(money(qty.multiply(outPrice)));
        ledger.setCostPrice(number(costPrice, 4));
        ledger.setCostAmount(money(costAmount));

        state.stockQty = state.stockQty.subtract(qty);
        state.stockAmount = state.stockAmount.subtract(costAmount);
        if (temporaryQty.compareTo(BigDecimal.ZERO) > 0 && update != null) {
            update.hadTemporaryCost = true;
            update.temporaryRemainingQty = update.temporaryRemainingQty.add(temporaryQty);
            state.pendingSales.add(new PendingSale(update, ledger, temporaryQty, costPrice));
        }
        refreshCostPrice(state, costPrice);
        return costAmount;
    }

    private void coverNegativeSales(StockState state, BigDecimal coverQty, BigDecimal actualUnitPrice) {
        BigDecimal remaining = coverQty;
        while (remaining.compareTo(BigDecimal.ZERO) > 0 && !state.pendingSales.isEmpty()) {
            PendingSale pending = state.pendingSales.peek();
            BigDecimal matchedQty = remaining.min(pending.remainingQty);
            BigDecimal adjustAmount = matchedQty.multiply(actualUnitPrice.subtract(pending.estimateUnitPrice));
            if (adjustAmount.compareTo(BigDecimal.ZERO) != 0) {
                pending.update.costAmount = pending.update.costAmount.add(adjustAmount);
                pending.update.costAdjustAmount = pending.update.costAdjustAmount.add(adjustAmount);
                BigDecimal ledgerCostAmount = decimal(pending.ledger.getCostAmount()).add(adjustAmount);
                pending.ledger.setCostAmount(money(ledgerCostAmount));
                BigDecimal outQty = decimal(pending.ledger.getOutQty());
                if (outQty.compareTo(BigDecimal.ZERO) != 0) {
                    pending.ledger.setCostPrice(number(ledgerCostAmount.divide(outQty, 4, RoundingMode.HALF_UP), 4));
                    state.lastCostPrice = ledgerCostAmount.divide(outQty, 4, RoundingMode.HALF_UP);
                }
                BigDecimal afterAmount = decimal(pending.ledger.getAfterAmount()).subtract(adjustAmount);
                pending.ledger.setAfterAmount(money(afterAmount));
                BigDecimal afterQty = decimal(pending.ledger.getAfterQty());
                if (afterQty.compareTo(BigDecimal.ZERO) != 0) {
                    pending.ledger.setAfterCostPrice(number(afterAmount.divide(afterQty, 4, RoundingMode.HALF_UP), 4));
                } else {
                    pending.ledger.setAfterCostPrice(number(state.lastCostPrice, 4));
                }
                state.stockAmount = state.stockAmount.subtract(adjustAmount);
            }
            pending.remainingQty = pending.remainingQty.subtract(matchedQty);
            pending.update.temporaryRemainingQty = pending.update.temporaryRemainingQty.subtract(matchedQty);
            remaining = remaining.subtract(matchedQty);
            if (pending.remainingQty.compareTo(BigDecimal.ZERO) == 0) {
                state.pendingSales.poll();
            }
        }
        if (state.stockQty.compareTo(BigDecimal.ZERO) != 0) {
            state.costPrice = state.stockAmount.divide(state.stockQty, 4, RoundingMode.HALF_UP);
        }
    }

    private void fillAfter(AppStockLedger ledger, StockState state) {
        normalizeZeroStock(state);
        ledger.setAfterQty(number(state.stockQty, 4));
        ledger.setAfterAmount(money(state.stockAmount));
        ledger.setAfterCostPrice(number(state.costPrice, 4));
    }

    private void updateSaleCost(Map<String, Object> movement, Map<Integer, SaleCostUpdate> saleUpdates,
                                Set<Integer> saleOrderIds, BigDecimal costAmount) {
        registerSaleCost(movement, saleUpdates, saleOrderIds, costAmount);
    }

    private SaleCostUpdate registerSaleCost(Map<String, Object> movement, Map<Integer, SaleCostUpdate> saleUpdates,
                                            Set<Integer> saleOrderIds, BigDecimal costAmount) {
        Integer itemId = integer(movement.get("source_item_id"));
        if (itemId == null) {
            return null;
        }
        Integer orderId = integer(movement.get("source_id"));
        if (orderId != null) {
            saleOrderIds.add(orderId);
        }
        BigDecimal saleAmount = decimal(movement.get("total_amount"))
                .multiply(resolvePercent(movement.get("discount_rate")))
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        SaleCostUpdate update = new SaleCostUpdate(itemId, saleAmount, costAmount);
        saleUpdates.put(itemId, update);
        return update;
    }

    private void flushSaleCostUpdates(Map<Integer, SaleCostUpdate> saleUpdates) {
        if (saleUpdates == null || saleUpdates.isEmpty()) {
            return;
        }
        for (SaleCostUpdate update : saleUpdates.values()) {
            BigDecimal grossProfit = update.saleAmount.subtract(update.costAmount);
            jdbcTemplate.update("UPDATE app_sale_order_item SET cost_amount = ?, cost_adjust_amount = ?, " +
                            "cost_status = ?, gross_profit = ? WHERE id = ?",
                    money(update.costAmount), update.costAdjustAmountValue(), update.costStatus(),
                    money(grossProfit), update.itemId);
        }
    }

    private void refreshSaleOrderGrossProfits(Set<Integer> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        for (Integer orderId : orderIds) {
            jdbcTemplate.update("UPDATE app_sale_order SET gross_profit = (" +
                    "SELECT COALESCE(SUM(gross_profit), 0) FROM app_sale_order_item " +
                    "WHERE order_id = ? AND is_del = 0) WHERE id = ?", orderId, orderId);
        }
    }

    private List<Map<String, Object>> queryMovements(String goodsId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM (" +
                        " SELECT 1 AS bill_type, i.id AS source_item_id, COALESCE(o.id, 0) AS source_id, " +
                        "        'app_purchase_order' AS source_table, COALESCE(o.order_no, '') AS business_no, " +
                        "        i.quantity, i.unit_price, i.total_amount, i.is_init, " +
                        "        COALESCE(o.create_time, i.create_time) AS business_time, o.order_type, 100 AS discount_rate " +
                        " FROM app_purchase_order_item i " +
                        " LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0 " +
                        " WHERE i.is_del = 0 AND i.goods_id = ? " +
                        " AND (i.order_id IS NULL OR o.status = 1) " +
                        " AND (COALESCE(i.is_init, 0) <> 1 OR COALESCE(i.quantity, 0) <> 0) " +
                        " UNION ALL " +
                        " SELECT 2 AS bill_type, i.id AS source_item_id, o.id AS source_id, " +
                        "        'app_sale_order' AS source_table, o.order_no AS business_no, " +
                        "        i.quantity, i.unit_price, i.total_amount, 0 AS is_init, " +
                        "        o.create_time AS business_time, o.order_type, COALESCE(o.discount_rate, 100) AS discount_rate " +
                        " FROM app_sale_order_item i " +
                        " INNER JOIN app_sale_order o ON o.id = i.order_id AND o.is_del = 0 AND o.status = 1 " +
                        " WHERE i.is_del = 0 AND i.goods_id = ? " +
                        " UNION ALL " +
                        " SELECT 3 AS bill_type, i.id AS source_item_id, o.id AS source_id, " +
                        "        'app_stock_check' AS source_table, o.order_no AS business_no, " +
                        "        i.profit_loss_quantity AS quantity, i.unit_price, i.profit_loss_amount AS total_amount, " +
                        "        0 AS is_init, o.create_time AS business_time, 0 AS order_type, 100 AS discount_rate " +
                        " FROM app_stock_check_item i " +
                        " INNER JOIN app_stock_check o ON o.id = i.check_id AND o.is_del = 0 " +
                        " WHERE i.is_del = 0 AND i.goods_id = ? " +
                        ") t",
                goodsId, goodsId, goodsId);
    }

    private Map<String, Object> queryGoods(String goodsId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, stock, stock_cost, cost_price, init_cost, pur_prc FROM app_goods " +
                        "WHERE id = ? AND COALESCE(is_del, 0) = 0",
                goodsId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void updateGoodsSnapshot(String goodsId, StockState state) {
        jdbcTemplate.update("UPDATE app_goods SET stock = ?, stock_cost = ?, cost_price = ? WHERE id = ?",
                state.stockQty.setScale(0, RoundingMode.HALF_UP).intValue(),
                money(state.stockAmount), number(state.costPrice, 4), goodsId);
    }

    private void refreshGoodsPrices(String goodsId) {
        Double latestPurchasePrice = queryDouble(
                "SELECT i.unit_price FROM app_purchase_order_item i " +
                        "LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0 " +
                        "WHERE i.is_del = 0 AND i.goods_id = ? AND COALESCE(i.is_init, 0) = 0 " +
                        "AND (i.order_id IS NULL OR o.status = 1) " +
                        "ORDER BY COALESCE(o.create_time, i.create_time) DESC, i.id DESC LIMIT 1",
                goodsId);
        Double latestSalePrice = queryDouble(
                "SELECT i.unit_price FROM app_sale_order_item i " +
                        "INNER JOIN app_sale_order o ON o.id = i.order_id AND o.is_del = 0 AND o.status = 1 " +
                        "WHERE i.is_del = 0 AND i.goods_id = ? " +
                        "ORDER BY o.create_time DESC, i.id DESC LIMIT 1",
                goodsId);
        if (latestPurchasePrice != null) {
            jdbcTemplate.update("UPDATE app_goods SET pur_prc = ? WHERE id = ?", latestPurchasePrice, goodsId);
        }
        if (latestSalePrice != null) {
            jdbcTemplate.update("UPDATE app_goods SET sale_prc = ? WHERE id = ?", latestSalePrice, goodsId);
        }
    }

    private Double queryDouble(String sql, String goodsId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, goodsId);
        if (rows.isEmpty() || rows.get(0).isEmpty()) {
            return null;
        }
        Object value = rows.get(0).values().iterator().next();
        return value == null ? null : Double.valueOf(value.toString());
    }

    private void refreshCostPrice(StockState state, BigDecimal fallbackUnitPrice) {
        normalizeZeroStock(state);
        if (state.stockQty.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        state.costPrice = state.stockAmount.divide(state.stockQty, 4, RoundingMode.HALF_UP);
        if (state.costPrice.compareTo(BigDecimal.ZERO) > 0) {
            state.lastCostPrice = state.costPrice;
        } else if (fallbackUnitPrice != null && fallbackUnitPrice.compareTo(BigDecimal.ZERO) > 0) {
            state.lastCostPrice = fallbackUnitPrice;
        }
    }

    private void normalizeEndingState(StockState state) {
        normalizeZeroStock(state);
        if (state.stockQty.compareTo(BigDecimal.ZERO) != 0) {
            state.costPrice = state.stockAmount.divide(state.stockQty, 4, RoundingMode.HALF_UP);
        }
    }

    private void normalizeZeroStock(StockState state) {
        if (state.stockQty.compareTo(BigDecimal.ZERO) == 0) {
            state.stockAmount = BigDecimal.ZERO;
        }
    }

    private BigDecimal estimateCostPrice(StockState state, BigDecimal fallbackUnitPrice) {
        if (state.costPrice.compareTo(BigDecimal.ZERO) > 0) {
            return state.costPrice;
        }
        if (state.lastCostPrice.compareTo(BigDecimal.ZERO) > 0) {
            return state.lastCostPrice;
        }
        if (fallbackUnitPrice != null && fallbackUnitPrice.compareTo(BigDecimal.ZERO) > 0) {
            return fallbackUnitPrice;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveFallbackCostPrice(Map<String, Object> goods) {
        BigDecimal costPrice = decimal(goods.get("cost_price"));
        if (costPrice.compareTo(BigDecimal.ZERO) > 0) {
            return costPrice;
        }
        BigDecimal purchasePrice = decimal(goods.get("pur_prc"));
        if (purchasePrice.compareTo(BigDecimal.ZERO) > 0) {
            return purchasePrice;
        }
        return decimal(goods.get("init_cost"));
    }

    private BigDecimal resolvePurchaseUnitPrice(Map<String, Object> goods, Map<String, Object> row, BigDecimal quantity) {
        BigDecimal unitPrice = decimal(row.get("unit_price"));
        if (unitPrice.compareTo(BigDecimal.ZERO) != 0) {
            return unitPrice.abs();
        }
        BigDecimal totalAmount = decimal(row.get("total_amount"));
        if (totalAmount.compareTo(BigDecimal.ZERO) != 0 && quantity.compareTo(BigDecimal.ZERO) != 0) {
            return totalAmount.divide(quantity, 4, RoundingMode.HALF_UP).abs();
        }
        Integer isInit = integer(row.get("is_init"));
        if (isInit != null && isInit == 1) {
            return decimal(goods.get("init_cost")).abs();
        }
        return resolveFallbackCostPrice(goods).abs();
    }

    private BigDecimal resolveCheckUnitPrice(StockState state, Map<String, Object> row) {
        BigDecimal unitPrice = decimal(row.get("unit_price"));
        if (unitPrice.compareTo(BigDecimal.ZERO) != 0) {
            return unitPrice.abs();
        }
        return estimateCostPrice(state, BigDecimal.ZERO).abs();
    }

    private BigDecimal resolvePercent(Object value) {
        BigDecimal percent = decimal(value);
        return percent.compareTo(BigDecimal.ZERO) == 0 ? new BigDecimal("100") : percent;
    }

    private Date toDate(Object value) {
        long time = timeValue(value);
        return time <= 0 ? new Date(0) : new Date(time);
    }

    private long timeValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).atZone(ZONE_ID).toInstant().toEpochMilli();
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        }
        if (value instanceof Date) {
            return ((Date) value).getTime();
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).getTime();
        }
        if (value == null || StringUtils.isEmpty(value.toString())) {
            return 0L;
        }
        String text = value.toString().trim();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Timestamp.valueOf(text.replace('T', ' ')).getTime();
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return LocalDate.parse(text, DATE_FORMATTER).atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }

    private Integer integer(Object value) {
        if (value == null || StringUtils.isBlank(value.toString())) {
            return null;
        }
        return Integer.valueOf(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private Double number(BigDecimal value, int scale) {
        return (value == null ? BigDecimal.ZERO : value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private Double money(BigDecimal value) {
        return number(value, 2);
    }

    private static class StockState {
        private BigDecimal stockQty = BigDecimal.ZERO;
        private BigDecimal stockAmount = BigDecimal.ZERO;
        private BigDecimal costPrice;
        private BigDecimal lastCostPrice;
        private final ArrayDeque<PendingSale> pendingSales = new ArrayDeque<>();

        private StockState(BigDecimal fallbackCostPrice) {
            BigDecimal fallback = fallbackCostPrice == null ? BigDecimal.ZERO : fallbackCostPrice;
            this.costPrice = fallback;
            this.lastCostPrice = fallback;
        }
    }

    private static class PendingSale {
        private final SaleCostUpdate update;
        private final AppStockLedger ledger;
        private BigDecimal remainingQty;
        private final BigDecimal estimateUnitPrice;

        private PendingSale(SaleCostUpdate update, AppStockLedger ledger,
                            BigDecimal remainingQty, BigDecimal estimateUnitPrice) {
            this.update = update;
            this.ledger = ledger;
            this.remainingQty = remainingQty == null ? BigDecimal.ZERO : remainingQty;
            this.estimateUnitPrice = estimateUnitPrice == null ? BigDecimal.ZERO : estimateUnitPrice;
        }
    }

    private static class SaleCostUpdate {
        private final Integer itemId;
        private final BigDecimal saleAmount;
        private BigDecimal costAmount;
        private BigDecimal costAdjustAmount = BigDecimal.ZERO;
        private BigDecimal temporaryRemainingQty = BigDecimal.ZERO;
        private boolean hadTemporaryCost;

        private SaleCostUpdate(Integer itemId, BigDecimal saleAmount, BigDecimal costAmount) {
            this.itemId = itemId;
            this.saleAmount = saleAmount == null ? BigDecimal.ZERO : saleAmount;
            this.costAmount = costAmount == null ? BigDecimal.ZERO : costAmount;
        }

        private Double costAdjustAmountValue() {
            return costAdjustAmount.setScale(2, RoundingMode.HALF_UP).doubleValue();
        }

        private String costStatus() {
            if (!hadTemporaryCost) {
                return "NORMAL";
            }
            return temporaryRemainingQty.compareTo(BigDecimal.ZERO) > 0 ? "TEMP" : "ADJUSTED";
        }
    }
}
