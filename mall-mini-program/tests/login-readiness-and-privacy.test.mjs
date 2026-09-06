import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const legal = require('../utils/legal')
const source = (file) => readFileSync(new URL(`../${file}`, import.meta.url), 'utf8')
const version = 'MINI_PROGRAM_PRIVACY_V1'
const ready = { enabled: true, phoneAuthorizationEnabled: true, privacyConsentVersion: version }
function harness(runtime, { token = '', response = { accessToken: 'test-session' } } = {}) {
  const module = { exports: {} }
  const calls = [], notices = [], navigations = []
  const mocks = {
    './auth': { runtime, login: async (data) => { calls.push(data); if (response.accessToken) token = response.accessToken; return response } },
    './session': { getToken: () => token },
    './login-invitation': { data: { inviteReady: true }, methods: { syncInvitation() {}, invitationReady: () => true } },
    '../config/runtime': { PRIVACY_CONSENT_VERSION: version },
    './theme': { pageData: () => ({}), apply() {} }
  }
  vm.runInNewContext(source('utils/login-flow.js'), {
    module, require: (id) => { assert.ok(mocks[id], id); return mocks[id] },
    wx: { showToast: (value) => notices.push(value), navigateTo: (value) => navigations.push(value),
      redirectTo: (value) => navigations.push(value), switchTab: (value) => navigations.push(value) }, setTimeout() {}
  })
  const page = { ...module.exports, data: { ...module.exports.data }, setData(patch) { Object.assign(this.data, patch) } }
  return { page, calls, notices, navigations }
}

test('用户取消或拒绝手机号授权是可恢复提示，不误报首次注册或发登录请求', async () => {
  for (const errMsg of ['getPhoneNumber:fail user deny', 'getPhoneNumber:fail cancel', 'getPhoneNumber:fail auth deny']) {
    const { page, calls } = harness(async () => ready)
    await page.loadRuntime()
    page.data.agreed = true
    await page.phoneLogin({ detail: { errMsg } })
    assert.equal(calls.length, 0)
    assert.equal(page.data.error, '')
    assert.match(page.data.loginNotice, /取消.*重新选择/)
    assert.equal(page.data.showLoginHelp, true, '取消手机号后仍可主动使用已关联的微信账号')
    assert.doesNotMatch(page.data.loginNotice, /首次注册/)
    assert.equal(page.data.submitting, false)
  }
})

test('手机号能力不足与不支持只解释不可用，不推断资质原因或输出原始错误', async () => {
  for (const errMsg of ['getPhoneNumber:fail no permission', 'getPhoneNumber:fail api is unauthorized', 'getPhoneNumber:fail not support']) {
    const { page, calls } = harness(async () => ready)
    await page.loadRuntime()
    page.data.agreed = true
    await page.phoneLogin({ detail: { errMsg: `${errMsg} sensitive-detail`, code: 'not-valid' } })
    assert.equal(calls.length, 0)
    assert.match(page.data.error, /暂不可用/)
    assert.equal(page.data.showLoginHelp, true)
    assert.doesNotMatch(page.data.error, /sensitive-detail|首次注册|认证失败/)
  }
})

test('手机号网络失败和返回凭证缺失分别提示，不因ok字符串或异常事件误登录', async () => {
  for (const event of [undefined, {}, { detail: {} }, { detail: { errMsg: 'getPhoneNumber:ok' } },
    { detail: { errMsg: 'getPhoneNumber:ok', code: ' ' } }, { detail: { errMsg: 'getPhoneNumber:ok', code: {} } }]) {
    const { page, calls } = harness(async () => ready)
    await page.loadRuntime(); page.data.agreed = true
    await page.phoneLogin(event)
    assert.equal(calls.length, 0)
    assert.match(page.data.error, /未收到有效.*重新/)
    assert.equal(page.data.showLoginHelp, true)
  }
  const { page, calls } = harness(async () => ready)
  await page.loadRuntime(); page.data.agreed = true
  await page.phoneLogin({ detail: { errMsg: 'getPhoneNumber:fail network timeout' } })
  assert.match(page.data.error, /网络.*重试/)
  assert.equal(calls.length, 0)
})

test('首次微信关联反馈不把已有商城账号叫新注册，拒绝后成功重试清除旧提示', async () => {
  const { page, calls } = harness(async () => ready, { response: { phoneAuthorizationRequired: true } })
  await page.loadRuntime(); page.data.agreed = true
  await page.returningLogin()
  assert.match(page.data.loginNotice, /手机号.*继续登录或注册/)
  assert.doesNotMatch(page.data.loginNotice, /首次注册/)
  const retry = harness(async () => ready)
  await retry.page.loadRuntime(); retry.page.data.agreed = true
  await retry.page.phoneLogin({ detail: { errMsg: 'getPhoneNumber:fail user deny' } })
  await retry.page.phoneLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'valid-code' } })
  assert.equal(retry.page.data.loginNotice, '')
  assert.equal(retry.page.data.error, '')
  assert.equal(retry.calls.length, 1)
  assert.equal(calls.length, 1)
})

test('登录页持有会话后回到原任务，无会话不跳受保护页；四个Tab使用原生切换', () => {
  const protectedTargets = ['/pages/orders/index?tab=pending-payment', '/pages/address/index', '/pages/messages/index']
  for (const url of protectedTargets) {
    const { page, navigations } = harness(async () => ready, { token: 'test-session' })
    page.redirect = url; page.finish()
    assert.equal(navigations[0].url, url)
  }
  const guest = harness(async () => ready)
  guest.page.redirect = protectedTargets[0]; guest.page.finish()
  assert.equal(guest.navigations.length, 0)
  const logged = harness(async () => ready, { token: 'test-session' })
  logged.page.redirect = '/pages/cart/index?ignored=1'; logged.page.finish()
  assert.equal(logged.navigations[0].url, '/pages/cart/index')
})

test('登录上下文只使用受控任务名称，不把任意来源参数显示给用户', async () => {
  const cases = [
    ['/pages/orders/index?tab=pending-payment', '查看待支付订单'],
    ['/pages/orders/index?tab=after-sale', '查看退款与售后'],
    ['/pages/address/index', '管理收货地址'],
    ['/pages/orders/index?tab=unknown-secret', '查看我的订单'],
    ['/pages/orders/index?tab=constructor', '查看我的订单'],
    ['/pages/orders/index?tab=__proto__', '查看我的订单'], ['toString', ''],
    ['https://example.com/private?token=should-not-render', ''],
    ['/pages/unknown/index?token=should-not-render', ''], ['', '']
  ]
  for (const [target, hint] of cases) {
    const { page } = harness(async () => ready)
    page.onLoad({ redirect: encodeURIComponent(target) })
    assert.equal(page.data.contextHint, hint)
    await Promise.resolve()
  }
  const { page } = harness(async () => ready)
  page.onLoad({ redirect: '%' })
  assert.equal(page.data.contextHint, '')
})

test('未知手机号失败保留安全的通用恢复提示，不回显微信原始返回', async () => {
  const { page, calls } = harness(async () => ready)
  await page.loadRuntime(); page.data.agreed = true
  await page.phoneLogin({ detail: { errMsg: 'getPhoneNumber:fail unrecognized private-payload' } })
  assert.equal(calls.length, 0)
  assert.match(page.data.error, /授权未完成.*客服/)
  assert.doesNotMatch(page.data.error, /private-payload/)
  assert.equal(page.data.showLoginHelp, true)
})

test('协议移到操作区下方，未同意时统一入口只提示而不登录或授权', async () => {
  const { page, calls, notices } = harness(async () => ready)
  await page.loadRuntime()
  assert.equal(page.data.agreed, false)
  await page.returningLogin()
  page.requireAgreement()
  await page.phoneLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'ignored-before-consent' } })
  assert.equal(calls.length, 0)
  assert.equal(page.data.agreed, false)
  assert.equal(page.data.agreementRequired, true)
  assert.ok(notices.every((item) => /请先阅读/.test(item.title)))
  const view = source('components/login-sheet/index.wxml')
  assert.ok(view.indexOf('class="agreement ') > view.lastIndexOf('手机号快捷登录</button>'))
  assert.match(view, /<button wx:if="\{\{agreed && inviteReady && !inviteBusy && !inviteConflict\}\}"[^>]*open-type="getPhoneNumber"/)
  assert.match(view, /<button wx:else[^>]*bindtap="requireAgreement"/)
  assert.match(view, /checked="\{\{agreed\}\}"/)
})

test('勾选只清除提示不自动登录，取消勾选后重新拦截授权', async () => {
  const { page, calls } = harness(async () => ready)
  await page.loadRuntime()
  page.requireAgreement()
  page.agreementChange({ detail: { value: ['agreed'] } })
  assert.equal(page.data.agreementRequired, false)
  assert.equal(calls.length, 0)
  await page.phoneLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'approved-phone-code' } })
  assert.equal(calls.length, 1)
  assert.equal(calls[0].phoneCode, 'approved-phone-code')
  assert.equal(calls[0].privacyConsentVersion, version)
  page.agreementChange({ detail: { value: [] } })
  await page.returningLogin()
  assert.equal(calls.length, 1)
  assert.equal(page.data.agreed, false)
  assert.equal(page.data.agreementRequired, true)
})

test('登录服务未就绪时准确提示服务状态，不误报只关闭了注册', async () => {
  const { page } = harness(async () => ({ ...ready, enabled: false }))
  await page.loadRuntime()
  assert.equal(page.data.enabled, false)
  assert.equal(page.data.phoneEnabled, false)
  assert.match(page.data.error, /登录服务暂未就绪/)
  const view = source('components/login-sheet/index.wxml')
  assert.doesNotMatch(view, /首次使用注册功能暂未开放/)
  assert.match(view, /bindtap="loadRuntime"/)
})

test('配置刷新失败必须禁用旧登录能力，恢复后允许重试且不自动授权', async () => {
  let offline = false
  const { page, calls } = harness(async () => { if (offline) throw Error('网络中断'); return ready })
  await page.loadRuntime()
  assert.equal(page.data.enabled, true)
  offline = true
  await page.loadRuntime()
  assert.equal(page.data.enabled, false)
  assert.equal(page.data.phoneEnabled, false)
  assert.match(page.data.error, /网络中断/)
  offline = false
  await page.loadRuntime()
  assert.equal(page.data.error, '')
  assert.equal(page.data.enabled, true)
  assert.equal(page.data.agreed, false)
  assert.equal(calls.length, 0)
})

test('较早的配置响应不能覆盖较新的关闭状态', async () => {
  let resolveOld, count = 0
  const { page } = harness(() => ++count === 1 ? new Promise((resolve) => { resolveOld = resolve }) : Promise.resolve({ ...ready, enabled: false }))
  const old = page.loadRuntime()
  await page.loadRuntime()
  resolveOld(ready)
  await old
  assert.equal(page.data.enabled, false)
  assert.equal(page.data.phoneEnabled, false)
})

test('配置只接受真布尔值，手机号授权不能绕过登录能力门禁', async () => {
  for (const runtime of [{ ...ready, enabled: 'false' }, { ...ready, enabled: null }, null]) {
    const { page } = harness(async () => runtime)
    await page.loadRuntime()
    assert.equal(page.data.enabled, false)
    assert.equal(page.data.phoneEnabled, false)
  }
  const { page } = harness(async () => ({ ...ready, phoneAuthorizationEnabled: 'true' }))
  await page.loadRuntime()
  assert.equal(page.data.enabled, true)
  assert.equal(page.data.phoneEnabled, false)
})

test('隐私版本不匹配保持禁用且移除旧同意，返回页面会重新读取配置', async () => {
  let count = 0
  const { page } = harness(async () => { count++; return { ...ready, privacyConsentVersion: 'OTHER' } })
  page.data.agreed = true
  await page.loadRuntime()
  assert.equal(page.data.enabled, false)
  assert.equal(page.data.agreed, false)
  assert.match(page.data.error, /隐私版本/)
  page.onShow()
  await new Promise((resolve) => setImmediate(resolve))
  assert.equal(count, 2)
})

test('禁用手机号授权时即使收到事件也不提交注册请求', async () => {
  const { page, calls } = harness(async () => ({ ...ready, phoneAuthorizationEnabled: false }))
  await page.loadRuntime()
  page.data.agreed = true
  await page.phoneLogin({ detail: { errMsg: 'getPhoneNumber:ok', code: 'test-code' } })
  assert.equal(calls.length, 0)
})

test('能力就绪时从微信授权界面返回不重新禁用按钮；页面卸载丢弃迟到配置', async () => {
  let count = 0
  const { page } = harness(async () => { count++; return ready })
  await page.loadRuntime()
  page.onShow()
  assert.equal(count, 1)
  assert.equal(page.data.enabled, true)
  let finish
  const h = harness(() => new Promise((resolve) => { finish = resolve }))
  const pending = h.page.loadRuntime()
  h.page.onUnload()
  finish(ready)
  await pending
  assert.equal(h.page.data.enabled, false)
})

test('隐私组件按钮只使用类选择器，保持原生同意与拒绝绑定', () => {
  const css = source('components/privacy-consent/index.wxss')
  const view = source('components/privacy-consent/index.wxml')
  assert.doesNotMatch(css, /(?:^|[\s},])button(?:::after|[\s{])/)
  assert.match(css, /\.privacy-action\s*\{/)
  assert.match(css, /\.privacy-action::after/)
  assert.match(view, /class="privacy-action privacy-decline" bindtap="decline"/)
  assert.match(view, /open-type="agreePrivacyAuthorization" bindagreeprivacyauthorization="agree"/)
  assert.doesNotMatch(view, /bindtap="agree"/)
})

test('缺失联系方式显示未配置，不伪造客服号码或覆盖客户自定义政策', () => {
  assert.equal(legal.resolveText('电话：{{servicePhone}} 邮箱：{{serviceEmail}}'), '电话：暂未配置 邮箱：暂未配置')
  assert.equal(legal.resolveText('{phone} {email}', { servicePhone: '  ', serviceEmail: '\n' }), '暂未配置 暂未配置')
  const custom = '客户自定义隐私条款，浏览器本地存储。'
  assert.equal(legal.content('privacy', { privacyPolicy: custom }), custom)
  assert.equal(legal.resolveText('{{servicePhone}}', { servicePhone: ' 400-000-0000 ' }), '400-000-0000')
  assert.match(legal.miniPrivacy, /小程序本地缓存/)
  assert.match(legal.miniPrivacy, /换绑手机号.*联系客服/)
  assert.match(source('pages/legal/index.wxml'), /商城通用隐私政策/)
})
