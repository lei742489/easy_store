import { API_BASE_URL, getClientDevType, getClientVersionAsync } from './config'
import { getToken } from './auth'

const request = async ({ url, data = {}, method = 'POST', timeout = 10000 }) => {
  const appVersion = await getClientVersionAsync()

  return new Promise((resolve, reject) => {
    const token = getToken()

    const header = {
      'Content-Type': 'application/json',
      devType: getClientDevType(),
      appVersion
    }
    if (token) header.token = token

    uni.request({
      url: `${API_BASE_URL.replace(/\/$/, '')}${url.startsWith('/') ? url : `/${url}`}`,
      method,
      data,
      timeout,
      header,
      success: (response) => {
        const body = response.data || {}
        if (body.success === false) {
          reject(new Error(body.message || '请求失败'))
          return
        }
        resolve(body.data)
      },
      fail: (error) => {
        reject(new Error(error.errMsg || '网络连接失败'))
      }
    })
  })
}

export default request
