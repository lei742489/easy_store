BEGIN TRANSACTION;

INSERT INTO app_home_menu
    (code, group_code, group_title, name, icon, url, action, sort_no, status, root_only, create_time, is_del)
VALUES
    ('sale_order_add', 'common', '常用功能', '销售单', 'sm1.png', '', 'SaleOrderModalRef', 10, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('receive_payment_add', 'common', '常用功能', '收款单', 'sm2.png', '', 'ReceivePaymentVoucherModal', 20, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('purchase_order_add', 'common', '常用功能', '进货单', 'sm3.png', '', 'PurchaseOrderModal', 30, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('payment_add', 'common', '常用功能', '付款单', 'sm4.png', '', 'PaymentVoucherModal', 40, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('goods', 'common', '常用功能', '库存管理', 'sm5.png', '/custom/goods', '', 50, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('customer', 'common', '常用功能', '客户管理', 'sm6.png', '/custom/customer', '', 60, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('supplier', 'common', '常用功能', '供应商管理', 'sm7.png', '/custom/supplier', '', 70, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('customer_quote', 'common', '常用功能', '大客户报价', 'sm8.png', '/custom/customerQuote', '', 80, 1, 0, '2026-08-11 05:04:51.000', 1),
    ('account_settle', 'common', '常用功能', '结算账户', 'sm9.png', '/custom/accountSettle', '', 90, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('app_user', 'other', '其它功能', '员工管理', 'icon-user-group', '/custom/appUser', '', 410, 1, 1, '2026-08-11 05:04:51.000', 0),
    ('app_role', 'other', '其它功能', '角色管理', 'icon-safe', '/custom/appRole', '', 420, 1, 1, '2026-08-11 05:04:51.000', 0),
    ('app_unit', 'other', '其它功能', '单位管理', 'icon-storage', '/custom/appUnit', '', 430, 1, 1, '2026-08-11 05:04:51.000', 0),
    ('income_expense_record', 'other', '其它功能', '收支记录', 'icon-book', '/custom/incomeExpenseRecord', '', 440, 1, 1, '2026-08-11 05:04:51.000', 0),
    ('purchase_order_list', 'purchase', '进货/库存', '进货单查询', 'jm1.png', '/custom/appSaleOrder', '', 210, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('payment_list', 'purchase', '进货/库存', '付款单查询', 'sm4.png', '/custom/appPaymentVoucher', '', 220, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('purchase_stats', 'purchase', '进货/库存', '进货统计', 'jm2.png', '/custom/purchaseStatistics', '', 230, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('payable_order', 'purchase', '进货/库存', '应付对帐单', 'jm3.png', '/custom/payableStatement', '', 240, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('payable_stats', 'purchase', '进货/库存', '应付统计', 'jm4.png', '/custom/payableStatistics', '', 250, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('payable_detail', 'purchase', '进货/库存', '应付明细', 'jm5.png', '/custom/payableDetail', '', 260, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('stock_stats', 'purchase', '进货/库存', '库存统计', 'cc1.png', '/custom/stockStatistics', '', 270, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('stock_warning', 'purchase', '进货/库存', '库存预警', 'cc2.png', '/custom/stockWarning', '', 280, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('stock_check', 'purchase', '进货/库存', '库存盘点', 'cc3.png', '/custom/stockCheck', '', 290, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('fund_stats', 'report', '统计报告', '资金统计', 'tj1.png', '/custom/fundStatistics', '', 310, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('profit_stats', 'report', '统计报告', '利润统计', 'tj2.png', '/custom/profitStatistics', '', 320, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('cashier_stats', 'report', '统计报告', '营业员统计', 'tj3.png', '/custom/cashierStatistics', '', 330, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('sale_order_list', 'sale', '销售相关', '销售单查询', 'dm1.png', '/custom/salesOrder', '', 110, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('receive_payment_list', 'sale', '销售相关', '收款单查询', 'sm2.png', '/custom/appReceivePaymentVoucher', '', 120, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('sale_stats', 'sale', '销售相关', '销售统计', 'dm2.png', '/custom/salesStatistics', '', 130, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('customer_statement', 'sale', '销售相关', '应收对账单', 'dm4.png', '/custom/receivableStatement', '', 140, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('debt_stats', 'sale', '销售相关', '欠款统计', 'dm3.png', '/custom/debtStatistics', '', 150, 1, 0, '2026-08-11 05:04:51.000', 0),
    ('debt_detail', 'sale', '销售相关', '欠款明细', 'dm5.png', '/custom/debtDetail', '', 160, 1, 0, '2026-08-11 05:04:51.000', 0)
ON CONFLICT(code) DO UPDATE SET
    group_code = excluded.group_code,
    group_title = excluded.group_title,
    name = excluded.name,
    icon = excluded.icon,
    url = CASE
        WHEN app_home_menu.url IS NULL OR trim(app_home_menu.url) = '' THEN excluded.url
        ELSE app_home_menu.url
    END,
    action = excluded.action,
    sort_no = excluded.sort_no,
    status = excluded.status,
    root_only = excluded.root_only,
    is_del = excluded.is_del;

COMMIT;
