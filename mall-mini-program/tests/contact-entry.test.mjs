import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { runMiniScript } from './helpers/run-mini-script.mjs'
const require = createRequire(import.meta.url)
const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8')
function harness(respond = async () => ({})) {
  let definition
  const routes = [], notices = [], phones = [], clips = []
  runMiniScript(read('pages/legal/index.js'), {
    Page(value) { definition = value },
    require(path) {
      if (path.endsWith('/request')) return respond
      if (path.endsWith('/theme')) return { pageData: () => ({}), apply() {} }
      if (path.endsWith('/format')) return { mediaUrl: value => value || '' }
      if (path.endsWith('/legal')) return require('../utils/legal.js')
      throw Error(path)
    },
    wx: { navigateTo: item => routes.push(item), showToast: item => notices.push(item.title), makePhoneCall: item => phones.push(item), setClipboardData: item => clips.push(item), setNavigationBarTitle() {} }
  })
  const page = { ...definition, data: structuredClone(definition.data), setData(patch) { Object.assign(this.data, patch) } }
  return { page, routes, notices, phones, clips }
}
test('客服入口不依赖微信会话成功，密码及我的入口有可执行导航与失败反馈', () => {
  for (const name of ['profile', 'account-security']) {
    const code = read(`pages/${name}/index.js`), view = read(`pages/${name}/index.wxml`)
    assert.match(view, /bindtap="contact"/)
    assert.match(code, /contact\(\) \{ wx.navigateTo\(\{ url: '\/pages\/legal\/index\?type=contact', fail:/)
  }
  const view = read('pages/legal/index.wxml')
  assert.ok(view.indexOf('class="card contact-actions"') < view.indexOf('wx:if="{{loading}}"'))
  for (const tag of view.match(/<button[^>]*open-type="contact"[^>]*>/g)) assert.match(tag, /binderror="contactError"/)
})
test('微信客服失败有明确替代入口，取消或离页不误报错误', async () => {
  const h = harness()
  await h.page.contactError({ detail: { errMsg: 'contact:fail unavailable' } })
  assert.match(h.page.data.contactError, /客服工单/)
  assert.equal(h.notices.length, 1)
  h.page.tickets(); assert.equal(h.routes[0].url, '/pages/support/index')
  h.page.contactStart(); assert.equal(h.page.data.contactError, '')
  h.page.contactError({ detail: { errMsg: 'contact:fail cancel' } })
  h.page.hidden = true; h.page.contactError({ detail: { errMsg: 'contact:fail' } })
  assert.equal(h.notices.length, 1)
})
test('客服电话邮箱只使用后台配置，缺失时不拨打不复制', async () => {
  const h = harness(async () => ({ servicePhone: ' 4000000000 ', serviceEmail: ' test@example.com ' }))
  h.page.callPhone(); h.page.copyEmail()
  assert.equal(h.phones.length + h.clips.length, 0)
  await h.page.load()
  h.page.callPhone(); h.page.copyEmail()
  assert.equal(h.phones[0].phoneNumber, '4000000000')
  assert.equal(h.clips[0].data, 'test@example.com')
  h.phones[0].fail({ errMsg: 'makePhoneCall:fail cancel' }); assert.equal(h.notices.length, 0)
  h.phones[0].fail({ errMsg: 'makePhoneCall:fail' }); assert.equal(h.notices.length, 1)
})
test('联系方式加载失败工单仍可进入，晚到响应不覆盖离页状态', async () => {
  const h = harness(async () => { throw Error('network unavailable') })
  await h.page.load(); h.page.tickets()
  assert.equal(h.page.data.loading, false)
  assert.equal(h.routes[0].url, '/pages/support/index')
  let resolve
  const late = harness(() => new Promise(done => { resolve = done }))
  const pending = late.page.load(); late.page.onHide(); resolve({ servicePhone: '4000000000' }); await pending
  assert.equal(late.page.data.config.servicePhone, undefined)
})
test('密码表单精简重复标题但保留当前密码、短信、确认与设备登出说明', () => {
  const view = read('pages/account-security/index.wxml')
  assert.doesNotMatch(view, /section-title">修改登录密码/)
  assert.match(view, /修改后需在所有设备重新登录/)
  for (const field of ['currentPassword', 'smsCode', 'newPassword', 'confirmPassword']) assert.match(view, new RegExp(`data-field="${field}"`))
  assert.match(view, /bindtap="changePassword"[^>]*>保存新密码/)
})
