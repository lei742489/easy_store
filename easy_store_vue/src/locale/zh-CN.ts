import localeMessageBox from '@/components/message-box/locale/zh-CN';
import localeLogin from '@/views/login/locale/zh-CN';

import localeWorkplace from '@/views/dashboard/workplace/locale/zh-CN';

import localeSettings from './zh-CN/settings';

export default {
  'menu.dashboard': '仪表盘',
  'menu.server.dashboard': '仪表盘-服务端',
  'menu.server.workplace': '工作台-服务端',
  'menu.server.monitor': '实时监控-服务端',
  'menu.list': '列表页',
  'menu.result': '结果页',
  'menu.exception': '异常页',
  'menu.form': '表单页',
  'menu.profile': '详情页',
  'menu.visualization': '数据可视化',
  'menu.user': '个人中心',
  'menu.arcoWebsite': 'Arco Design',
  'menu.faq': '常见问题',
  'navbar.docs': '文档中心',
  'navbar.action.locale': '切换为中文',
  'menu.custom.saleOrder': '销售单',
  'menu.custom.saleOrder.list': '销售单查询',
  'menu.custom.receivePaymentOrder': '收款单',
  'menu.custom.receivePaymentOrderList': '收款单查询',
  'menu.custom.purchaseOrder': '进货单',
  'menu.custom.purchaseOrder.list': '进货单查询',
  'menu.custom.paymentOrder': '付款单',
  'menu.custom.paymentOrderQuery': '付款单查询',
  'menu.custom.inventoryManagement': '库存管理',
  'menu.custom.customerManagement': '客户管理',
  'menu.custom.supplierManagement': '供应商管理',
  'menu.custom.customerQuotation': '大客户报价',
  'menu.custom.accountSettle': '结算帐户',
  'menu.custom.appUserManagement': '员工管理',
  'menu.custom.appRole': '角色管理',
  'menu.custom.appUnit': '字典单位',
  'menu.custom.setting': '系统设置',
  'menu.custom.salesStatistics': '销售统计',
  'menu.custom.purchaseStatistics': '进货统计',
  'menu.custom.debtStatistics': '应收欠款统计',
  'menu.custom.outstandingBalancePayable': '应付欠款统计',
  'menu.custom.detailsOfOutstandingDebts': '应收欠款明细',
  'menu.custom.detailsOutstandingBalancePayable': '应付欠款明细',
  'menu.custom.statementOfAccount': '应收对帐单',
  'menu.custom.outstandingBalancePayableOrder': '应付对帐单',

  ...localeSettings,
  ...localeMessageBox,
  ...localeLogin,
  ...localeWorkplace,
};
