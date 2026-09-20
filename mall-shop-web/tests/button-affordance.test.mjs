import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')

test('H5次级操作使用浅色底，订单详情只保留订单状态相关动作', () => {
  const globalCss = read('src/assets/styles.css')
  const orders = read('src/views/OrdersView.vue')
  const detail = read('src/views/OrderDetailView.vue')

  assert.match(globalCss, /\.btn\.secondary\s*\{[^}]*background:\s*#f1f3f6/)
  assert.match(orders, /class="order-action btn secondary"/)
  assert.match(orders, /class="order-action btn primary"/)
  assert.doesNotMatch(detail, /serviceTicketLink|>联系客服<|>客服工单</)
  assert.match(detail, />申请售后</)
})

test('H5商品与优惠券的独立次级操作不再使用纯白按钮底', () => {
  const product = read('src/views/ProductDetailView.vue')
  const coupons = read('src/views/CouponsView.vue')
  assert.match(product, /\.write-review-button[^}]*background:var\(--brand-primary-soft/)
  assert.match(product, /\.load-more[^}]*background:#f1f3f6/)
  assert.match(coupons, /\.coupons-page \.coupon-pager button[^}]*background:#f1f3f6/)
})

test('H5消息加载更多与退出登录不再使用白色操作按钮', () => {
  const messages = read('src/views/MessageCenterView.vue')
  const profile = read('src/views/ProfileView.vue')
  const publicProfile = read('src/surfaces/public/PublicProfileView.vue')
  assert.match(messages, /\.more\{[^}]*background:#f1f3f6/)
  assert.match(profile, /\.logout-button[^}]*color:#fff;[^}]*background:#c92d45/)
  assert.match(publicProfile, /\.logout-button[^}]*color:#fff;[^}]*background:#c92d45/)
})
