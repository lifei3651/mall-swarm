const crypto = require('../vendor/payload-crypto/index')

// Keep this set aligned with PayloadEncryptionServiceImpl.SENSITIVE_FIELDS.
const SENSITIVE_FIELDS = new Set([
  'password', 'currentpassword', 'currentadminpassword', 'newpassword', 'oldpassword',
  'loginpassword', 'paymentpassword', 'adminpassword', 'confirmpassword', 'smscode',
  'currentphonesmscode', 'newphonesmscode', 'captchacode', 'appsecret', 'callbacktoken',
  'realname', 'idcard', 'code'
])
const SECURITY_ERROR = '安全加密组件暂不可用，请更新微信或稍后重试'
const isSensitive = (key) => SENSITIVE_FIELDS.has(String(key).toLowerCase())
const hasValue = (value) => value !== null && value !== undefined && value !== ''

function queryKeyIsSensitive(key) {
  return String(key).split(/[.\[\]]/).some((part) => isSensitive(part.trim()))
}

function assertSafeQuery(params, url = '') {
  function scan(value) {
    if (!value || typeof value !== 'object') return
    for (const [key, child] of Object.entries(value)) {
      if (queryKeyIsSensitive(key) && hasValue(child)) throw new Error('敏感信息不能放在请求地址中')
      if (child && typeof child === 'object') scan(child)
    }
  }
  scan(params)
  const query = String(url).split('?')[1]
  if (!query) return
  for (const pair of query.split('#')[0].split('&')) {
    const separator = pair.indexOf('=')
    const rawKey = separator < 0 ? pair : pair.slice(0, separator)
    let key
    try { key = decodeURIComponent(rawKey.replace(/\+/g, ' ')) } catch (_) { throw new Error('请求地址格式不正确') }
    if (queryKeyIsSensitive(key)) throw new Error('敏感信息不能放在请求地址中')
  }
}

// Snapshot the exact JSON shape before asynchronous work. This also closes toJSON/getter mutation gaps.
function snapshot(value) {
  if (value === undefined) return undefined
  let result
  try { result = typeof value === 'string' ? JSON.parse(value) : JSON.parse(JSON.stringify(value)) }
  catch (_) { throw new Error('请求数据格式不正确') }
  return result
}

function sensitiveFields(value, fields = []) {
  if (Array.isArray(value)) {
    value.forEach((child) => sensitiveFields(child, fields))
  } else if (value && typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) {
      if (isSensitive(key) && hasValue(child)) {
        if (typeof child !== 'string') throw new Error('敏感字段格式不正确，请重新输入')
        fields.push({ owner: value, key, value: child })
      } else sensitiveFields(child, fields)
    }
  }
  return fields
}

function secureRandom(length) {
  if (typeof wx === 'undefined' || typeof wx.getRandomValues !== 'function') return Promise.reject(new Error(SECURITY_ERROR))
  return new Promise((resolve, reject) => {
    let settled = false
    const timeout = setTimeout(() => { settled = true; reject(new Error(SECURITY_ERROR)) }, 10000)
    const finish = (error, bytes) => {
      if (settled) return
      settled = true
      clearTimeout(timeout)
      if (error) reject(new Error(SECURITY_ERROR))
      else resolve(bytes)
    }
    try {
      wx.getRandomValues({ length,
        success: (response) => {
          const buffer = response && response.randomValues
          if (Object.prototype.toString.call(buffer) !== '[object ArrayBuffer]' || buffer.byteLength !== length) { finish(true); return }
          finish(false, new Uint8Array(buffer).slice())
        },
        fail: () => finish(true)
      })
    } catch (_) { finish(true) }
  })
}

function validateChallenge(challenge) {
  if (!challenge || challenge.algorithm !== 'RSA-OAEP-256+A256GCM'
    || typeof challenge.challengeId !== 'string'
    || !/^[a-f0-9]{32}\.\d{13}\.[A-Za-z0-9_-]{43}$/.test(challenge.challengeId)
    || !Number.isSafeInteger(challenge.expiresAt)
    || challenge.expiresAt <= Date.now() || challenge.expiresAt > Date.now() + 125000
    || Number(challenge.challengeId.split('.')[1]) !== challenge.expiresAt) throw new Error(SECURITY_ERROR)
  return crypto.readPublicKey(challenge.publicKey)
}

async function prepareRequest(options, loadChallenge) {
  options = { ...options }
  const method = String(options.method || 'GET').toUpperCase()
  const params = snapshot(options.params)
  assertSafeQuery(params, options.url)
  const data = snapshot(options.data)
  if (method === 'GET' || method === 'HEAD') assertSafeQuery(data)
  const fields = sensitiveFields(data)
  if (!fields.length) return { ...options, params, data, method, encryptionHeaders: {} }
  if (method === 'GET' || method === 'HEAD') throw new Error('敏感信息不能放在请求地址中')
  if (typeof wx === 'undefined' || typeof wx.getRandomValues !== 'function') throw new Error(SECURITY_ERROR)
  let material
  try {
    const challenge = await loadChallenge()
    const publicKey = validateChallenge(challenge)
    // One random allocation keeps key, OAEP seed and all field IVs distinct byte ranges.
    material = await secureRandom(64 + fields.length * 12)
    if (challenge.expiresAt <= Date.now()) throw new Error(SECURITY_ERROR)
    const aesKey = material.subarray(0, 32)
    const wrappedKey = crypto.wrapKey(publicKey, aesKey, material.subarray(32, 64))
    const usedIvs = new Set()
    for (let index = 0; index < fields.length; index++) {
      const field = fields[index], iv = material.subarray(64 + index * 12, 76 + index * 12)
      const ivKey = Array.from(iv).join(',')
      if (usedIvs.has(ivKey)) throw new Error(SECURITY_ERROR)
      usedIvs.add(ivKey)
      field.owner[field.key] = crypto.encryptValue(aesKey, iv, `${challenge.challengeId}:${field.key.toLowerCase()}`, field.value)
    }
    return { ...options, params, data, method, encryptionHeaders: {
      'X-Payload-Encryption-Id': challenge.challengeId,
      'X-Payload-Encryption-Key': wrappedKey
    } }
  } catch (_) {
    // Do not expose library errors that may contain request/key material, and never fall back to plaintext.
    throw new Error(SECURITY_ERROR)
  } finally {
    if (material) material.fill(0)
    fields.forEach((field) => { field.value = '' })
  }
}

module.exports = { prepareRequest, assertSafeQuery }
