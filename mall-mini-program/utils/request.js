const runtime = require('../config/runtime')
const session = require('./session')
const encryption = require('./payload-encryption')
const { transportError, statusError } = require('./transport-error')
const { retryDelay } = require('./h5-rules/transportRetry')

function buildQuery(params) {
  encryption.assertSafeQuery(params)
  return Object.entries(params || {})
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&')
}

async function transport(options, token, retryCount = 0) {
  if (session.getToken() !== token) return Promise.reject(new Error('登录状态已变化，请重新操作'))
  const method = String(options.method || 'GET').toUpperCase()
  const query = buildQuery(options.params)
  try { return await new Promise((resolve, reject) => {
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
          const message = typeof payload.message === 'string' && /[\u3400-\u9fff]/.test(payload.message) && !/<(?:html|script)|exception|stacktrace/i.test(payload.message) ? payload.message : statusError(response.statusCode)
          const failure = new Error(message)
          failure.response = { status: response.statusCode }
          failure.sessionExpired = response.statusCode === 401
          reject(failure)
          return
        }
        resolve(payload.data)
      },
      fail(error) {
        const failure = new Error(transportError(error))
        const raw = String(error && error.errMsg || '')
        // Configuration, certificates and user cancellation are not transient network failures.
        if (!/ssl|cert|domain|permission|denied|cancel|abort|url not in/i.test(raw)) {
          if (/timeout|time out/i.test(raw)) failure.code = 'ETIMEDOUT'
          else if (/network|socket|connection|connect|offline|internet|reset/i.test(raw)) failure.code = 'ERR_NETWORK'
        }
        reject(failure)
      }
    })
  }) } catch (error) {
    if (error.sessionExpired && !session.getToken()) throw error
    if (session.getToken() !== token) throw new Error('登录状态已变化，请重新操作')
    const delay = retryDelay(method, error, retryCount)
    if (delay === null) throw error
    await new Promise(resolve => setTimeout(resolve, delay))
    return transport(options, token, retryCount + 1)
  }
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
