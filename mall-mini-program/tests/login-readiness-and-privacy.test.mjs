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
function harness(runtime) {
  let definition
  const calls = [], notices = []
  const mocks = {
    '../../utils/auth': { runtime, login: async (data) => { calls.push(data); return {} } },
    '../../utils/session': { getToken: () => '' },
    '../../utils/invite': { getPendingInvite: () => 'TESTINVITE' },
    '../../config/runtime': { PRIVACY_CONSENT_VERSION: version },
    '../../utils/theme': { pageData: () => ({}), apply() {} }
  }
  vm.runInNewContext(source('pages/login/index.js'), {
    Page(value) { definition = value }, require: (id) => { assert.ok(mocks[id], id); return mocks[id] },
    wx: { showToast: (value) => notices.push(value), navigateTo() {} }, setTimeout() {}
  })
  const page = { ...definition, data: { ...definition.data }, setData(patch) { Object.assign(this.data, patch) } }
  return { page, calls, notices }
}

test('协议移到操作区下方，未同意时两个入口都只提示而不登录或授权', async () => {
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
  const view = source('pages/login/index.wxml')
  assert.ok(view.indexOf('class="agreement ') > view.lastIndexOf('手机号快捷登录 / 注册</button>'))
  assert.match(view, /<button wx:if="\{\{agreed\}\}"[^>]*open-type="getPhoneNumber"/)
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
  const view = source('pages/login/index.wxml')
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
