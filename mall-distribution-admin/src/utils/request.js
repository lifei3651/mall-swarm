import axios from 'axios'
import { ElMessage } from 'element-plus'
import { expireAdminSession, isAdminSessionExpired } from '@/utils/adminSession'
import { encryptSensitiveRequest } from '@/utils/payloadEncryption'
import { requestAdminStepUpToken } from '@/utils/adminStepUp'

// 创建axios实例
const service = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true,
})

const isAuthenticationFailure = (code, message) => [401, 419, 440].includes(Number(code)) || [
  '后台登录已失效',
  '后台登录已超时',
  '登录状态已失效',
  '登录状态已过期',
  '登录会话已失效',
  '登录会话已过期',
  '会话已失效',
  '会话已过期',
  '请先登录',
  'token已经过期',
  'token已过期',
  'Token已经过期',
  'Token已过期',
  '未授权',
  '请重新登录',
].some((item) => String(message || '').includes(item))

const showError = (message) => ElMessage({
  type: 'error',
  message: message || '请求失败',
  grouping: true,
})

const createRequestError = (message, details = {}) => {
  const error = new Error(message || '请求失败')
  Object.assign(error, details)
  return error
}

// 请求拦截器
service.interceptors.request.use(
  async (config) => {
    // 只使用 HttpOnly Cookie，不再从浏览器存储读取或发送 Bearer Token。
    const hasSession = localStorage.getItem('admin_session_present') === '1'
    if (hasSession && isAdminSessionExpired()) {
      const message = '后台登录已超时，请重新登录'
      expireAdminSession(message)
      const error = new Error(message)
      error.isAdminSessionExpired = true
      return Promise.reject(error)
    }
    config.headers['X-Admin-Client'] = 'admin-web'
    if (config.adminStepUp) {
      config.headers['X-Admin-Step-Up-Token'] = await requestAdminStepUpToken(config)
      delete config.adminStepUp
    }
    return encryptSensitiveRequest(config)
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res.code !== 200) {
      if (isAuthenticationFailure(res.code, res.message)) {
        expireAdminSession(res.message || '后台登录已失效，请重新登录')
      } else if (!response.config.silentError) {
        showError(res.message || '请求失败')
      }
      return Promise.reject(createRequestError(res.message, {
        code: res.code,
        responseStatus: response.status,
        isSilentRequest: Boolean(response.config.silentError),
      }))
    }
    return res
  },
  (error) => {
    if (error.isAdminSessionExpired || error.isAdminStepUpCancelled) {
      return Promise.reject(error)
    }
    console.error('响应错误:', error)
    const serverMessage = error.response?.data?.message
    const silentError = Boolean(error.config?.silentError)
    if (error.response) {
      const responseStatus = Number(error.response.status)
      const responseCode = error.response?.data?.code
      if (isAuthenticationFailure(responseStatus, serverMessage)
        || isAuthenticationFailure(responseCode, serverMessage)) {
        expireAdminSession(serverMessage || '后台登录已失效，请重新登录')
      } else switch (responseStatus) {
        case 403:
          if (!silentError) showError(serverMessage || '拒绝访问')
          break
        case 404:
          if (!silentError) showError(serverMessage || '请求地址不存在')
          break
        case 413:
          if (!silentError) showError(serverMessage || '单张图片不能超过5MB，请压缩后重试')
          break
        case 500:
          if (!silentError) showError(serverMessage || '服务器内部错误')
          break
        default:
          if (!silentError) showError(serverMessage || error.message || '请求失败')
      }
    } else if (!silentError) {
      showError('网络连接异常')
    }
    return Promise.reject(createRequestError(serverMessage || error.message, {
      code: error.response?.data?.code,
      responseStatus: error.response?.status,
      isSilentRequest: silentError,
      originalError: error,
    }))
  }
)

export default service
