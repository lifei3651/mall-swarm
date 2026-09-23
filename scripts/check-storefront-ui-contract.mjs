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
for (const token of ['--radius-card', '--radius-control', '--control-height', '--action-height', '--action-font-size', '--action-padding-x', '--danger']) {
  if (!miniCore.includes(token)) failures.push(`mall-mini-program/app.wxss: 缺少共用变量 ${token}`)
}
for (const token of ['--shop-card-radius', '--shop-control-radius', '--shop-control-height', '--shop-action-height', '--shop-action-font-size', '--shop-action-padding-x', '--shop-danger']) {
  if (!webCore.includes(token)) failures.push(`mall-shop-web/src/assets/styles.css: 缺少共用变量 ${token}`)
}
for (const selector of ['.ui-card', '.ui-service-entry', '.ui-utility-button', '.ui-action-button', '.ui-copy-action', '.ui-price', '.ui-status-pill', '.ui-action-bar', '.ui-action-group']) {
  if (!miniCore.includes(selector)) failures.push(`mall-mini-program/app.wxss: 缺少共用组件 ${selector}`)
  if (!webCore.includes(selector)) failures.push(`mall-shop-web/src/assets/styles.css: 缺少共用组件 ${selector}`)
}

requireText('mall-mini-program/app.wxss', /--action-height:\s*56rpx;/, '普通操作按钮高度必须固定为56rpx')
requireText('mall-mini-program/app.wxss', /--action-font-size:\s*26rpx;/, '普通操作按钮字号必须固定为26rpx')
requireText('mall-mini-program/app.wxss', /--action-padding-x:\s*24rpx;/, '普通操作按钮左右内边距必须固定为24rpx')
requireText('mall-mini-program/app.wxss', /\.ui-action-button, \.ui-order-action\s*\{[^}]*flex:\s*none;[^}]*width:\s*auto\s*!important;[^}]*height:\s*var\(--action-height\)\s*!important;/, '普通操作按钮必须按文字收宽，禁止均分或固定大宽度')
requireText('mall-mini-program/app.wxss', /\.ui-action-button, \.ui-order-action\s*\{[^}]*margin-left:\s*0\s*!important;[^}]*margin-right:\s*0\s*!important;/, '小程序普通操作按钮必须覆盖原生自动左右外边距，避免单按钮居中或多按钮分散')
requireText('mall-mini-program/app.wxss', /\.ui-action-bar, \.ui-action-group\s*\{[^}]*flex-wrap:\s*nowrap;[^}]*justify-content:\s*flex-end;/, '普通操作组必须单行右对齐')
requireText('mall-mini-program/app.wxss', /\.ui-copy-action\s*\{[^}]*background:\s*transparent\s*!important;[^}]*font-size:\s*22rpx\s*!important;/, '复制必须使用小型文字按钮')

requireText('mall-shop-web/src/assets/styles.css', /--shop-action-height:\s*28px;/, 'H5普通操作按钮高度必须与小程序56rpx等比')
requireText('mall-shop-web/src/assets/styles.css', /--shop-action-font-size:\s*13px;/, 'H5普通操作按钮字号必须与小程序26rpx等比')
requireText('mall-shop-web/src/assets/styles.css', /--shop-action-padding-x:\s*12px;/, 'H5普通操作按钮内边距必须与小程序24rpx等比')
requireText('mall-shop-web/src/assets/styles.css', /\.ui-action-button, \.ui-order-action\s*\{[^}]*flex:\s*none;[^}]*width:\s*auto\s*!important;[^}]*height:\s*var\(--shop-action-height\)\s*!important;/, 'H5普通操作按钮必须按文字收宽，禁止均分或固定大宽度')
requireText('mall-shop-web/src/assets/styles.css', /\.ui-action-button, \.ui-order-action\s*\{[^}]*margin-left:\s*0\s*!important;[^}]*margin-right:\s*0\s*!important;/, 'H5普通操作按钮必须与小程序共用零左右外边距规范')
requireText('mall-shop-web/src/assets/styles.css', /\.ui-action-bar, \.ui-action-group\s*\{[^}]*flex-wrap:\s*nowrap;[^}]*justify-content:\s*flex-end;/, 'H5普通操作组必须单行右对齐')
requireText('mall-shop-web/src/assets/styles.css', /\.ui-copy-action\s*\{[^}]*background:\s*transparent\s*!important;[^}]*font-size:\s*11px\s*!important;/, 'H5复制必须使用小型文字按钮')

if (miniCore.includes('.ui-utility-button--wide')) failures.push('mall-mini-program/app.wxss: 禁止恢复固定宽度的辅助按钮')

const sharedOrderSelectors = ['.ui-order-card', '.ui-order-header', '.ui-order-products', '.ui-order-product', '.ui-order-product-name', '.ui-order-product-spec', '.ui-order-service-tags', '.ui-order-product-prices', '.ui-order-logistics', '.ui-order-summary', '.ui-order-actions', '.ui-order-action']
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
  requireText(path, /ui-order-copy/, '订单列表右上角必须提供小型复制订单号操作')
  requireText(path, /退换\/售后/, '订单列表售后操作统一命名为“退换/售后”')
  requireText(path, /再买一单/, '订单列表再次购买操作必须与其他操作保持同一组')
}

for (const path of ['mall-mini-program/pages/order-detail/index.wxml', 'mall-shop-web/src/views/OrderDetailView.vue']) {
  requireText(path, /ui-action-button/, '订单详情的普通操作必须使用 ui-action-button')
  requireText(path, /ui-copy-action/, '订单详情的复制操作必须使用 ui-copy-action')
  const source = read(path)
  for (const match of source.matchAll(/<button\b([^>]*)>[^<]*复制[^<]*<\/button>/g)) {
    if (!match[1].includes('ui-copy-action')) failures.push(`${path}: “复制”必须使用 ui-copy-action 小型文字按钮`)
  }
}
requireText('mall-shop-web/src/views/OrderDetailView.vue', /class="after-sale-record-actions ui-action-bar"/, 'H5售后记录操作必须使用共用单行操作组')
requireText('mall-shop-web/src/views/OrderDetailView.vue', /class="btn secondary ui-action-button after-sale-cancel"/, 'H5售后记录普通操作必须使用共用紧凑按钮')
requireText('mall-mini-program/pages/order-detail/index.wxml', /class="sale-actions ui-action-bar"/, '小程序售后记录操作必须使用共用单行操作组')

for (const path of ['mall-mini-program/pages/orders/index.wxml', 'mall-shop-web/src/views/OrdersView.vue']) {
  const source = read(path)
  if (/class="[^"]*(?:primary-button|btn primary)[^"]*"[^>]*>去评价/.test(source)) failures.push(`${path}: “去评价”是普通操作，不得占用唯一主题色主操作`)
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
  for (const match of source.matchAll(/([^{}]*(?:ui-card|ui-service-entry|ui-utility-button|ui-action-button|ui-copy-action|ui-price|ui-status-pill|ui-action-bar|ui-action-group)[^{}]*)\{([^{}]*)\}/g)) {
    if (/!important/.test(match[2])) failures.push(`${relative(root, path)}: 共用组件页面覆盖禁止使用 !important（${match[1].trim()}）`)
  }
}

if (failures.length) {
  console.error(`商城界面共用规范检查失败（${failures.length}项）：`)
  for (const failure of failures) console.error(`- ${failure}`)
  process.exit(1)
}

console.log('商城界面共用规范检查通过：普通操作按钮、复制、价格、卡片、状态与客服入口均由共用层管理。')
