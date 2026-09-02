const TOKEN_KEY = 'easy-store-app-token'
const USER_KEY = 'easy-store-app-user'
const LOGIN_USERNAME_KEY = 'easy-store-app-login-username'

const sanitizeUser = (user) => {
  if (!user) return null
  const { password, salt, token, ...safeUser } = user
  return safeUser
}

export const getToken = () => uni.getStorageSync(TOKEN_KEY) || ''

export const getLastLoginUsername = () =>
  uni.getStorageSync(LOGIN_USERNAME_KEY) || ''

export const saveLastLoginUsername = (username) => {
  uni.setStorageSync(LOGIN_USERNAME_KEY, String(username || '').trim())
}

export const getUser = () => {
  const value = uni.getStorageSync(USER_KEY)
  if (!value) return null
  if (typeof value === 'string') {
    try {
      return JSON.parse(value)
    } catch (error) {
      return null
    }
  }
  return value
}

export const saveSession = (user) => {
  if (!user || !user.token || !user.id) {
    throw new Error('登录返回的用户信息不完整')
  }
  uni.setStorageSync(TOKEN_KEY, user.token)
  uni.setStorageSync(USER_KEY, sanitizeUser(user))
}

export const updateUser = (user) => {
  if (user) uni.setStorageSync(USER_KEY, sanitizeUser(user))
}

export const clearSession = () => {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

export const isLoggedIn = () => !!getToken()
