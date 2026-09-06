import { runMiniScript } from './helpers/run-mini-script.mjs'
import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'

const source = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')
const ready = { enabled: true, phoneAuthorizationEnabled: true, privacyConsentVersion: 'MINI_PROGRAM_PRIVACY_V1' }
function harness({ runtime = async () => ready, login, token = '' } = {}) {
  const events = [], calls = [], routes = []
  const session = { getToken: () => token }
  const deps = {
    './auth': { runtime, login: async (data) => { calls.push(data); const result = login ? await login(data) : { accessToken: 'test-session' }; if (result.accessToken) token = result.accessToken; return result } },
    './session': session, './login-invitation': { data: { inviteReady: true }, methods: {
      syncInvitation() { this.setData({ inviteCode: 'ABCD1234' }) }, invitationReady: () => true } },
    '../config/runtime': { PRIVACY_CONSENT_VERSION: ready.privacyConsentVersion },
    './theme': { pageData: () => ({}), apply() {} }
  }
  const context = { module: { exports: {} }, require: (id) => { assert.ok(deps[id], id); return deps[id] },
    wx: { showToast() {}, navigateTo: (value) => routes.push(value) } }
  runMiniScript(source('utils/login-flow.js'), context)
  let component
  runMiniScript(source('components/login-sheet/index.js'), {
    require: (id) => id === '../../utils/login-flow' ? context.module.exports : session,
    Component: (value) => { component = value }
  })
  const panel = { ...component.methods, data: { ...component.data }, properties: { presentation: 'sheet' },
    setData(patch) { Object.assign(this.data, patch) }, triggerEvent(name, detail) { events.push({ name, detail }) } }
  return { panel, component, events, calls, routes }
}

test('登录弹窗只在主动打开时读取配置，默认未同意且带上原始目标和扫码邀请', async () => {
  let checks = 0
  const h = harness({ runtime: async () => { checks++; return ready } })
  assert.equal(checks, 0)
  await h.panel.open('/pages/orders/index?tab=pending-payment')
  assert.equal(h.panel.data.visible, true)
  assert.equal(h.panel.data.agreed, false)
  assert.equal(h.panel.data.contextHint, '查看待支付订单')
  assert.equal(h.panel.data.inviteCode, 'ABCD1234')
  assert.equal(checks, 1)
  assert.equal(h.calls.length, 0)
})

test('关闭弹窗不导航不授权，迟到的配置和手机号事件不能重新开启登录', async () => {
  let resolve
  const h = harness({ runtime: () => new Promise((done) => { resolve = done }) })
  const pending = h.panel.open('/pages/address/index')
  h.panel.close()
  resolve(ready); await pending
  await h.panel.phoneLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'late-code' } })
  assert.equal(h.panel.data.visible, false)
  assert.equal(h.panel.data.enabled, false)
  assert.equal(h.panel.data.agreed, false)
  assert.equal(h.calls.length, 0)
  assert.equal(h.routes.length, 0)
  assert.deepEqual(h.events.map((item) => item.name), ['close'])
})

test('未同意不发请求，授权成功只通知原任务且不暴露会话；重新打开需要重新同意', async () => {
  const h = harness()
  await h.panel.open('/pages/address/index')
  await h.panel.phoneLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'before-consent' } })
  assert.equal(h.calls.length, 0)
  h.panel.agreementChange({ detail: { value: ['agreed'] } })
  await h.panel.phoneLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'approved-code' } })
  assert.equal(h.calls.length, 1)
  assert.equal(h.calls[0].privacyConsentVersion, ready.privacyConsentVersion)
  assert.equal(h.panel.data.visible, false)
  assert.deepEqual(JSON.parse(JSON.stringify(h.events)), [{ name: 'success', detail: { redirect: '/pages/address/index' } }])
  await h.panel.open('')
  assert.equal(h.panel.data.agreed, false)
})

test('提交中不能重复提交或关闭；没有真实会话的响应不报告成功', async () => {
  let resolve
  const h = harness({ login: () => new Promise((done) => { resolve = done }) })
  await h.panel.open(''); h.panel.data.agreed = true
  const pending = h.panel.phoneLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'test-code' } })
  h.panel.close()
  await h.panel.phoneLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'double-code' } })
  assert.equal(h.panel.data.visible, true)
  assert.equal(h.calls.length, 1)
  resolve({}); await pending
  assert.equal(h.events.length, 0)
  assert.match(h.panel.data.error, /登录.*重试/)
  const previous = harness({ token: 'previous-session', login: async () => ({}) })
  await previous.panel.open(''); previous.panel.data.agreed = true
  await previous.panel.returningLogin()
  assert.equal(previous.events.length, 0, '旧会话仍存在也不能把本次不完整登录响应当成成功')
})

test('手机号拒绝留在弹窗内，可重试；协议跳转不代用户勾选', async () => {
  const h = harness()
  await h.panel.open(''); h.panel.data.agreed = true
  await h.panel.phoneLogin({ detail: { errMsg: 'getPhoneNumber:fail user deny' } })
  assert.equal(h.panel.data.visible, true)
  assert.match(h.panel.data.loginNotice, /取消/)
  h.panel.data.agreed = false
  h.panel.openPrivacy(); h.panel.openAgreement()
  assert.equal(h.panel.data.agreed, false)
  assert.deepEqual(h.routes.map((item) => item.url), ['/pages/legal/index?type=privacy', '/pages/legal/index?type=agreement'])
})

test('已关联老账号在拒绝手机号后仍能主动走原微信登录，不额外提交手机号凭证', async () => {
  const h = harness()
  await h.panel.open(''); h.panel.data.agreed = true
  await h.panel.phoneLogin({ detail: { errMsg: 'getPhoneNumber:fail user deny' } })
  assert.equal(h.panel.data.showLoginHelp, true)
  assert.equal(h.calls.length, 0)
  await h.panel.returningLogin()
  assert.equal(h.calls.length, 1)
  assert.equal(h.calls[0].phoneCode, '')
  assert.equal(h.events[0].name, 'success')
})

test('原登录路由复用同一表单，保留结算深链且关闭可返回原页', () => {
  const opened = [], completed = [], routes = []
  let page
  const flow = { finish() { completed.push(this.redirect) } }
  runMiniScript(source('pages/login/index.js'), {
    Page: (value) => { page = value }, require: () => flow,
    wx: { navigateBack: (options) => routes.push(options), switchTab: (options) => routes.push(options) }
  })
  page.selectComponent = () => ({ open: (redirect) => opened.push(redirect) })
  const target = '/pages/checkout/index?source=buy-now'
  page.onLoad({ redirect: encodeURIComponent(target) }); page.onReady()
  assert.deepEqual(opened, [target])
  page.authorized({ detail: { redirect: target } })
  assert.deepEqual(completed, [target])
  page.close(); assert.equal(typeof routes[0].fail, 'function')
  routes[0].fail(); assert.equal(routes[1].url, '/pages/profile/index')
  page.onLoad({ redirect: '%broken' }); page.onReady()
  assert.equal(opened[1], '')
})

test('弹窗卸载后迟到登录不发成功事件，协议页返回也不擅自勾选', async () => {
  let resolve
  const h = harness({ login: () => new Promise((done) => { resolve = done }) })
  await h.panel.open('')
  h.component.pageLifetimes.show.call(h.panel)
  assert.equal(h.panel.data.agreed, false)
  h.panel.data.agreed = true
  const pending = h.panel.phoneLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'test-code' } })
  h.component.lifetimes.detached.call(h.panel)
  resolve({ accessToken: 'late-session' }); await pending
  assert.equal(h.events.length, 0)
})

test('个人中心采用整行账号入口和独立隐私组件，弹窗的单一主动作在协议上方', () => {
  const view = source('pages/profile/index.wxml')
  assert.match(view, /<button[^>]*class="profile-header"[^>]*bindtap="accountEntry"/)
  assert.doesNotMatch(view, /login-small|还未登录|欢迎来到商城/)
  assert.match(view, /商城账号登录/)
  assert.match(view, /<login-sheet id="login-sheet"/)
  assert.match(view, /<privacy-consent id="privacy-consent"/)
  const panel = source('components/login-sheet/index.wxml')
  assert.ok(panel.indexOf('class="agreement ') > panel.indexOf('open-type="getPhoneNumber"'))
  assert.match(panel, /wx:if="\{\{agreed && inviteReady && !inviteBusy && !inviteConflict\}\}"[^>]*open-type="getPhoneNumber"/)
  assert.match(panel, /wx:else[^>]*bindtap="requireAgreement"/)
  assert.match(panel, /role="dialog"/)
  assert.doesNotMatch(panel, /中国移动|登录其他账号|首次使用.*注册/)
  assert.match(source('custom-tab-bar/index.wxml'), /wx:if="\{\{!hidden\}\}"/)
  assert.match(source('components/login-sheet/index.wxss'), /\.login-panel \.login-button\s*\{[^}]*width: 100%/)
  assert.match(source('components/login-sheet/index.wxss'), /\.login-panel \.login-button\.is-disabled\s*\{[^}]*background: #eef0f3/)
  assert.doesNotMatch(source('components/login-sheet/index.wxss'), /\[disabled\]/)
  assert.match(source('components/login-sheet/index.wxml'), /class="login-button[^"\n]*is-disabled/)
})
