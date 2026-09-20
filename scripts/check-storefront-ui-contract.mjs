import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import process from 'node:process'

const root = resolve(import.meta.dirname, '..')
const read = (path) => readFileSync(join(root, path), 'utf8')
const failures = []
const requireText = (path, pattern, message) => {
  if (!pattern.test(read(path))) failures.push(`${path}: ${message}`)
}

const miniCore = read('mall-mini-program/app.wxss')
const webCore = read('mall-shop-web/src/assets/styles.css')
for (const token of ['--radius-card', '--radius-control', '--control-height', '--danger']) {
  if (!miniCore.includes(token)) failures.push(`mall-mini-program/app.wxss: 缺少共用变量 ${token}`)
}
for (const token of ['--shop-card-radius', '--shop-control-radius', '--shop-control-height', '--shop-danger']) {
  if (!webCore.includes(token)) failures.push(`mall-shop-web/src/assets/styles.css: 缺少共用变量 ${token}`)
}
for (const selector of ['.ui-card', '.ui-service-entry', '.ui-utility-button', '.ui-price', '.ui-status-pill', '.ui-action-bar']) {
  if (!miniCore.includes(selector)) failures.push(`mall-mini-program/app.wxss: 缺少共用组件 ${selector}`)
  if (!webCore.includes(selector)) failures.push(`mall-shop-web/src/assets/styles.css: 缺少共用组件 ${selector}`)
}

const sharedOrderSelectors = ['.ui-order-card', '.ui-order-header', '.ui-order-product', '.ui-order-logistics', '.ui-order-summary', '.ui-order-actions', '.ui-order-action']
for (const selector of sharedOrderSelectors) {
  if (!miniCore.includes(selector)) failures.push(`mall-mini-program/app.wxss: 缺少订单共用组件 ${selector}`)
  if (!webCore.includes(selector)) failures.push(`mall-shop-web/src/assets/styles.css: 缺少订单共用组件 ${selector}`)
}

const sharedServiceSurfaces = [
  'mall-mini-program/pages/legal/index.wxml',
  'mall-mini-program/pages/support/index.wxml',
  'mall-mini-program/pages/account-security/index.wxml',
  'mall-mini-program/pages/account-settings/index.wxml',
  'mall-mini-program/pages/wallet/index.wxml',
  'mall-mini-program/pages/profile/index.wxml',
  'mall-mini-program/components/login-sheet/index.wxml',
  'mall-shop-web/src/views/LegalView.vue',
  'mall-shop-web/src/views/ChangeLoginPasswordView.vue',
  'mall-shop-web/src/views/ProfileView.vue',
  'mall-shop-web/src/surfaces/public/PublicProfileView.vue',
]
for (const path of sharedServiceSurfaces) {
  requireText(path, /ui-service-entry/, '客服入口必须使用 ui-service-entry 共用组件')
}

const sharedPriceSurfaces = [
  'mall-mini-program/pages/home/index.wxml',
  'mall-mini-program/pages/category/index.wxml',
  'mall-mini-program/pages/cart/index.wxml',
  'mall-mini-program/pages/product/index.wxml',
  'mall-mini-program/pages/orders/index.wxml',
  'mall-mini-program/pages/order-detail/index.wxml',
  'mall-shop-web/src/views/HomeView.vue',
  'mall-shop-web/src/views/CategoryView.vue',
  'mall-shop-web/src/views/CartView.vue',
  'mall-shop-web/src/views/ProductDetailView.vue',
  'mall-shop-web/src/views/OrdersView.vue',
  'mall-shop-web/src/views/OrderDetailView.vue',
]
for (const path of sharedPriceSurfaces) {
  requireText(path, /ui-price/, '核心价格必须使用 ui-price 共用组件')
}

for (const path of ['mall-mini-program/pages/orders/index.wxml', 'mall-mini-program/pages/order-detail/index.wxml', 'mall-shop-web/src/views/OrdersView.vue', 'mall-shop-web/src/views/OrderDetailView.vue']) {
  const source = read(path)
  for (const selector of ['ui-card', 'ui-status-pill', 'ui-action-bar']) {
    if (!source.includes(selector)) failures.push(`${path}: 订单页面缺少 ${selector} 共用组件`)
  }
}

for (const path of ['mall-mini-program/pages/orders/index.wxml', 'mall-shop-web/src/views/OrdersView.vue']) {
  const source = read(path)
  for (const selector of sharedOrderSelectors) {
    if (!source.includes(selector.slice(1))) failures.push(`${path}: 订单列表缺少 ${selector} 共用组件`)
  }
}

for (const path of ['mall-mini-program/pages/order-detail/index.wxml', 'mall-shop-web/src/views/OrderDetailView.vue']) {
  requireText(path, /ui-utility-button/, '订单详情的复制、查询等辅助操作必须使用 ui-utility-button')
}

const walk = (dir, extensions, results = []) => {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    const stat = statSync(path)
    if (stat.isDirectory()) walk(path, extensions, results)
    else if (extensions.some((extension) => path.endsWith(extension))) results.push(path)
  }
  return results
}

// 共用组件的视觉属性只允许在入口样式中定义。页面可以改布局，但不能用 !important 抢回颜色、圆角或阴影。
const pageStyleFiles = [
  ...walk(join(root, 'mall-mini-program/pages'), ['.wxss']),
  ...walk(join(root, 'mall-mini-program/components'), ['.wxss']),
  ...walk(join(root, 'mall-shop-web/src/views'), ['.vue']),
  ...walk(join(root, 'mall-shop-web/src/surfaces'), ['.vue']),
]
for (const path of pageStyleFiles) {
  const source = readFileSync(path, 'utf8')
  for (const match of source.matchAll(/([^{}]*(?:ui-card|ui-service-entry|ui-utility-button|ui-price|ui-status-pill|ui-action-bar)[^{}]*)\{([^{}]*)\}/g)) {
    if (/!important/.test(match[2])) failures.push(`${relative(root, path)}: 共用组件页面覆盖禁止使用 !important（${match[1].trim()}）`)
  }
}

if (failures.length) {
  console.error(`商城界面共用规范检查失败（${failures.length}项）：`)
  for (const failure of failures) console.error(`- ${failure}`)
  process.exit(1)
}

console.log('商城界面共用规范检查通过：按钮、价格、卡片、状态与客服入口均由共用层管理。')
