import axios from 'axios'
import router from '@/router'
import { apiBaseUrl, resolvePublicMediaUrls } from '@/utils/appEnvironment'
import { encryptSensitiveRequest } from '@/utils/payloadEncryption'
import { loginRedirectLocation, notifyAuthRequired } from '@/utils/authNavigation'
import { clearShopSession } from '@/utils/shopSession'

const service = axios.create({
  baseURL: apiBaseUrl,
  timeout: 30000,
})

service.interceptors.request.use(async (config) => {
  const token = localStorage.getItem('shop_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return encryptSensitiveRequest(config)
})

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && res.code !== 200) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return resolvePublicMediaUrls(res)
  },
  (error) => {
    // 处理401未授权（token过期或无效）
    if (error.response && error.response.status === 401) {
      clearShopSession()
      const current = router.currentRoute.value
      const isAuthPage = ['Login', 'Register', 'ForgotPassword'].includes(current.name)
      if (!isAuthPage) {
        notifyAuthRequired('登录状态已失效，请重新登录')
        router.replace(loginRedirectLocation(current.fullPath))
      }
    }
    const message = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

export default service
