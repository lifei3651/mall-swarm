import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { auditSecondaryButtons } from '../../scripts/lib/storefront-button-contract.mjs'

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')

test('H5地址识别与小程序共用浅主题色辅助按钮层级', () => {
  const css = read('src/assets/styles.css')
  const address = read('src/views/AddressView.vue')
  assert.match(address, /class="ui-button--assist" type="button" @click="parseAddress"/)
  assert.match(css, /\.ui-button--assist\s*\{[^}]*background:\s*var\(--brand-primary-soft\) !important;[^}]*border:\s*1px solid var\(--brand-primary\) !important;[^}]*color:\s*var\(--brand-primary\) !important;/)
  assert.match(css, /\.ui-button--assist:disabled[^}]*\{[^}]*background:\s*#eef0f3 !important;[^}]*color:\s*#788292 !important;/)
  assert.match(address, /class="btn primary save-button"/)
  assert.doesNotMatch(address, /\.paste-box button\{[^}]*(?:color:|background:)/)
})

test('H5次级操作使用浅色底，订单详情只保留订单状态相关动作', () => {
  const globalCss = read('src/assets/styles.css')
  const orders = read('src/views/OrdersView.vue')
  const detail = read('src/views/OrderDetailView.vue')

  assert.match(globalCss, /\.btn\.secondary, \.ui-button--secondary, \.ui-button--assist\s*\{[^}]*background:\s*var\(--brand-primary-soft\)/)
  assert.match(orders, /class="order-action btn secondary ui-action-button ui-order-action"/)
  assert.match(orders, /class="order-action btn primary ui-action-button ui-action-button--primary ui-order-action ui-order-action--primary"/)
  assert.doesNotMatch(detail, /serviceTicketLink|>联系客服<|>客服工单</)
  assert.match(detail, /'申请售后'/)
  assert.match(detail, /'取消并退款'/)
})

test('H5商品与优惠券的独立次级操作接入共用颜色', () => {
  const product = read('src/views/ProductDetailView.vue')
  const coupons = read('src/views/CouponsView.vue')
  assert.match(product, /\.write-review-button[^}]*background:var\(--brand-primary-soft/)
  assert.match(product, /class="load-more ui-button--secondary"/)
  assert.match(coupons, /class="ui-button--secondary"[^>]*@click="turn\(-1\)"/)
})

test('H5消息加载更多与退出登录不再使用白色操作按钮', () => {
  const messages = read('src/views/MessageCenterView.vue')
  const profile = read('src/views/ProfileView.vue')
  const publicProfile = read('src/surfaces/public/PublicProfileView.vue')
  assert.match(messages, /class="more ui-button--secondary"/)
  assert.match(profile, /\.logout-button[^}]*color:#fff;[^}]*background:#c92d45/)
  assert.match(publicProfile, /\.logout-button[^}]*color:#fff;[^}]*background:#c92d45/)
})

test('H5查看全部、重试和更多入口统一检查，付款和危险操作不混入', () => {
  for (const name of ['HomeView', 'MessageCenterView', 'CouponsView', 'ProductDetailView', 'OrdersView', 'ServiceTicketsView']) {
    assert.deepEqual(auditSecondaryButtons(read(`src/views/${name}.vue`)), [], name)
  }
  assert.equal(auditSecondaryButtons('<button class="own-color">查看全部商品</button>').length, 1)
  assert.equal(auditSecondaryButtons('<button class="btn primary">加载更多</button>').length, 1)
  assert.deepEqual(auditSecondaryButtons('<button class="btn primary">确认收货</button><button class="btn danger">删除</button>'), [])
  const css = read('src/assets/styles.css')
  assert.doesNotMatch(css, /\.ui-order-action\.secondary\s*\{/)
  assert.match(css, /\.btn\.secondary:disabled, \.ui-button--secondary:disabled/)
})
