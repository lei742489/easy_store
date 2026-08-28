import request from './request'
import { API_BASE_URL } from './config'
import { getToken } from './auth'

export const login = (data) => request({ url: '/api/login', data })

export const getUserInfo = () => request({ url: '/api/user/getInfo', data: {} })

export const listHomeMenus = () =>
  request({ url: '/api/user/appHomeMenu/listHome', data: {} })

export const pendingApproveCounts = () =>
  request({ url: '/api/user/appHomeMenu/pendingApproveCounts', data: {} })

export const listSaleOrders = (data) =>
  request({ url: '/api/user/appSaleOrder/listPage', data })

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

export const listGoodsCategories = () =>
  request({ url: '/api/user/goods/category/list', data: {} })

export const uploadGoodsImage = (filePath) =>
  new Promise((resolve, reject) => {
    const token = getToken()
    const header = {}
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

export const listAppUsers = () =>
  request({ url: '/api/user/list', data: {} })

export const searchCustomers = (key) =>
  request({ url: '/api/user/customer/searchKey', data: { key } })
