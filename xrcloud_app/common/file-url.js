import { API_BASE_URL } from './config'

export const getStaticFileUrl = (value) => {
  if (!value) return ''
  const filePath = String(value).trim()
  if (!filePath) return ''
  if (/^(https?:|data:|blob:)/i.test(filePath)) return filePath

  const normalizedPath = filePath.startsWith('/') ? filePath : `/${filePath}`
  return `${API_BASE_URL.replace(/\/$/, '')}/api/upload/static${normalizedPath}`
}
