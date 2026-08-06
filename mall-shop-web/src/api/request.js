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
