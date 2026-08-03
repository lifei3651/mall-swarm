#!/usr/bin/env node

const baseUrl = (process.argv[2] || 'https://lingqimall.com').replace(/\/$/, '')
const encoder = new TextEncoder()

const toBase64 = (value) => Buffer.from(value).toString('base64')
const fromBase64 = (value) => Buffer.from(value, 'base64')

async function challenge() {
  const response = await fetch(`${baseUrl}/api/security/payload-encryption/key?_=${Date.now()}`, {
    headers: { 'Cache-Control': 'no-store' },
  })
  const body = await response.json()
  if (!response.ok || Number(body.code) !== 200 || !body.data?.challengeId || !body.data?.publicKey) {
    throw new Error(`无法获取安全挑战：${body.message || response.status}`)
  }
  return body.data
}

async function encryptRequest(data) {
  const current = await challenge()
  const publicKey = await crypto.subtle.importKey(
    'spki',
    fromBase64(current.publicKey),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )
  const aesKey = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt'])
  const rawAesKey = await crypto.subtle.exportKey('raw', aesKey)
  const encryptedKey = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, rawAesKey)

  const encrypted = { ...data }
  for (const fieldName of ['password', 'captchaCode']) {
    const iv = crypto.getRandomValues(new Uint8Array(12))
    const cipherText = await crypto.subtle.encrypt({
      name: 'AES-GCM',
      iv,
      additionalData: encoder.encode(`${current.challengeId}:${fieldName.toLowerCase()}`),
      tagLength: 128,
    }, aesKey, encoder.encode(String(data[fieldName])))
    encrypted[fieldName] = `enc:v1:${toBase64(iv)}:${toBase64(new Uint8Array(cipherText))}`
  }

  return {
    data: encrypted,
    headers: {
      'Content-Type': 'application/json',
      'X-Payload-Encryption-Id': current.challengeId,
      'X-Payload-Encryption-Key': toBase64(new Uint8Array(encryptedKey)),
    },
  }
}

async function verify(name, path, data) {
  const encrypted = await encryptRequest(data)
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: encrypted.headers,
    body: JSON.stringify(encrypted.data),
  })
  const result = await response.json()
  const message = String(result.message || '')
  if (message.includes('页面安全组件已更新') || message.includes('安全请求已失效')) {
    throw new Error(`${name}加密请求仍被误判：${message}`)
  }
  if (!message) throw new Error(`${name}没有返回可验证的业务响应`)
  console.log(`${name}: HTTP ${response.status}, ${message}`)
}

await verify('商城登录', '/api/shop/auth/login', {
  account: '__release_verification_account__',
  password: '__release_verification_password__',
  captchaId: '__release_verification_captcha__',
  captchaCode: '0000',
})

await verify('后台登录', '/api/distribution/admin-auth/login', {
  username: '__release_verification_admin__',
  password: '__release_verification_password__',
  captchaId: '__release_verification_captcha__',
  captchaCode: '0000',
})

