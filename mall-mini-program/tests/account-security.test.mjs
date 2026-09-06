import { runMiniScript } from './helpers/run-mini-script.mjs'
import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'

const plain = (value) => JSON.parse(JSON.stringify(value))
const member = { id: '9212345678901234567', username: 'MallTester', phone: '13800138000', nickname: '测试用户' }
const input = (field, value) => ({ currentTarget: { dataset: { field } }, detail: { value } })
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }

function harness({ respond = () => member, loggedIn = true } = {}) {
  let definition, cleared = 0, now = 100000
  const requests = [], routes = [], timers = new Map()
  const DateMock = class extends Date { static now() { return now } }
  runMiniScript(readFileSync(new URL('../pages/account-security/index.js', import.meta.url), 'utf8'), {
    Page: (value) => { definition = value },
    require: (path) => {
      if (path === '../../utils/request') return async (options) => { requests.push(plain(options)); return respond(options) }
      if (path === '../../utils/auth') return { requireLogin: (redirect) => { if (!loggedIn) routes.push(`login:${redirect}`); return loggedIn } }
      if (path === '../../utils/session') return { clearSession: () => { cleared++; loggedIn = false } }
      if (path === '../../utils/theme') return { pageData: () => ({}), apply() {}, sync() {} }
      if (path === '../../utils/privacy') return { requireConsent: async () => {} }
      if (path === '../../utils/member-avatar') return { fallback: '/assets/profile/user-round.png', load: async () => '/assets/profile/user-round.png', release() {} }
      assert.fail(`Unknown dependency ${path}`)
    },
    Date: DateMock,
    setTimeout: (fn) => { const id = Symbol(); timers.set(id, fn); return id },
    clearTimeout: (id) => timers.delete(id),
    wx: { redirectTo: ({ url }) => routes.push(url), showToast() {}, setStorageSync() { assert.fail('账号安全页面不得持久化凭据') } },
    console: { log() { assert.fail('账号安全页面不得打印凭据') } }
  })
  const page = { ...definition, data: plain(definition.data), setData(patch) { Object.assign(this.data, plain(patch)) } }
  return { page, requests, routes, timers, cleared: () => cleared, setLogin: (value) => { loggedIn = value }, advance: (value) => { now += value } }
}

test('账号安全必须登录，登录返回可加载本人资料并掩码手机号', async () => {
  const h = harness({ loggedIn: false })
  await h.page.onShow()
  assert.deepEqual(h.requests, [])
  assert.equal(h.routes[0], 'login:/pages/account-security/index')
  h.setLogin(true); await h.page.onShow()
  assert.equal(h.page.data.maskedPhone, '138****8000')
  assert.equal(h.page.data.canSetupAccount, false)
})

test('仅未设置或username等于手机号可首次设置，已命名账号不可再次重设', async () => {
  for (const [username, expected] of [['', true], [member.phone, true], ['MallTester', false]]) {
    const h = harness({ respond: () => ({ ...member, username }) })
    await h.page.onShow()
    assert.equal(h.page.data.canSetupAccount, expected)
  }
})

test('资料加载失败清空账号对象，旧页面不能继续变更密码', async () => {
  const h = harness({ respond: () => { throw new Error('登录过期') } })
  h.page.setData({ member, currentPassword: 'OldCredential#1', canSetupAccount: true })
  await h.page.onShow()
  assert.equal(h.page.data.member, null)
  assert.equal(h.page.data.currentPassword, '')
  await h.page.setupAccount(); await h.page.changePassword()
  assert.equal(h.requests.length, 1)
})

test('昵称合法性校验且只更新昵称字段，不混入其他账号属性', async () => {
  const h = harness()
  await h.page.onShow()
  h.page.fieldInput(input('nickname', '坏<script>'))
  await h.page.saveNickname(); assert.equal(h.requests.length, 1)
  h.page.fieldInput(input('nickname', '  测试   用户  '))
  await h.page.saveNickname()
  assert.deepEqual(h.requests[1], { url: '/shop/auth/nickname', method: 'PUT', data: { nickname: '测试 用户' } })
  assert.equal(h.cleared(), 0)
})

test('首次设置校验账号/密码/确认密码，成功后清理全部秘密与当前会话', async () => {
  const h = harness({ respond: () => ({ ...member, username: member.phone }) })
  await h.page.onShow()
  h.page.setData({ username: '1234', password: 'AValidSecret#9', confirmPassword: 'AValidSecret#9' })
  await h.page.setupAccount(); assert.equal(h.requests.length, 1)
  h.page.setData({ username: 'FreshMember', password: 'short', confirmPassword: 'short' })
  await h.page.setupAccount(); assert.equal(h.requests.length, 1)
  h.page.setData({ password: 'AValidSecret#9', confirmPassword: 'different' })
  await h.page.setupAccount(); assert.equal(h.requests.length, 1)
  h.page.setData({ confirmPassword: 'AValidSecret#9' }); await h.page.setupAccount()
  assert.deepEqual(h.requests[1], { url: '/shop/auth/account', method: 'PUT', data: { username: 'FreshMember', password: 'AValidSecret#9' } })
  assert.equal(h.cleared(), 1)
  assert.equal(h.page.data.password, '')
  assert.equal(h.page.data.confirmPassword, '')
  assert.equal(h.routes.at(-1), '/pages/login/index')
})

test('修改密码必须当前密码、绑定手机验证码、合格新密码及两次一致', async () => {
  const h = harness()
  await h.page.onShow()
  h.page.setData({ newPassword: 'AValidSecret#9', confirmPassword: 'AValidSecret#9', smsCode: '123456' })
  await h.page.changePassword(); assert.equal(h.requests.length, 1)
  h.page.setData({ currentPassword: 'OldCredential#1', smsCode: '123' })
  await h.page.changePassword(); assert.equal(h.requests.length, 1)
  h.page.setData({ smsCode: '123456', newPassword: 'MallTester1234', confirmPassword: 'MallTester1234' })
  await h.page.changePassword(); assert.equal(h.requests.length, 1)
  h.page.setData({ newPassword: 'AValidSecret#9', confirmPassword: 'AValidSecret#9' }); await h.page.changePassword()
  assert.deepEqual(h.requests[1], { url: '/shop/auth/password', method: 'PUT', data: { currentPassword: 'OldCredential#1', newPassword: 'AValidSecret#9', smsCode: '123456' } })
  assert.equal(h.cleared(), 1)
  for (const key of ['currentPassword', 'newPassword', 'confirmPassword', 'smsCode']) assert.equal(h.page.data[key], '')
})

test('密码保存失败清空密码及验证码，不伪装成功、不撤销仍有效会话', async () => {
  const h = harness({ respond: ({ method }) => { if (method === 'PUT') throw new Error('验证码错误'); return member } })
  await h.page.onShow()
  h.page.setData({ currentPassword: 'OldCredential#1', newPassword: 'AValidSecret#9', confirmPassword: 'AValidSecret#9', smsCode: '123456' })
  await h.page.changePassword()
  assert.equal(h.page.data.error, '验证码错误')
  assert.equal(h.page.data.smsCode, '')
  assert.equal(h.page.data.currentPassword, '')
  assert.equal(h.cleared(), 0)
  assert.equal(h.routes.length, 0)
})

test('重复点击账号保存不产生双请求，正在保存时不能改表单', async () => {
  const pending = deferred()
  const h = harness({ respond: ({ method }) => method === 'PUT' ? pending.promise : { ...member, username: member.phone } })
  await h.page.onShow()
  h.page.setData({ username: 'FreshMember', password: 'AValidSecret#9', confirmPassword: 'AValidSecret#9' })
  const first = h.page.setupAccount(); await h.page.setupAccount()
  h.page.fieldInput(input('password', 'NotAllowed#9'))
  assert.equal(h.requests.length, 2)
  assert.equal(h.page.data.password, 'AValidSecret#9')
  pending.resolve({}); await first
})

test('短信固定用途8与本人手机号，倒计时/重复点击不重复发送，不显示测试验证码', async () => {
  const pending = deferred()
  const h = harness({ respond: ({ url }) => url === '/sms/send' ? pending.promise : member })
  await h.page.onShow()
  const first = h.page.sendCode(); await h.page.sendCode()
  assert.deepEqual(h.requests[1], { url: '/sms/send', method: 'POST', data: { phone: member.phone, bizType: 8 } })
  pending.resolve('123456'); await first
  assert.equal(h.page.data.countdown, 60)
  assert.ok(!h.page.data.message.includes('123456'))
  await h.page.sendCode(); assert.equal(h.requests.length, 2)
  h.advance(61000); h.page.updateCountdown()
  assert.equal(h.page.data.countdown, 0)
  assert.equal(h.timers.size, 0)
})

test('离开页面/卸载清空密码验证码与计时器，晚到资料不恢复旧用户', async () => {
  const pending = deferred()
  const h = harness({ respond: () => pending.promise })
  const loading = h.page.onShow()
  h.page.setData({ password: 'AValidSecret#9', currentPassword: 'OldCredential#1', newPassword: 'AValidSecret#9', confirmPassword: 'AValidSecret#9', smsCode: '123456' })
  h.page.onHide()
  for (const field of ['password', 'currentPassword', 'newPassword', 'confirmPassword', 'smsCode']) assert.equal(h.page.data[field], '')
  pending.resolve(member); await loading
  assert.equal(h.page.data.member, null)
  h.page.onUnload(); assert.equal(h.timers.size, 0)
})

test('密码控件隐藏显示，验证码与密码不置于路由/存储，不虚构找回或注销能力', () => {
  const code = readFileSync(new URL('../pages/account-security/index.js', import.meta.url), 'utf8')
  assert.doesNotMatch(code, /setStorageSync\([^\n]*(?:password|smsCode|EMPTY_SECRETS)|console\.|\/forgot-password|\/account\/delete/)
  assert.match(code, /setStorageSync\('mall_mini_member', member\)/)
  const view = readFileSync(new URL('../pages/account-security/index.wxml', import.meta.url), 'utf8')
  for (const field of ['password', 'currentPassword', 'newPassword', 'confirmPassword']) {
    const tags = (view.match(/<input[^>]+>/g) || []).filter((tag) => tag.includes(`data-field="${field}"`))
    assert.ok(tags.length)
    assert.ok(tags.every((tag) => /\spassword(?:\s|=)/.test(tag)))
  }
  assert.match(view, /open-type="contact"/)
  assert.match(view, /忘记当前密码/)
})
