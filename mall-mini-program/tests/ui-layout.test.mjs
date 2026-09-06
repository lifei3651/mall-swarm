import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = (file) => readFileSync(new URL(`../${file}`, import.meta.url), 'utf8')

test('全部已注册页面保留后台主题绑定，统一按钮支持换行和禁用态', () => {
  const app = JSON.parse(source('app.json'))
  for (const page of app.pages) {
    const view = page === 'pages/login/index' ? 'components/login-sheet/index.wxml' : `${page}.wxml`
    assert.match(source(view), /\{\{themeStyle\}\}/, page)
  }
  const css = source('app.wxss')
  assert.match(css, /\.primary-button, \.secondary-button\s*\{[^}]*min-height: 88rpx;[^}]*white-space: normal;/)
  assert.match(css, /\.primary-button\[disabled\], \.secondary-button\[disabled\]/)
})

test('地址字段有常驻标签，长地址和订单备注用多行输入且保留原提交绑定', () => {
  const address = source('pages/address/index.wxml')
  for (const label of ['收货人', '联系电话', '所在地区', '详细地址']) {
    assert.ok(address.includes(`<text class="field-label">${label}</text>`))
  }
  assert.match(address, /<textarea[^>]*data-field="detailAddress"[^>]*maxlength="200"[^>]*bindinput="input"/)
  assert.match(source('pages/checkout/index.wxml'), /<textarea[^>]*maxlength="500"[^>]*value="\{\{remark\}\}"[^>]*bindinput="remarkInput"/)
})

test('关键详情和表单页为安全区留白，订单多按钮允许换行', () => {
  for (const page of ['login', 'address', 'checkout', 'product', 'order-detail', 'payout', 'messages', 'subscriptions', 'account-security']) {
    const stylesheet = page === 'login' ? 'components/login-sheet/index.wxss' : `pages/${page}/index.wxss`
    assert.match(source(stylesheet), /env\(safe-area-inset-bottom\)/, page)
  }
  assert.match(source('pages/orders/index.wxss'), /\.order-actions\s*\{[^}]*flex-wrap: wrap;/)
})

test('提现金额用原生 text 组件，账号昵称主次操作位于输入框之后', () => {
  const payout = source('pages/payout/index.wxml')
  assert.doesNotMatch(payout, /<\/?strong/)
  assert.match(payout, /<text class="payout-amount">¥\{\{item.amountText\}\}<\/text>/)
  const account = source('pages/account-security/index.wxml')
  assert.ok(account.indexOf('name="nickname"') < account.indexOf('bindtap="enableWechatNickname"'))
  assert.match(account, /form-type="submit"[^>]*>保存昵称/)
})
