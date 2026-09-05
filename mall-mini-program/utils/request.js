const runtime = require('../config/runtime')
const session = require('./session')
const encryption = require('./payload-encryption')

function buildQuery(params) {
  encryption.assertSafeQuery(params)
  return Object.entries(params || {})
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&')
}

function transport(options, token) {
  if (session.getToken() !== token) return Promise.reject(new Error('登录状态已变化，请重新操作'))
  const method = String(options.method || 'GET').toUpperCase()
  const query = buildQuery(options.params)
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${runtime.API_BASE_URL}${options.url}${query ? `${options.url.includes('?') ? '&' : '?'}${query}` : ''}`,
      method,
      data: options.data,
      timeout: 30000,
      header: {
        'content-type': 'application/json',
        'X-Shop-Client': 'wechat-mini-program',
        'X-Shop-Surface': 'mini-program',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.idempotencyKey ? { 'X-Idempotency-Key': options.idempotencyKey } : {}),
        ...(options.encryptionHeaders || {})
      },
      success(response) {
        const payload = response.data || {}
        if (session.getToken() !== token) {
          reject(new Error('登录状态已变化，请重新操作'))
          return
        }
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

async function request(options) {
  const token = session.getToken()
  const prepared = await encryption.prepareRequest(options, () => transport({
    url: '/security/payload-encryption/key', params: { _: Date.now() }, method: 'GET'
  }, token))
  return transport(prepared, token)
}

module.exports = request
module.exports.buildQuery = buildQuery
