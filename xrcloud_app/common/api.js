import request from './request'
import { API_BASE_URL, getClientDevType, getClientVersionAsync } from './config'
import { getToken } from './auth'

export const login = (data) => request({ url: '/api/login', data })

export const getUserInfo = () => request({ url: '/api/user/getInfo', data: {} })

export const listHomeMenus = () =>
  request({ url: '/api/user/appHomeMenu/listHome', data: {} })

export const pendingApproveCounts = () =>
  request({ url: '/api/user/appHomeMenu/pendingApproveCounts', data: {} })

export const getHomeStatistics = (data = {}) =>
  request({ url: '/api/user/appHomeStatistics/today', data })

export const getHomeAssetStatistics = (data = {}) =>
  request({ url: '/api/user/appHomeStatistics/asset', data })

export const listSaleOrders = (data) =>
  request({ url: '/api/user/appSaleOrder/listPage', data })

export const updateUserProfile = (data) =>
  request({ url: '/api/user/updateProfile', data })

export const updateUserPassword = (data) =>
  request({ url: '/api/user/updatePwd', data })

export const logout = (data = {}) =>
  request({ url: '/api/user/logout', data })

export const createSaleOrderNo = () =>
  request({ url: '/api/user/appSaleOrder/createOrderNo', data: {} })

export const addSaleOrder = (data) =>
  request({ url: '/api/user/appSaleOrder/add', data })

export const editSaleOrder = (data) =>
  request({ url: '/api/user/appSaleOrder/edit', data })

export const removeSaleOrder = (data) =>
  request({ url: '/api/user/appSaleOrder/remove', data })

export const listPurchaseOrders = (data) =>
  request({ url: '/api/user/appPurchaseOrder/listPage', data })

export const createPurchaseOrderNo = () =>
  request({ url: '/api/user/appPurchaseOrder/createOrderNo', data: {} })

export const addPurchaseOrder = (data) =>
  request({ url: '/api/user/appPurchaseOrder/add', data })

export const editPurchaseOrder = (data) =>
  request({ url: '/api/user/appPurchaseOrder/edit', data })

export const removePurchaseOrder = (data) =>
  request({ url: '/api/user/appPurchaseOrder/remove', data })

export const listReceivePaymentVouchers = (data) =>
  request({ url: '/api/user/appReceivePaymentVoucher/listPage', data })

export const createReceivePaymentVoucherNo = () =>
  request({ url: '/api/user/appReceivePaymentVoucher/createOrderNo', data: {} })

export const addReceivePaymentVoucher = (data) =>
  request({ url: '/api/user/appReceivePaymentVoucher/add', data })

export const editReceivePaymentVoucher = (data) =>
  request({ url: '/api/user/appReceivePaymentVoucher/edit', data })

export const removeReceivePaymentVoucher = (data) =>
  request({ url: '/api/user/appReceivePaymentVoucher/remove', data })

export const listReceivePaymentSettleItems = (paymentId, userId) =>
  request({
    url: '/api/user/appReceivePaymentSettleItem/listByOrderId',
    data: { paymentId, userId }
  })

export const listPaymentVouchers = (data) =>
  request({ url: '/api/user/appPaymentVoucher/listPage', data })

export const createPaymentVoucherNo = () =>
  request({ url: '/api/user/appPaymentVoucher/createOrderNo', data: {} })

export const addPaymentVoucher = (data) =>
  request({ url: '/api/user/appPaymentVoucher/add', data })

export const editPaymentVoucher = (data) =>
  request({ url: '/api/user/appPaymentVoucher/edit', data })

export const removePaymentVoucher = (data) =>
  request({ url: '/api/user/appPaymentVoucher/remove', data })

export const listPaymentSettleItems = (paymentId, userId) =>
  request({
    url: '/api/user/appPaymentSettleItem/listByOrderId',
    data: { paymentId, userId }
  })

export const listGoods = (data) =>
  request({ url: '/api/user/appGoods/listQuotePage', data })

export const addGoods = (data) =>
  request({ url: '/api/user/appGoods/add', data })

export const editGoods = (data) =>
  request({ url: '/api/user/appGoods/edit', data })

export const removeGoods = (id) =>
  request({ url: '/api/user/appGoods/batchRemove', data: { ids: String(id) } })

export const listGoodsUnits = () =>
  request({ url: '/api/user/appUnit/list', data: {} })

export const listGoodsSuppliers = () =>
  request({ url: '/api/user/appSupplier/list', data: {} })

export const getDefaultSupplier = () =>
  request({ url: '/api/user/appSupplier/defaultOne', data: {} })

export const listCustomerPage = (data) =>
  request({ url: '/api/user/customer/listPage', data })

export const listSupplierPage = (data) =>
  request({ url: '/api/user/appSupplier/listPage', data })

export const addCustomer = (data) =>
  request({ url: '/api/user/customer/add', data })

export const editCustomer = (data) =>
  request({ url: '/api/user/customer/edit', data })

export const removeCustomer = (id) =>
  request({ url: '/api/user/customer/remove', data: { id } })

export const listCustomers = () =>
  request({ url: '/api/user/customer/list', data: {} })

export const getDefaultCustomer = () =>
  request({ url: '/api/user/customer/defaultOne', data: {} })

export const listCustomerCategories = () =>
  request({ url: '/api/user/customer/category/list', data: {} })

export const listCustomerLevels = () =>
  request({ url: '/api/user/customer/level/list', data: {} })

export const addSupplier = (data) =>
  request({ url: '/api/user/appSupplier/add', data })

export const editSupplier = (data) =>
  request({ url: '/api/user/appSupplier/edit', data })

export const removeSupplier = (id) =>
  request({ url: '/api/user/appSupplier/remove', data: { id } })

export const listGoodsCategories = () =>
  request({ url: '/api/user/goods/category/list', data: {} })

export const listStockStatistics = (data) =>
  request({ url: '/api/user/appGoods/stockStatistics', data })

export const getStockDetail = (data) =>
  request({ url: '/api/user/appGoods/stockDetail', data })

export const listStockWarnings = (data) =>
  request({ url: '/api/user/appGoods/stockWarningPage', data })

export const listStockChecks = (data) =>
  request({ url: '/api/user/appStockCheck/listPage', data })

export const createStockCheckNo = () =>
  request({ url: '/api/user/appStockCheck/createOrderNo', data: {} })

export const addStockCheck = (data) =>
  request({ url: '/api/user/appStockCheck/add', data })

export const editStockCheck = (data) =>
  request({ url: '/api/user/appStockCheck/edit', data })

export const removeStockCheck = (data) =>
  request({ url: '/api/user/appStockCheck/remove', data })

export const rebuildStockLedger = (goodsId) =>
  request({
    url: '/api/user/appGoods/rebuildStockLedger',
    data: { goodsId }
  })

export const listAccountSettles = (userId) =>
  request({
    url: '/api/user/appAccountSettle/list',
    data: userId ? { userId } : {}
  })

export const listAccountSettlePage = (data) =>
  request({ url: '/api/user/appAccountSettle/listPage', data })

export const listAccountSettleTypes = (userId) =>
  request({
    url: '/api/user/appAccountSettle/typeList',
    data: userId ? { userId } : {}
  })

export const addAccountSettle = (data) =>
  request({ url: '/api/user/appAccountSettle/add', data })

export const editAccountSettle = (data) =>
  request({ url: '/api/user/appAccountSettle/edit', data })

export const removeAccountSettle = (data) =>
  request({ url: '/api/user/appAccountSettle/remove', data })

export const listDebtStatistics = (data) =>
  request({ url: '/api/user/appDebtStatistics/list', data })

export const listDebtDetail = (data) =>
  request({ url: '/api/user/appDebtDetail/list', data })

export const listPayableStatistics = (data) =>
  request({ url: '/api/user/appPayableReport/statistics', data })

export const listPayableDetail = (data) =>
  request({ url: '/api/user/appPayableReport/detail', data })

export const listReceivableStatement = (data) =>
  request({ url: '/api/user/appReceivableStatement/list', data })

export const listPayableStatement = (data) =>
  request({ url: '/api/user/appPayableReport/statement', data })

export const listSaleStatistics = (data) =>
  request({ url: '/api/user/appSaleStatistics/list', data })

export const listSaleStatisticsDetail = (data) =>
  request({ url: '/api/user/appSaleStatistics/detail', data })

export const listPurchaseStatistics = (data) =>
  request({ url: '/api/user/appPurchaseStatistics/list', data })

export const listPurchaseStatisticsDetail = (data) =>
  request({ url: '/api/user/appPurchaseStatistics/detail', data })

export const listFundStatisticsItems = (data) =>
  request({ url: '/api/user/appFundStatistics/items', data })

export const listFundStatistics = (data) =>
  request({ url: '/api/user/appFundStatistics/list', data })

export const listFundStatisticsDetail = (data) =>
  request({ url: '/api/user/appFundStatistics/detail', data })

export const listIncomeExpenseRecords = (data) =>
  request({ url: '/api/user/appIncomeExpenseRecord/list', data })

export const addIncomeExpenseRecord = (data) =>
  request({ url: '/api/user/appIncomeExpenseRecord/add', data })

export const listIncomeExpenseItems = (data = {}) =>
  request({ url: '/api/user/appIncomeExpenseRecord/itemList', data })

export const listProfitStatistics = (data) =>
  request({ url: '/api/user/appProfitStatistics/list', data })

export const listProfitStatisticsDetail = (data) =>
  request({ url: '/api/user/appProfitStatistics/detail', data })

export const listCashierStatistics = (data) =>
  request({ url: '/api/user/appCashierStatistics/list', data })

export const listCashierStatisticsDetail = (data) =>
  request({ url: '/api/user/appCashierStatistics/detail', data })

export const listCashierStatisticsPeriod = (data) =>
  request({ url: '/api/user/appCashierStatistics/period', data })

export const uploadGoodsImage = async (filePath) => {
  const appVersion = await getClientVersionAsync()

  return new Promise((resolve, reject) => {
    const token = getToken()
    const header = {
      devType: getClientDevType(),
      appVersion
    }
    if (token) header.token = token

    uni.uploadFile({
      url: `${API_BASE_URL.replace(/\/$/, '')}/api/file/upload`,
      filePath,
      name: 'file',
      header,
      success: (response) => {
        let body = response.data || {}
        if (typeof body === 'string') {
          try {
            body = JSON.parse(body)
          } catch (error) {
            reject(new Error('图片上传返回数据解析失败'))
            return
          }
        }
        if (body.success === false) {
          reject(new Error(body.message || '图片上传失败'))
          return
        }
        resolve(body.data)
      },
      fail: (error) => {
        reject(new Error(error.errMsg || '图片上传失败'))
      }
    })
  })
}

export const listAppUsers = () =>
  request({ url: '/api/user/list', data: {} })

export const listAppUserPage = (data) =>
  request({ url: '/api/user/listPage', data })

export const addAppUser = (data) =>
  request({ url: '/api/user/add', data })

export const editAppUser = (data) =>
  request({ url: '/api/user/edit', data })

export const removeAppUser = (data) =>
  request({ url: '/api/user/remove', data })

export const resetAppUserPassword = (data) =>
  request({ url: '/api/user/resetPwd', data })

export const listAppRolePage = (data) =>
  request({ url: '/api/user/appRole/listPage', data })

export const listAppRoles = () =>
  request({ url: '/api/user/appRole/list', data: {} })

export const addAppRole = (data) =>
  request({ url: '/api/user/appRole/add', data })

export const editAppRole = (data) =>
  request({ url: '/api/user/appRole/edit', data })

export const removeAppRole = (data) =>
  request({ url: '/api/user/appRole/remove', data })

export const listAppRoleMenuIds = (roleId) =>
  request({ url: '/api/user/appRole/menuIds', data: { roleId } })

export const listAppRolePermissionCodes = (roleId) =>
  request({ url: '/api/user/appRole/permissionCodes', data: { roleId } })

export const listAllAppMenus = () =>
  request({ url: '/api/user/appHomeMenu/listAll', data: {} })

export const listAppUnitPage = (data) =>
  request({ url: '/api/user/appUnit/listPage', data })

export const addAppUnit = (data) =>
  request({ url: '/api/user/appUnit/add', data })

export const editAppUnit = (data) =>
  request({ url: '/api/user/appUnit/edit', data })

export const removeAppUnit = (data) =>
  request({ url: '/api/user/appUnit/remove', data })

export const listAppOperationLogPage = (data) =>
  request({ url: '/api/user/appOperationLog/listPage', data })

export const searchCustomers = (key) =>
  request({ url: '/api/user/customer/searchKey', data: { key } })
