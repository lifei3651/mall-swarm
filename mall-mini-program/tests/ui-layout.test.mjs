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
  assert.match(css, /--control-height:\s*88rpx;/)
  assert.match(css, /\.primary-button, \.secondary-button, \.danger-button, \.ui-button\s*\{[^}]*min-height: var\(--control-height\);[^}]*white-space: normal;/)
  assert.match(css, /\.primary-button\[disabled\], \.secondary-button\[disabled\], \.danger-button\[disabled\], \.ui-button\[disabled\]/)
})

test('地址字段有常驻标签，长地址和订单备注用多行输入且保留原提交绑定', () => {
  const address = source('pages/address/index.wxml')
  for (const label of ['收货人', '联系电话', '所在地区', '详细地址']) {
    assert.ok(address.includes(`<text class="field-label">${label}</text>`))
  }
  assert.match(address, /<textarea[^>]*data-field="detailAddress"[^>]*maxlength="200"[^>]*bindinput="input"/)
  assert.match(source('pages/checkout/index.wxml'), /<textarea[^>]*maxlength="500"[^>]*value="\{\{remarkDraft\}\}"[^>]*bindinput="remarkInput"/)
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

test('提现资格提示只保留统一主操作，不显示无效客服或手动刷新按钮', () => {
  const withdraw = source('pages/withdraw/index.wxml')
  const requirement = withdraw.match(/<view wx:if="\{\{blockReason\}\}" class="card requirement">([\s\S]*?)<\/view>/)?.[1] || ''
  assert.match(requirement, /class="primary-button" bindtap="security">前往支付安全<\/button>/)
  assert.doesNotMatch(requirement, /已完成，刷新状态|open-type="contact"|联系商城客服|secondary-button/)
  assert.equal((requirement.match(/<button\b/g) || []).length, 1)
})

test('首页和分类已售罄提示覆盖图片居中显示并提高字号', () => {
  const home = source('pages/home/index.wxss')
  const category = source('pages/category/index.wxss')
  assert.match(home, /\.sold-out-mask\s*\{[^}]*inset: 0;[^}]*align-items: center;[^}]*justify-content: center;[^}]*font-size: 28rpx;/)
  assert.match(category, /\.sold-out-label\s*\{[^}]*inset: 0;[^}]*align-items: center;[^}]*justify-content: center;[^}]*font-size: 28rpx;/)
  assert.doesNotMatch(category, /\.sold-out-label\s*\{[^}]*bottom: 0;/)
})

test('分类列表结束提示紧跟商品收口，不复用大间距空状态', () => {
  const view = source('pages/category/index.wxml')
  const category = source('pages/category/index.wxss')
  assert.match(view, /class="category-list-end">已显示全部商品 · 共 \{\{total\}\} 件/)
  assert.doesNotMatch(view, /class="empty">已显示全部商品/)
  assert.match(category, /\.category-list-end\s*\{[^}]*padding: 24rpx 18rpx 32rpx;[^}]*text-align: center;/)
  const rule = category.match(/\.category-list-end\s*\{([^}]*)\}/)?.[1] || ''
  assert.doesNotMatch(rule, /(?:^|;)\s*(?:min-height|height|flex)\s*:/)
})

test('首页和分类左栏的长分类名称最多显示两行且不撑乱布局', () => {
  const home = source('pages/home/index.wxss')
  const categoryView = source('pages/category/index.wxml')
  const category = source('pages/category/index.wxss')
  assert.match(home, /\.category-item > text\s*\{[^}]*min-height: 64rpx;[^}]*-webkit-line-clamp: 2;/)
  assert.match(categoryView, /class="side-item-label">\{\{item\.categoryName\}\}<\/text>/)
  assert.match(category, /\.side-item\s*\{[^}]*height: 112rpx;[^}]*align-items: center;[^}]*justify-content: center;/)
  assert.match(category, /\.side-item-label\s*\{[^}]*-webkit-line-clamp: 2;[^}]*overflow-wrap: anywhere;/)
})
