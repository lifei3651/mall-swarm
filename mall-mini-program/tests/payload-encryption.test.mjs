import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createHash, generateKeyPairSync, randomBytes, privateDecrypt, createDecipheriv, constants } from 'node:crypto'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const pair = generateKeyPairSync('rsa', { modulusLength: 2048 })
const publicKey = pair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64')
const plain = (value) => JSON.parse(JSON.stringify(value))
const fields = ['password', 'currentpassword', 'currentadminpassword', 'newpassword', 'oldpassword',
  'loginpassword', 'paymentpassword', 'adminpassword', 'confirmpassword', 'smscode',
  'currentphonesmscode', 'newphonesmscode', 'captchacode', 'appsecret', 'callbacktoken', 'realname', 'idcard', 'code']
const challenge = (patch = {}) => {
  const expiresAt = Date.now() + 120000
  return { publicKey, algorithm: 'RSA-OAEP-256+A256GCM', expiresAt,
    challengeId: `${randomBytes(16).toString('hex')}.${expiresAt}.${randomBytes(32).toString('base64url')}`, ...patch }
}
const random = ({ length, success }) => {
  const bytes = randomBytes(length)
  success({ randomValues: bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) })
}

function harness({ randomSource = random, response, token = 'fixture-session-a', clockTimeout = setTimeout } = {}) {
  const calls = [], cache = new Map()
  let cleared = 0, randomCalls = 0
  const session = { getToken: () => token, clearSession: () => { cleared++; token = '' } }
  const wx = { request(options) {
    calls.push(options)
    if (response) return response(options)
    options.success({ statusCode: 200, data: { code: 200,
      data: options.url.includes('/security/payload-encryption/key') ? challenge() : { ok: true } } })
  } }
  if (randomSource) wx.getRandomValues = (options) => { randomCalls++; return randomSource(options) }
  const context = vm.createContext({ wx, setTimeout: clockTimeout, clearTimeout })
  // Deliberately no window, self, Buffer, Node crypto or browser WebCrypto globals.
  function load(path) {
    const filename = resolve(path.endsWith('.js') ? path : `${path}.js`)
    if (filename === resolve(root, 'config/runtime.js')) return { API_BASE_URL: 'https://fixture.invalid/api' }
    if (filename === resolve(root, 'utils/session.js')) return session
    if (cache.has(filename)) return cache.get(filename).exports
    const module = { exports: {} }; cache.set(filename, module)
    vm.runInContext(`(function(require,module,exports){${readFileSync(filename, 'utf8')}\n})`, context, { filename })(
      (id) => { assert.ok(id.startsWith('.'), 'Bundle must not load runtime dependencies'); return load(resolve(dirname(filename), id)) },
      module, module.exports)
    return module.exports
  }
  return { request: load(resolve(root, 'utils/request.js')), adapter: load(resolve(root, 'utils/payload-encryption.js')),
    calls, context, setToken: (next) => { token = next }, getToken: () => token,
    cleared: () => cleared, randomCalls: () => randomCalls }
}

function decode(call, field, value, changedChallenge) {
  const rawKey = privateDecrypt({ key: pair.privateKey, oaepHash: 'sha256', padding: constants.RSA_PKCS1_OAEP_PADDING },
    Buffer.from(call.header['X-Payload-Encryption-Key'], 'base64'))
  assert.equal(rawKey.length, 32)
  const [, , iv64, cipher64] = value.split(':')
  const encrypted = Buffer.from(cipher64, 'base64')
  const decipher = createDecipheriv('aes-256-gcm', rawKey, Buffer.from(iv64, 'base64'))
  decipher.setAuthTag(encrypted.subarray(-16))
  decipher.setAAD(Buffer.from(`${changedChallenge || call.header['X-Payload-Encryption-Id']}:${field.toLowerCase()}`, 'utf8'))
  return Buffer.concat([decipher.update(encrypted.subarray(0, -16)), decipher.final()]).toString('utf8')
}
const submit = (h, data = { smsCode: 'fixture-code' }) => h.request({ url: '/shop/orders', method: 'POST', data, idempotencyKey: 'fixture-idempotency' })
const tick = () => new Promise((resolveTick) => setImmediate(resolveTick))

test('native-like VM imports real bundle without browser/Node globals; ordinary GET never needs crypto', async () => {
  const h = harness({ randomSource: null })
  assert.deepEqual(plain(await h.request({ url: '/shop/products', params: { page: 1, q: '中文', omit: null } })), { ok: true })
  assert.equal(h.calls.length, 1)
  assert.match(h.calls[0].url, /page=1&q=%E4%B8%AD%E6%96%87$/)
  assert.equal(h.calls[0].header.Authorization, 'Bearer fixture-session-a')
  assert.equal(h.randomCalls(), 0)
  assert.equal(vm.runInContext('typeof window + ":" + typeof self + ":" + typeof Buffer + ":" + typeof crypto', h.context), 'undefined:undefined:undefined:undefined')
})

test('all backend-sensitive fields encrypt recursively with OAEP-SHA256/MGF1-SHA256 and GCM, preserving original data', async () => {
  const data = { account: 'fixture-account', items: fields.map((field, i) => ({ [i % 2 ? field.toUpperCase() : field]: `测试🔐-${i}` })), empty: { password: '', smsCode: null } }
  const original = plain(data), h = harness()
  await submit(h, data)
  assert.equal(h.calls.length, 2)
  const call = h.calls[1]
  assert.equal(call.header['X-Idempotency-Key'], 'fixture-idempotency')
  assert.equal(call.header['X-Shop-Client'], 'wechat-mini-program')
  assert.equal(call.data.account, original.account)
  assert.deepEqual(plain(data), original)
  const ivs = new Set()
  call.data.items.forEach((item, i) => {
    const [field, value] = Object.entries(item)[0]
    assert.match(value, /^enc:v1:/)
    assert.equal(decode(call, field, value), Object.values(original.items[i])[0])
    ivs.add(value.split(':')[2])
  })
  assert.equal(ivs.size, fields.length)
  assert.equal(h.randomCalls(), 1)
})

test('field AAD, challenge AAD and GCM tampering are rejected by independent Node crypto', async () => {
  const h = harness(); await submit(h)
  const call = h.calls[1], encrypted = call.data.smsCode
  assert.throws(() => decode(call, 'password', encrypted))
  assert.throws(() => decode(call, 'smsCode', encrypted, `${call.header['X-Payload-Encryption-Id']}x`))
  const chunks = encrypted.split(':'), bytes = Buffer.from(chunks[3], 'base64')
  bytes[0] ^= 1; chunks[3] = bytes.toString('base64')
  assert.throws(() => decode(call, 'smsCode', chunks.join(':')))
})

test('randomness absent, throwing, failing, short or duplicate IV fails closed without a business request', async () => {
  for (const randomSource of [null, () => { throw Error('upstream must not leak') }, (o) => o.fail({ errMsg: 'upstream must not leak' }),
    (o) => o.success({ randomValues: new ArrayBuffer(1) }), (o) => o.success({ randomValues: new ArrayBuffer(o.length) })]) {
    const h = harness({ randomSource })
    await assert.rejects(submit(h, { smsCode: 'fixture', password: 'fixture' }), /^Error: 安全加密组件/)
    assert.ok(h.calls.every((call) => call.url.includes('/security/payload-encryption/key')))
  }
})

test('random timeout fails closed', async () => {
  const h = harness({ randomSource() {}, clockTimeout: (fn) => setTimeout(fn, 1) })
  await assert.rejects(submit(h), /安全加密组件/)
  assert.equal(h.calls.length, 1)
})

test('a challenge expiring while random is pending cannot be submitted', async () => {
  let pending
  const h = harness({ randomSource: (o) => { pending = o } })
  const result = submit(h); await tick()
  vm.runInContext('Date.now = () => 9999999999999', h.context)
  random(pending)
  await assert.rejects(result, /安全加密组件/)
  assert.equal(h.calls.length, 1)
})

test('untrusted or expired challenge and malformed RSA key cannot fall back to plaintext', async () => {
  for (const patch of [{ algorithm: 'RSA-OAEP+A256GCM' }, { expiresAt: Date.now() - 1 },
    { expiresAt: Date.now() + 500000 }, { challengeId: 'malformed' }, { publicKey: 'not-a-key' },
    { publicKey: `${publicKey}AA==` }]) {
    const h = harness({ response: (o) => o.success({ statusCode: 200, data: { code: 200, data: challenge(patch) } }) })
    await assert.rejects(submit(h), /安全加密组件/)
    assert.equal(h.calls.length, 1)
    assert.equal(h.randomCalls(), 0)
  }
})

test('challenge network failure sends no body and returns only generic security error', async () => {
  const h = harness({ response: (o) => o.fail({ errMsg: 'fixture-private-server-error' }) })
  await assert.rejects(submit(h), /^Error: 安全加密组件/)
  assert.equal(h.calls.length, 1)
  assert.equal(h.calls[0].data, undefined)
})

test('weak RSA key, unexpected RSA exponent or non-RSA key is rejected before random generation', async () => {
  const keys = [generateKeyPairSync('rsa', { modulusLength: 1024 }).publicKey,
    generateKeyPairSync('rsa', { modulusLength: 2048, publicExponent: 3 }).publicKey,
    generateKeyPairSync('ec', { namedCurve: 'prime256v1' }).publicKey]
  for (const key of keys) {
    const publicKey = key.export({ type: 'spki', format: 'der' }).toString('base64')
    const h = harness({ response: (o) => o.success({ statusCode: 200, data: { code: 200, data: challenge({ publicKey }) } }) })
    await assert.rejects(submit(h), /安全加密组件/)
    assert.equal(h.calls.length, 1); assert.equal(h.randomCalls(), 0)
  }
})

test('sensitive params, encoded URL keys, nested keys and GET bodies never reach network', async () => {
  const cases = [
    { params: { smsCode: 'fixture' } }, { params: { 'account.password': 'fixture' } },
    { params: { items: [{ password: 'fixture' }] } }, { url: '/shop/products?%73msCode=fixture' },
    { url: '/shop/products?x%5Bpassword%5D=fixture' }, { method: 'GET', data: { password: 'fixture' } },
    { method: 'HEAD', data: { password: 'fixture' } }, { params: { password: 123 } }
  ]
  for (const options of cases) {
    const h = harness()
    await assert.rejects(h.request({ url: '/shop/products', ...options }), /敏感信息不能放在请求地址中/)
    assert.equal(h.calls.length, 0)
  }
})

test('non-string sensitive values and cyclic bodies are blocked; JSON string/toJSON shapes are encrypted', async () => {
  for (const value of [123, true, { plaintext: 'fixture' }, ['fixture']]) {
    const h = harness(); await assert.rejects(submit(h, { smsCode: value }), /敏感字段格式/); assert.equal(h.calls.length, 0)
  }
  const cyclic = {}; cyclic.self = cyclic
  const bad = harness(); await assert.rejects(submit(bad, cyclic), /请求数据格式/); assert.equal(bad.calls.length, 0)
  for (const body of ['{"smsCode":"fixture"}', { toJSON: () => ({ smsCode: 'fixture' }) }]) {
    const h = harness(); await submit(h, body)
    assert.equal(decode(h.calls[1], 'smsCode', h.calls[1].data.smsCode), 'fixture')
  }
})

test('async challenge takes a stable body snapshot and each retry gets a fresh challenge and key', async () => {
  let pending
  const h = harness({ response: (o) => {
    if (o.url.includes('/security/')) pending = o
    else o.success({ statusCode: 200, data: { code: 200, data: true } })
  } })
  const body = { smsCode: 'before-await' }, first = submit(h, body)
  body.smsCode = 'after-await'
  pending.success({ statusCode: 200, data: { code: 200, data: challenge() } })
  await first
  const second = submit(h, body)
  pending.success({ statusCode: 200, data: { code: 200, data: challenge() } })
  await second
  const a = h.calls[1], b = h.calls[3]
  assert.equal(decode(a, 'smsCode', a.data.smsCode), 'before-await')
  assert.equal(decode(b, 'smsCode', b.data.smsCode), 'after-await')
  assert.notEqual(a.header['X-Payload-Encryption-Id'], b.header['X-Payload-Encryption-Id'])
  assert.notEqual(a.header['X-Payload-Encryption-Key'], b.header['X-Payload-Encryption-Key'])
})

test('request URL and idempotency key are snapshotted before encryption awaits', async () => {
  let pending
  const h = harness({ response: (o) => {
    if (o.url.includes('/security/')) pending = o
    else o.success({ statusCode: 200, data: { code: 200, data: true } })
  } })
  const options = { url: '/shop/orders', method: 'POST', data: { smsCode: 'fixture' }, idempotencyKey: 'original' }
  const result = h.request(options)
  options.url = '/shop/orders?password=fixture'; options.idempotencyKey = 'mutated'
  pending.success({ statusCode: 200, data: { code: 200, data: challenge() } })
  await result
  assert.equal(h.calls[1].url, 'https://fixture.invalid/api/shop/orders')
  assert.equal(h.calls[1].header['X-Idempotency-Key'], 'original')
})

test('switching accounts while challenge is pending blocks submission and preserves new session', async () => {
  let pending
  const h = harness({ response: (o) => { pending = o } })
  const result = submit(h); h.setToken('fixture-session-b')
  pending.success({ statusCode: 200, data: { code: 200, data: challenge() } })
  await assert.rejects(result)
  assert.equal(h.calls.length, 1)
  assert.equal(h.getToken(), 'fixture-session-b'); assert.equal(h.cleared(), 0)
})

test('switching accounts during secure random callback cannot submit old form under new token', async () => {
  let pending
  const h = harness({ randomSource: (o) => { pending = o } })
  const result = submit(h); await tick()
  h.setToken('fixture-session-b'); random(pending)
  await assert.rejects(result, /登录状态已变化/)
  assert.equal(h.calls.length, 1); assert.equal(h.cleared(), 0)
})

test('late old-account 401 does not clear new session; same-account 401 still clears it', async () => {
  for (const changed of [false, true]) {
    let pending
    const h = harness({ response: (o) => { pending = o } })
    const result = h.request({ url: '/shop/profile' }); await tick()
    if (changed) h.setToken('fixture-session-b')
    pending.success({ statusCode: 401, data: { code: 401 } })
    await assert.rejects(result, changed ? /登录状态已变化/ : /请先登录/)
    assert.equal(h.cleared(), changed ? 0 : 1)
    assert.equal(h.getToken(), changed ? 'fixture-session-b' : '')
  }
})

test('session switch before ordinary GET transport rejects without sending, late success is isolated too', async () => {
  const h = harness(), result = h.request({ url: '/shop/profile' })
  h.setToken('fixture-session-b'); await assert.rejects(result, /登录状态已变化/)
  assert.equal(h.calls.length, 0)
  let pending
  const late = harness({ response: (o) => { pending = o } })
  const response = late.request({ url: '/shop/profile' }); await tick()
  late.setToken('fixture-session-b'); pending.success({ statusCode: 200, data: { code: 200, data: { private: 'old-account' } } })
  await assert.rejects(response, /登录状态已变化/)
})

test('caller cannot inject transport authentication through encryptionHeaders on ordinary or sensitive requests', async () => {
  for (const data of [{ quantity: 1 }, { smsCode: 'fixture' }]) {
    const h = harness()
    await h.request({ url: '/fixture', method: 'POST', data, encryptionHeaders: { Authorization: 'Bearer caller-supplied', 'X-Payload-Encryption-Id': 'caller-supplied' } })
    const sent = h.calls.at(-1)
    assert.equal(sent.header.Authorization, 'Bearer fixture-session-a')
    assert.notEqual(sent.header['X-Payload-Encryption-Id'], 'caller-supplied')
  }
})

test('sensitive field set exactly matches backend, vendored source digest and no implicit random/code generation', () => {
  const backend = readFileSync(resolve(root, '../mall-distribution/src/main/java/com/macro/mall/distribution/service/impl/PayloadEncryptionServiceImpl.java'), 'utf8')
  const fieldSet = [...backend.match(/SENSITIVE_FIELDS = Set\.of\(([\s\S]*?)\);/)[1].matchAll(/"([a-z]+)"/g)].map((match) => match[1])
  assert.deepEqual(fields, fieldSet)
  const source = readFileSync(resolve(root, 'utils/payload-encryption.js'), 'utf8')
  assert.deepEqual([...source.match(/SENSITIVE_FIELDS = new Set\(\[([\s\S]*?)\]\)/)[1].matchAll(/'([a-z]+)'/g)].map((match) => match[1]), fields)
  const vendor = readFileSync(resolve(root, 'vendor/payload-crypto/index.js'))
  const provenance = JSON.parse(readFileSync(resolve(root, 'vendor/payload-crypto/SOURCE.json'), 'utf8'))
  assert.equal(createHash('sha256').update(vendor).digest('hex'), provenance.sha256)
  assert.doesNotMatch(vendor.toString(), /Math\.random|\beval\s*\(|\bnew Function\s*\(/)
})
