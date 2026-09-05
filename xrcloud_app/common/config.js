// App frontend configuration
export const API_BASE_URL = 'http://8.152.0.49:8036'
export const APP_NAME = '新锐盈'

let clientVersionCache = ''
let clientVersionPromise = null

export function getClientDevType() {
  const systemInfo = uni.getSystemInfoSync ? uni.getSystemInfoSync() : {}
  if (systemInfo.uniPlatform) {
    return systemInfo.uniPlatform === 'app' ? 'app' : 'web'
  }

  // #ifdef APP-PLUS
  return 'app'
  // #endif

  return 'web'
}

export function getClientVersion() {
  if (clientVersionCache) return clientVersionCache
  const appBaseInfo = uni.getAppBaseInfo ? uni.getAppBaseInfo() : {}
  return String(appBaseInfo.appVersionCode || appBaseInfo.appVersion || 'web')
}

export function getClientVersionAsync() {
  if (clientVersionCache) return Promise.resolve(clientVersionCache)
  if (clientVersionPromise) return clientVersionPromise

  // #ifdef APP-PLUS
  if (typeof plus !== 'undefined' && plus.runtime && plus.runtime.getProperty) {
    clientVersionPromise = new Promise((resolve) => {
      plus.runtime.getProperty(plus.runtime.appid, (widgetInfo = {}) => {
        clientVersionCache = String(
          widgetInfo.versionCode ||
          widgetInfo.version ||
          plus.runtime.versionCode ||
          plus.runtime.version ||
          getClientVersion()
        )
        resolve(clientVersionCache)
      })
    })
    return clientVersionPromise
  }
  // #endif

  clientVersionCache = getClientVersion()
  return Promise.resolve(clientVersionCache)
}

export function initClientVersion() {
  return getClientVersionAsync()
}
