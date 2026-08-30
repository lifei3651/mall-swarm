const runtime = require('../config/runtime')
const session = require('./session')

function buildQuery(params) {
  return Object.entries(params || {})
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&')
}

function request(options) {
  const method = String(options.method || 'GET').toUpperCase()
  const query = buildQuery(options.params)
  const token = session.getToken()
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${runtime.API_BASE_URL}${options.url}${query ? `?${query}` : ''}`,
      method,
      data: options.data,
      timeout: 30000,
      header: {
        'content-type': 'application/json',
        'X-Shop-Client': 'wechat-mini-program',
        'X-Shop-Surface': 'mini-program',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.idempotencyKey ? { 'X-Idempotency-Key': options.idempotencyKey } : {})
      },
      success(response) {
        const payload = response.data || {}
        if (response.statusCode === 401) session.clearSession()
        if (response.statusCode < 200 || response.statusCode >= 300 || payload.code !== 200) {
          reject(new Error(payload.message || (response.statusCode === 401 ? '请先登录' : '网络请求失败')))
          return
        }
        resolve(payload.data)
      },
      fail(error) {
        reject(new Error(error && error.errMsg ? error.errMsg.replace(/^request:fail\s*/, '') : '网络连接失败'))
      }
    })
  })
}

module.exports = request
module.exports.buildQuery = buildQuery
