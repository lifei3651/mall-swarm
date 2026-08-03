import axios from 'axios'

const SENSITIVE_FIELDS = new Set([
  'password',
  'currentpassword',
  'newpassword',
  'oldpassword',
  'loginpassword',
  'paymentpassword',
  'adminpassword',
  'confirmpassword',
  'smscode',
  'captchacode',
  'appsecret',
  'callbacktoken',
  'code',
])

const textEncoder = new TextEncoder()
const isPlainObject = (value) => Object.prototype.toString.call(value) === '[object Object]'
const isSensitiveField = (key) => SENSITIVE_FIELDS.has(String(key).toLowerCase())

const containsSensitiveValue = (value) => {
  if (Array.isArray(value)) return value.some(containsSensitiveValue)
  if (!isPlainObject(value)) return false
  return Object.entries(value).some(([key, child]) => (
    (isSensitiveField(key) && typeof child === 'string' && child.length > 0)
    || containsSensitiveValue(child)
  ))
}

const bytesToBase64 = (bytes) => {
  let binary = ''
  for (let index = 0; index < bytes.length; index += 1) binary += String.fromCharCode(bytes[index])
  return window.btoa(binary)
}

const base64ToBytes = (value) => {
  const binary = window.atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index)
  return bytes
}

const requestChallenge = async () => {
  const response = await axios.get('/api/security/payload-encryption/key', {
    timeout: 10000,
    params: { _: Date.now() },
    headers: { 'Cache-Control': 'no-store' },
  })
  const result = response.data
  if (!result || Number(result.code) !== 200 || !result.data?.challengeId || !result.data?.publicKey) {
    throw new Error(result?.message || '安全加密组件暂不可用，请稍后重试')
  }
  return result.data
}

const encryptSensitiveValue = async (value, fieldName, challengeId, aesKey) => {
  const iv = window.crypto.getRandomValues(new Uint8Array(12))
  const additionalData = textEncoder.encode(`${challengeId}:${String(fieldName).toLowerCase()}`)
  const cipherText = await window.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, additionalData, tagLength: 128 },
    aesKey,
    textEncoder.encode(value),
  )
  return `enc:v1:${bytesToBase64(iv)}:${bytesToBase64(new Uint8Array(cipherText))}`
}

const encryptObject = async (value, challengeId, aesKey) => {
  if (Array.isArray(value)) {
    return Promise.all(value.map((child) => encryptObject(child, challengeId, aesKey)))
  }
  if (!isPlainObject(value)) return value

  const encryptedEntries = await Promise.all(Object.entries(value).map(async ([key, child]) => {
    if (isSensitiveField(key) && typeof child === 'string' && child.length > 0) {
      return [key, await encryptSensitiveValue(child, key, challengeId, aesKey)]
    }
    return [key, await encryptObject(child, challengeId, aesKey)]
  }))
  return Object.fromEntries(encryptedEntries)
}

export const encryptSensitiveRequest = async (config) => {
  if (!containsSensitiveValue(config.data)) return config
  if (!window.crypto?.subtle) {
    throw new Error('当前浏览器不支持安全加密，请升级浏览器后重试')
  }

  const challenge = await requestChallenge()
  const publicKey = await window.crypto.subtle.importKey(
    'spki',
    base64ToBytes(challenge.publicKey),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )
  const aesKey = await window.crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt'],
  )
  const rawAesKey = await window.crypto.subtle.exportKey('raw', aesKey)
  const encryptedKey = await window.crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, rawAesKey)

  config.data = await encryptObject(config.data, challenge.challengeId, aesKey)
  const encryptedKeyValue = bytesToBase64(new Uint8Array(encryptedKey))
  if (typeof config.headers?.set === 'function') {
    config.headers.set('X-Payload-Encryption-Id', challenge.challengeId)
    config.headers.set('X-Payload-Encryption-Key', encryptedKeyValue)
  } else {
    config.headers = config.headers || {}
    config.headers['X-Payload-Encryption-Id'] = challenge.challengeId
    config.headers['X-Payload-Encryption-Key'] = encryptedKeyValue
  }
  return config
}
