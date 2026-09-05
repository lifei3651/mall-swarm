import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { randomBytes } from 'node:crypto'
import { spawn } from 'node:child_process'
import { createInterface } from 'node:readline'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

// Run backend PayloadEncryptionServiceImplTest first so its compiled classes/classpath exist.
// The bridge never connects to Redis, any HTTP endpoint or a real account.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const xml = readFileSync(resolve(root, 'mall-distribution/target/surefire-reports/TEST-com.macro.mall.distribution.service.impl.PayloadEncryptionServiceImplTest.xml'), 'utf8')
const classpath = xml.match(/<property name="java.class.path" value="([^"]*)"/)[1]
  .replaceAll('&quot;', '"').replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&')
const child = spawn('java', ['--class-path', classpath, resolve(root, 'mall-mini-program/tests/fixtures/PayloadEncryptionInterop.java')], { stdio: ['pipe', 'pipe', 'pipe'] })
const pending = []
let diagnostics = ''
child.stderr.on('data', (chunk) => { diagnostics += chunk.toString() })
child.on('error', (error) => { while (pending.length) pending.shift().reject(error) })
child.on('exit', (code) => {
  if (pending.length) { const error = new Error(`Java fixture exited (${code}); check compilation/runtime prerequisites`); while (pending.length) pending.shift().reject(error) }
})
createInterface({ input: child.stdout }).on('line', (line) => {
  if (!line.startsWith('{')) return // Dependency logging is not test protocol.
  const waiter = pending.shift()
  if (!waiter) return
  try { waiter.resolve(JSON.parse(line)) } catch (error) { waiter.reject(error) }
})
function bridge(command) {
  return new Promise((resolveReply, reject) => {
    const timeout = setTimeout(() => { child.kill(); reject(new Error('Java fixture timed out')) }, 30000)
    pending.push({ resolve: (value) => { clearTimeout(timeout); resolveReply(value) }, reject: (error) => { clearTimeout(timeout); reject(error) } })
    child.stdin.write(`${JSON.stringify(command)}\n`)
  })
}
globalThis.wx = { getRandomValues({ length, success }) {
  const bytes = randomBytes(length)
  success({ randomValues: bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) })
} }
const { prepareRequest } = createRequire(import.meta.url)('../utils/payload-encryption.js')
const prepare = (data) => prepareRequest({ url: '/fixture', method: 'POST', data }, () => bridge({ operation: 'issue' }))
const decrypt = (prepared, body = prepared.data) => bridge({ operation: 'decrypt', body,
  challengeId: prepared.encryptionHeaders['X-Payload-Encryption-Id'], encryptedKey: prepared.encryptionHeaders['X-Payload-Encryption-Key'] })
try {
  const original = { account: 'fixture', password: 'fixture🔐密码', nested: [{ smsCode: 'fixture-code', RealName: '测试姓名' }] }
  const encrypted = await prepare(original)
  const roundtrip = await decrypt(encrypted)
  assert.equal(roundtrip.accepted, true, 'Production Java service must accept mini-program hybrid encryption')
  assert.equal(JSON.stringify(roundtrip.body), JSON.stringify(original), 'Production must decode every nested value without dropping or modifying fields')
  assert.equal((await decrypt(encrypted)).accepted, false, 'Production challenge is one-time')
  const tampered = await prepare({ smsCode: 'fixture' })
  const chunks = tampered.data.smsCode.split(':'), cipher = Buffer.from(chunks[3], 'base64')
  cipher[0] ^= 1; chunks[3] = cipher.toString('base64'); tampered.data.smsCode = chunks.join(':')
  assert.equal((await decrypt(tampered)).accepted, false, 'Production GCM rejects tampering')
  const wrongField = await prepare({ smsCode: 'fixture' })
  assert.equal((await decrypt(wrongField, { password: wrongField.data.smsCode })).accepted, false, 'Production AAD binds field')
  const plaintext = await prepare({ smsCode: 'fixture' })
  assert.equal((await decrypt(plaintext, { smsCode: 'fixture' })).accepted, false, 'Production refuses unencrypted field even with valid encryption headers')
  const wrongChallenge = await prepare({ smsCode: 'fixture' })
  const other = await bridge({ operation: 'issue' })
  wrongChallenge.encryptionHeaders['X-Payload-Encryption-Id'] = other.challengeId
  assert.equal((await decrypt(wrongChallenge)).accepted, false, 'Production AAD binds challenge')
  console.log('Payload interoperability: 6/6 passed (real Java production service; no network, accounts or persisted secrets).')
} finally {
  child.stdin.end(); child.kill()
  delete globalThis.wx
  diagnostics = ''
}
