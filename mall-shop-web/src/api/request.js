import axios from 'axios'
import router from '@surface-router'
import { apiBaseUrl, resolvePublicMediaUrls } from '@/utils/appEnvironment'
import { encryptSensitiveRequest } from '@/utils/payloadEncryption'
import { loginRedirectLocation, notifyAuthRequired } from '@/utils/authNavigation'
import { clearShopSession, finishLegacyTokenMigration, getLegacyShopToken } from '@/utils/shopSession'
import { appSurface } from '@/utils/appSurface'

const service = axios.create({
  baseURL: apiBaseUrl,
  timeout: 30000,
  withCredentials: true,
})

// 移动网络在页面切换、从支付宝/微信返回时，偶尔会把一次幂等的查询请求
// 中断。GET 请求可以安全重试一次，避免订单页把短暂的连接抖动误报成空白页。
const RETRYABLE_METHODS = new Set(['get', 'head', 'options'])
const isTransientTransportError = (error) => {
  if (error?.response) return false
  return ['ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)
    || error?.message === 'Network Error'
}

const waitBeforeRetry = (delay = 250) => new Promise((resolve) => setTimeout(resolve, delay))

service.interceptors.request.use(async (config) => {
  config.headers['X-Shop-Client'] = 'storefront'
  config.headers['X-Shop-Surface'] = appSurface
  const legacyToken = getLegacyShopToken()
  const authPath = String(config.url || '')
  const createsSession = ['/shop/auth/login', '/shop/auth/register', '/shop/public/auth/register', '/shop/auth/resetPassword'].includes(authPath)
  if (legacyToken && !createsSession) {
    config.headers.Authorization = `Bearer ${legacyToken}`
    // 只有 /auth/me 会在服务端校验旧 Token 并换发 HttpOnly Cookie。
    // 其他并发请求成功时不能提前删除旧 Token，否则页面初始化可能把用户误退出。
    config.__legacyShopTokenMigration = authPath === '/shop/auth/me'
  }
  return encryptSensitiveRequest(config)
})

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && res.code !== 200) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    if (response.config?.__legacyShopTokenMigration) finishLegacyTokenMigration()
    return resolvePublicMediaUrls(res)
  },
  async (error) => {
    const config = error?.config
    const method = String(config?.method || 'get').toLowerCase()
    const retryCount = Number(config?.__transportRetryCount || 0)
    if (config && RETRYABLE_METHODS.has(method) && retryCount < 1 && isTransientTransportError(error)) {
      config.__transportRetryCount = retryCount + 1
      await waitBeforeRetry()
      return service.request(config)
    }

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
    const message = error.response?.data?.message
      || (isTransientTransportError(error) ? '网络暂时不可用，请检查网络后重试' : error.message)
      || '请求失败'
    return Promise.reject(new Error(message))
  }
)

export default service
