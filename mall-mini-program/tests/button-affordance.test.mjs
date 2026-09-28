import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { auditSecondaryButtons } from '../../scripts/lib/storefront-button-contract.mjs'

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')

test('地址识别使用浅主题色辅助按钮，保留保存主操作与忙碌禁用态', () => {
  const css = read('app.wxss')
  const address = read('pages/address/index.wxml')
  assert.match(address, /class="secondary-button ui-button--assist" bindtap="recognizeAddress" disabled="\{\{saving \|\| importing\}\}"/)
  assert.match(css, /\.ui-button--assist\s*\{[^}]*background:\s*var\(--brand-soft\) !important;[^}]*color:\s*var\(--brand\) !important;[^}]*border:\s*1rpx solid var\(--brand\) !important;/)
  assert.match(css, /\.secondary-button\[disabled\][^{]*\{[^}]*background:\s*#eef0f3 !important;[^}]*color:\s*#788292 !important;/)
  assert.match(address, /class="primary-button"[^>]*bindtap="save"/)
})

test('原生次级操作使用可辨认底色，订单详情不再放重复返回和客服工单', () => {
  const appCss = read('app.wxss')
  const detail = read('pages/order-detail/index.wxml')
  const detailScript = read('pages/order-detail/index.js')

  assert.match(appCss, /\.secondary-button, \.ui-button--secondary, \.ui-button--assist\s*\{[^}]*background:\s*var\(--brand-soft\)/)
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

  for (const selector of ['quantity-button', 'type-button', 'cart-header-actions', 'review-heading', 'share-button', 'support-error button']) {
    assert.match(sources, new RegExp(`${selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}[^}]*background:(?:\\s*)?(?:#f1f3f6|var\\(--brand-soft\\))`))
  }
  assert.match(read('pages/coupons/index.wxml'), /class="ui-button--secondary" data-delta="-1"/)
  assert.match(read('pages/cart/index.wxml'), /class="ui-button--secondary" hover-class="none" bindtap="toggleManage"/)
})

test('消息加载更多与退出登录遵循统一操作层级', () => {
  const messages = read('pages/messages/index.wxml')
  const profile = read('pages/profile/index.wxss')
  assert.match(messages, /class="more ui-button--secondary"/)
  assert.match(profile, /\.profile-page \.logout[^}]*background:\s*#fde8ed/)
})

test('首页和独立分页入口必须接入共用色，门禁能发现新增漏接入口', () => {
  for (const file of ['home', 'category', 'messages', 'coupons', 'product', 'support']) {
    const source = read(`pages/${file}/index.wxml`)
    assert.deepEqual(auditSecondaryButtons(source), [], file)
  }
  const home = read('pages/home/index.wxml')
  assert.equal(auditSecondaryButtons(home.replace('secondary-button all-products-button', 'all-products-button')).length, 1)
  assert.equal(auditSecondaryButtons('<button>下一页</button>').length, 1)
  assert.deepEqual(auditSecondaryButtons('<button class="primary-button">付款</button><button class="danger-button">删除</button>'), [])
  assert.doesNotMatch(read('app.wxss'), /\.ui-order-action\.secondary-button\s*\{/)
  assert.match(read('app.wxss'), /\.ui-button--secondary\[disabled\]/)
})
