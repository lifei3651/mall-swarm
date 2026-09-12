import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
const read = path => readFileSync(new URL('../'+path, import.meta.url), 'utf8')
const styles = read('pages/account-security/index.wxss')

test('账户辅助入口使用通栏、可收缩说明和不拆行操作，避免微信默认按钮半宽', () => {
  const rule = styles.match(/\.security-page \.support-link \{([^}]+)\}/)[1]
  for (const property of ['display:flex', 'width:100%', 'max-width:100%', 'min-width:0', 'margin:0', 'text-align:left', 'white-space:normal']) assert.ok(rule.includes(property), property)
  assert.match(styles, /\.support-copy \{[^}]*flex:1;[^}]*min-width:0;/)
  assert.match(styles, /\.support-action \{[^}]*flex:none;[^}]*white-space:nowrap;/)
  for (const file of ['account-settings', 'account-security']) {
    const buttons = read(`pages/${file}/index.wxml`).match(/<button\b[^>]*class="support-link"[^>]*>[\s\S]*?<\/button>/g)
    assert.ok(buttons.length)
    for (const button of buttons) {
      assert.match(button, /class="support-copy"/)
      assert.match(button, /class="support-action"/)
      assert.match(button, /chevron-right\.png/)
    }
  }
})

test('支付密码仅在未设商城账号时显示设置入口，已有账号不重复引导', () => {
  const view = read('pages/account-settings/index.wxml')
  const condition = view.match(/<button wx:if="\{\{([^}]+)\}\}" class="support-link" bindtap="loginPassword"/)[1]
  for (const [member, expected] of [[{},true],[{username:'',phone:'13800008000'},true],[{username:'13800008000',phone:'13800008000'},true],[{username:'PreviewOnly',phone:'13800008000'},false]]) {
    assert.equal(vm.runInNewContext(condition,{member}),expected)
  }
  assert.match(view, /bindtap="savePayment"[^>]*disabled="\{\{busy \|\| sending \|\| wallet.paymentPasswordLocked\}\}"/)
})

test('账户表单验证码行可收缩且同高，昵称双按钮不会继承100%宽度挤出', () => {
  assert.match(styles, /\.field \{[^}]*min-width: 0;[^}]*height: 88rpx;/)
  assert.match(styles, /\.code-input \{[^}]*min-width: 0;[^}]*width: 0;/)
  assert.match(styles, /\.security-page \.sms-button \{[^}]*min-height: 88rpx;/)
  assert.match(styles, /\.security-page \.nickname-actions button \{[^}]*width: 0;[^}]*min-width: 0;/)
  assert.match(read('pages/account-settings/index.wxss'), /\.identity-consent > text \{[^}]*min-width: 0;/)
})
