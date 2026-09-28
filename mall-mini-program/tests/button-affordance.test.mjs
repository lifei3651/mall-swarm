import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')

test('地址识别使用浅主题色辅助按钮，保留保存主操作与忙碌禁用态', () => {
  const css = read('app.wxss')
  const address = read('pages/address/index.wxml')
  assert.match(address, /class="secondary-button ui-button--assist" bindtap="recognizeAddress" disabled="\{\{saving \|\| importing\}\}"/)
  assert.match(css, /\.ui-button--assist\s*\{[^}]*background:\s*var\(--brand-soft\) !important;[^}]*color:\s*var\(--brand\) !important;[^}]*border-color:\s*var\(--brand\) !important;/)
  assert.match(css, /\.secondary-button\[disabled\][^{]*\{[^}]*background:\s*#eef0f3 !important;[^}]*color:\s*#788292 !important;/)
  assert.match(address, /class="primary-button"[^>]*bindtap="save"/)
})

test('原生次级操作使用可辨认底色，订单详情不再放重复返回和客服工单', () => {
  const appCss = read('app.wxss')
  const detail = read('pages/order-detail/index.wxml')
  const detailScript = read('pages/order-detail/index.js')

  assert.match(appCss, /\.secondary-button, \.ui-button--secondary\s*\{[^}]*background:\s*#f1f3f6/)
  assert.doesNotMatch(appCss, /\.secondary-button\s*\{[^}]*background:\s*var\(--paper\)/)
  assert.doesNotMatch(detail, /客服工单|查看全部订单|bindtap="support"/)
  assert.doesNotMatch(detailScript, /support\(event\)/)
  assert.match(detail, />申请售后<|>去评价</)
  assert.match(detail, /class="shipment-company-action"[^>]*bindtap="openWeChatTracking"/)
  assert.doesNotMatch(detail, /物流配送中|>查看物流<\/button>/)
})

test('高频商城页的独立次级操作不再使用白色卡片底', () => {
  const sources = [
    read('pages/after-sale/index.wxss'),
    read('pages/cart/index.wxss'),
    read('pages/product/index.wxss'),
    read('pages/coupons/index.wxss'),
    read('pages/store-content/index.wxss'),
    read('styles/support.wxss'),
  ].join('\n')

  for (const selector of ['quantity-button', 'type-button', 'cart-header-actions', 'review-heading', 'pager button', 'share-button', 'support-error button']) {
    assert.match(sources, new RegExp(`${selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}[^}]*background:(?:\\s*)?(?:#f1f3f6|var\\(--brand-soft\\))`))
  }
})

test('消息加载更多与退出登录遵循统一操作层级', () => {
  const messages = read('pages/messages/index.wxss')
  const profile = read('pages/profile/index.wxss')
  assert.match(messages, /\.message-page \.more[^}]*background:#f1f3f6/)
  assert.match(profile, /\.profile-page \.logout[^}]*background:\s*#fde8ed/)
})
