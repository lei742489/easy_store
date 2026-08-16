import { DEFAULT_LAYOUT } from '../base';
import { AppRouteRecordRaw } from '../types';

const FORM: AppRouteRecordRaw = {
  path: '/custom',
  name: 'custom',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.form',
    icon: 'icon-settings',
    requiresAuth: true,
    order: 3,
  },
  children: [
    {
      path: 'userInfo',
      name: 'UserInfo',
      component: () => import('@/views/app/AppUser/setting/index.vue'),
      meta: {
        locale: 'menu.user',
        requiresAuth: false,
      },
    },
    {
      path: 'salesOrder',
      name: 'SalesOrder',
      component: () => import('@/views/app/AppSaleOrder/list.vue'),
      meta: {
        locale: 'menu.custom.saleOrder.list',
        requiresAuth: false,
      },
    },
    {
      path: 'salesStatistics',
      name: 'SalesStatistics',
      component: () => import('@/views/app/AppSaleStatistics/list.vue'),
      meta: {
        locale: 'menu.custom.salesStatistics',
        requiresAuth: false,
      },
    },
    {
      path: 'purchaseStatistics',
      name: 'PurchaseStatistics',
      component: () => import('@/views/app/AppPurchaseStatistics/list.vue'),
      meta: {
        locale: 'menu.custom.purchaseStatistics',
        requiresAuth: false,
      },
    },
    {
      path: 'profitStatistics',
      name: 'ProfitStatistics',
      component: () => import('@/views/app/AppProfitStatistics/list.vue'),
      meta: {
        locale: '利润统计',
        requiresAuth: false,
      },
    },
    {
      path: 'cashierStatistics',
      name: 'CashierStatistics',
      component: () => import('@/views/app/AppCashierStatistics/list.vue'),
      meta: {
        locale: '营业员统计',
        requiresAuth: false,
      },
    },
    {
      path: 'fundStatistics',
      name: 'FundStatistics',
      component: () => import('@/views/app/AppFundStatistics/list.vue'),
      meta: {
        locale: '资金统计',
        requiresAuth: false,
      },
    },
    {
      path: 'fundStatisticsDetail',
      name: 'FundStatisticsDetail',
      component: () => import('@/views/app/AppFundStatistics/detail.vue'),
      meta: {
        locale: '资金统计明细',
        requiresAuth: false,
      },
    },
    {
      path: 'customer',
      name: 'Customer',
      component: () => import('@/views/app/customer/list.vue'),
      meta: {
        locale: 'menu.custom.customerManagement',
        requiresAuth: false,
      },
    },
    {
      path: 'customerQuote',
      name: 'CustomerQuote',
      component: () => import('@/views/app/AppCustomerQuote/list.vue'),
      meta: {
        locale: 'menu.custom.customerQuotation',
        requiresAuth: false,
      },
    },
    {
      path: 'goods',
      name: 'Goods',
      component: () => import('@/views/app/goods/list.vue'),
      meta: {
        locale: 'menu.custom.inventoryManagement',
        requiresAuth: false,
      },
    },
    {
      path: 'supplier',
      name: 'Supplier',
      component: () => import('@/views/app/AppSupplier/list.vue'),
      meta: {
        locale: 'menu.custom.supplierManagement',
        requiresAuth: false,
      },
    },
    {
      path: 'accountSettle',
      name: 'AccountSettle',
      component: () => import('@/views/app/AppAccountSettle/list.vue'),
      meta: {
        locale: 'menu.custom.accountSettle',
        requiresAuth: false,
      },
    },
    {
      path: 'appUser',
      name: 'AppUser',
      component: () => import('@/views/app/AppUser/list.vue'),
      meta: {
        locale: 'menu.custom.appUserManagement',
        requiresAuth: false,
      },
    },
    {
      path: 'appRole',
      name: 'AppRole',
      component: () => import('@/views/app/AppRole/list.vue'),
      meta: {
        locale: 'menu.custom.appRole',
        requiresAuth: false,
      },
    },
    {
      path: 'appSaleOrder',
      name: 'AppSaleOrder',
      component: () => import('@/views/app/AppPurchaseOrder/list.vue'),
      meta: {
        locale: 'menu.custom.purchaseOrder.list',
        requiresAuth: false,
      },
    },
    {
      path: 'appUnit',
      name: 'AppUnit',
      component: () => import('@/views/app/AppUnit/list.vue'),
      meta: {
        locale: 'menu.custom.appUnit',
        requiresAuth: false,
      },
    },
    {
      path: 'incomeExpenseRecord',
      name: 'IncomeExpenseRecord',
      component: () => import('@/views/app/AppIncomeExpenseRecord/list.vue'),
      meta: {
        locale: '收支记录',
        requiresAuth: false,
      },
    },
    {
      path: 'appPaymentVoucher',
      name: 'AppPaymentVoucher',
      component: () => import('@/views/app/AppPaymentVoucher/list.vue'),
      meta: {
        locale: 'menu.custom.paymentOrder',
        requiresAuth: false,
      },
    },
    {
      path: 'appReceivePaymentVoucher',
      name: 'AppReceivePaymentVoucher',
      component: () => import('@/views/app/AppReceivePaymentVoucher/list.vue'),
      meta: {
        locale: 'menu.custom.receivePaymentOrderList',
        requiresAuth: false,
      },
    },
    {
      path: 'debtDetail',
      name: 'DebtDetail',
      component: () => import('@/views/app/AppDebtDetail/list.vue'),
      meta: {
        locale: 'menu.custom.detailsOfOutstandingDebts',
        requiresAuth: false,
      },
    },
    {
      path: 'debtStatistics',
      name: 'DebtStatistics',
      component: () => import('@/views/app/AppDebtStatistics/list.vue'),
      meta: {
        locale: 'menu.custom.debtStatistics',
        requiresAuth: false,
      },
    },
    {
      path: 'receivableStatement',
      name: 'ReceivableStatement',
      component: () => import('@/views/app/AppReceivableStatement/list.vue'),
      meta: {
        locale: 'menu.custom.statementOfAccount',
        requiresAuth: false,
      },
    },
    {
      path: 'payableStatement',
      name: 'PayableStatement',
      component: () => import('@/views/app/AppPayableReport/list.vue'),
      meta: {
        locale: 'menu.custom.outstandingBalancePayableOrder',
        requiresAuth: false,
      },
    },
    {
      path: 'payableStatistics',
      name: 'PayableStatistics',
      component: () => import('@/views/app/AppPayableReport/list.vue'),
      meta: {
        locale: 'menu.custom.outstandingBalancePayable',
        requiresAuth: false,
      },
    },
    {
      path: 'payableDetail',
      name: 'PayableDetail',
      component: () => import('@/views/app/AppPayableReport/list.vue'),
      meta: {
        locale: 'menu.custom.detailsOutstandingBalancePayable',
        requiresAuth: false,
      },
    },
    {
      path: 'stockStatistics',
      name: 'StockStatistics',
      component: () => import('@/views/app/AppStockStatistics/list.vue'),
      meta: {
        locale: '库存统计',
        requiresAuth: false,
      },
    },
    {
      path: 'stockWarning',
      name: 'StockWarning',
      component: () => import('@/views/app/AppStockWarning/list.vue'),
      meta: {
        locale: '库存预警',
        requiresAuth: false,
      },
    },
    {
      path: 'stockCheck',
      name: 'StockCheck',
      component: () => import('@/views/app/AppStockCheck/list.vue'),
      meta: {
        locale: '库存盘点',
        requiresAuth: false,
      },
    },
  ],
};

export default FORM;
