import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolveQuickCartItem } from '../src/utils/quickCart.js'
import { extractModuleEntry } from '../src/utils/buildFreshness.js'

const readView = (name) => readFile(new URL(`../src/views/${name}`, import.meta.url), 'utf8')

test('quick add uses the first in-stock SKU without opening product detail', () => {
  const item = resolveQuickCartItem(
    { id: 7, productName: '套装', salePrice: 100, pvValue: 100, stock: 8 },
    {
      product: { id: 7, productName: '套装', coverUrl: '/product.jpg', pvValue: 80 },
      skus: [
        { id: 70, skuName: '缺货款', salePrice: 90, stock: 0 },
        { id: 71, skuName: '默认款', attrsJson: '{"规格":"默认"}', salePrice: 99, pvValue: 120, stock: 3 },
      ],
    },
  )

  assert.equal(item.skuId, 71)
  assert.equal(item.skuName, '默认款')
  assert.equal(item.salePrice, 99)
  assert.equal(item.pvValue, 99)
  assert.equal(item.stock, 3)
})

test('quick add rejects products whose SKUs are all out of stock', () => {
  const item = resolveQuickCartItem(
    { id: 7, stock: 8 },
    { product: { id: 7 }, skus: [{ id: 70, salePrice: 90, stock: 0 }] },
  )
  assert.equal(item, null)
})

test('profile hides unused labels and uses a reliable invite navigation', async () => {
  const source = await readView('ProfileView.vue')
  assert.doesNotMatch(source, /注册用户/)
  assert.doesNotMatch(source, /to="\/orders">全部/)
  assert.match(source, /window\.location\.assign\('\/invite'\)/)
})

test('checkout keeps the address block compact and preserves the remark while switching', async () => {
  const source = await readView('CheckoutView.vue')
  const address = await readView('AddressView.vue')
  assert.match(source, /更换地址/)
  assert.match(source, /address-picker-overlay/)
  assert.match(source, /openAddressPage\('create'\)/)
  assert.match(source, /openAddressPage\('manage'\)/)
  assert.match(source, /checkout_draft/)
  assert.match(source, /v-model="form\.remark"/)
  assert.match(source, /addressesLoading/)
  assert.match(source, /addressesLoadError/)
  assert.match(source, /重新加载地址/)
  assert.match(source, /addressesLoaded && !addresses\.length && !addressesLoadError/)
  assert.match(address, /选择收货地址/)
  assert.match(address, /使用此地址/)
})

test('home shows a dedicated retry state when initial data loading fails', async () => {
  const source = await readView('HomeView.vue')
  assert.match(source, /homeLoadError/)
  assert.match(source, /商城首页暂时加载失败/)
  assert.match(source, /重新加载/)
  assert.match(source, /const reloadHome = async/)
  assert.match(source, /await reloadHome\(\)/)
})

test('checkout only exposes configured payment channels', async () => {
  const source = await readView('CheckoutView.vue')
  assert.match(source, /当前已开通余额支付；微信支付、支付宝通道完成商户配置后会自动显示。/)
  assert.match(source, /payType: 'BALANCE'/)
  assert.doesNotMatch(source, /value: 'WECHAT'/)
  assert.match(source, /payConfig\.value\.alipayEnabled/)
  assert.match(source, /getPayConfig\(\)/)
})

test('alipay checkout posts the generated payment form and CSP allows only the official gateway', async () => {
  const source = await readView('CheckoutView.vue')
  const nginx = await readFile(new URL('../../scripts/nginx/lingqimall.conf', import.meta.url), 'utf8')
  assert.match(source, /div\.innerHTML = payUrl/)
  assert.match(source, /const form = div\.querySelector\('form'\)/)
  assert.match(source, /form\.submit\(\)/)
  assert.match(nginx, /form-action 'self' https:\/\/openapi\.alipay\.com/)
})

test('wallet password setup prompt is shown first and uses payment password wording', async () => {
  const wallet = await readView('WalletView.vue')
  const security = await readView('SecurityView.vue')
  const change = await readView('ChangePaymentPasswordView.vue')
  const orderDetail = await readView('OrderDetailView.vue')
  assert.ok(wallet.indexOf('security-callout') < wallet.indexOf('balance-card'))
  assert.match(wallet, /首次交易前请设置支付密码/)
  assert.match(security, /请先设置支付密码/)
  assert.match(security, /设置支付密码/)
  assert.match(change, /支付密码为6位数字/)
  assert.doesNotMatch(`${security}\n${change}\n${orderDetail}`, /交易密码|二级密码/)
})

test('payment password setup uses the dedicated server-side SMS endpoint', async () => {
  const api = await readFile(new URL('../src/api/shop.js', import.meta.url), 'utf8')
  const change = await readView('ChangePaymentPasswordView.vue')
  const checkout = await readView('CheckoutView.vue')
  assert.match(api, /url: '\/sms\/send\/payment-password'/)
  assert.match(change, /sendPaymentPasswordSmsCode\(\)/)
  assert.match(checkout, /sendPaymentPasswordSmsCode\(\)/)
  assert.doesNotMatch(`${change}\n${checkout}`, /sendSmsCode\([^\n]*,\s*7\)/)
})

test('checkout confirms payment password was saved before continuing payment', async () => {
  const source = await readView('CheckoutView.vue')
  assert.match(source, /支付密码已设置/)
  assert.match(source, /continueAfterPasswordSaved/)
  assert.match(source, /paymentPasswordSaved\.value = true/)
})

test('wallet actions keep transfer on its own page and explain non-agent team access', async () => {
  const wallet = await readView('WalletView.vue')
  const transfer = await readView('BalanceTransferView.vue')
  const team = await readView('TeamPerformanceView.vue')
  assert.match(wallet, /<RouterLink class="wallet-action-link" to="\/profile\/wallet\/transfer">/)
  assert.match(wallet, /grid-template-columns:repeat\(4,minmax\(0,1fr\)\)/)
  assert.doesNotMatch(wallet, /activeTool === 'transfer'/)
  assert.match(transfer, /转账金额只能为整数/)
  assert.match(transfer, /type="number" min="1" step="1"/)
  assert.match(team, /完成首单后开通团队业绩/)
  assert.match(team, /当前账号尚未开通代理身份/)
})

test('home quick add no longer redirects SKU products to product detail', async () => {
  const source = await readView('HomeView.vue')
  assert.match(source, /resolveQuickCartItem/)
  assert.doesNotMatch(source, /router\.push\(`\/product\/\$\{product\.id\}`\)/)
})

test('home exposes trust information and preserves recent search shortcuts', async () => {
  const source = await readView('HomeView.vue')
  assert.match(source, /home-trust-strip/)
  assert.match(source, /showTrustStrip/)
  assert.match(source, /border-radius: 0 999px 999px 0/)
  assert.match(source, /shop_recent_searches/)
  assert.match(source, /热门搜索/)
})

test('wallet balance records show load failures instead of a misleading empty state', async () => {
  const source = await readView('WalletView.vue')
  assert.match(source, /余额记录加载失败，请稍后重试/)
  assert.match(source, /变动前 ¥\{\{ money\(item\.balanceBefore\) \}\}/)
  assert.match(source, /formatDateTime\(item\.createTime\)/)
})

test('frontend exposes the configured FAQ and service rules', async () => {
  const legal = await readView('LegalView.vue')
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')
  assert.match(legal, /type === 'faq'/)
  assert.match(legal, /JSON\.parse\(config\.value\.faqs/)
  assert.match(app, /to="\/legal\/faq"/)
  assert.match(app, /to="\/legal\/after-sale"/)
  assert.match(legal, /replaceTenantPlaceholders/)
})

test('build freshness guard can identify the current production entry', () => {
  assert.equal(
    extractModuleEntry('<script type="module" crossorigin src="/assets/index-new.js"></script>'),
    '/assets/index-new.js',
  )
})

test('cart deletion requires confirmation but checkout navigates directly', async () => {
  const source = await readView('CartView.vue')
  assert.match(source, /@click="requestRemoveSelected"/)
  assert.match(source, /@click="requestCheckoutSelected"/)
  assert.match(source, /@click="requestCheckoutAll"/)
  assert.match(source, /确认删除选中商品/)
  assert.match(source, /if \(selectedKeys\.size\) checkoutSelected\(\)/)
  assert.match(source, /if \(items\.length\) checkoutAll\(\)/)
  assert.doesNotMatch(source, /确认进入结算/)
  assert.match(source, /confirmPendingAction/)
})

test('category view includes search bar for keyword filtering', async () => {
  const source = await readView('CategoryView.vue')
  assert.match(source, /cat-search-bar/)
  assert.match(source, /v-model="query\.keyword"/)
  assert.match(source, /@submit\.prevent="submitSearch"/)
  assert.match(source, /placeholder="搜索商品"/)
})

test('product detail renders rating distribution bars computed from star counts', async () => {
  const source = await readView('ProductDetailView.vue')
  assert.match(source, /barPercent/)
  assert.match(source, /countForStar/)
  assert.match(source, /rating-distribution/)
})

test('product detail always renders the standard after-sale policy section', async () => {
  const source = await readView('ProductDetailView.vue')
  assert.match(source, /after-sale-section/)
  assert.match(source, /defaultAfterSalePolicy/)
  assert.match(source, /售后说明/)
})

test('product detail back button falls back home when there is no browser history', async () => {
  const source = await readView('ProductDetailView.vue')
  assert.match(source, /@click="goBack"/)
  assert.match(source, /window\.history\.state\?\.back/)
  assert.match(source, /router\.replace\(\{ name: 'Home' \}\)/)
})

test('product detail shows the configured member purchase limit', async () => {
  const source = await readView('ProductDetailView.vue')
  assert.match(source, /displayProduct\.purchaseLimit/)
  assert.match(source, /每位会员限购/)
})

test('order detail renders logistics timeline with shipping steps', async () => {
  const source = await readView('OrderDetailView.vue')
  assert.match(source, /timeline-steps/)
  assert.match(source, /courierInitial/)
  assert.match(source, /estimatedDelivery/)
})

test('refund flow uses clear type cards, reason sheet, quantity stepper and estimate summary', async () => {
  const source = await readView('OrderDetailView.vue')
  assert.match(source, /after-sale-type-grid/)
  assert.match(source, /reason-sheet/)
  assert.match(source, /setRefundQuantity/)
  assert.match(source, /预计退款/)
  assert.match(source, /reason: \[selectedReason\.value, afterSaleForm\.value\.reasonDetail\.trim\(\)\]/)
})

test('order queries retry one transient mobile network failure', async () => {
  const request = await readFile(new URL('../src/api/request.js', import.meta.url), 'utf8')
  assert.match(request, /RETRYABLE_METHODS = new Set\(\['get', 'head', 'options'\]\)/)
  assert.match(request, /retryCount < 1 && isTransientTransportError\(error\)/)
  assert.match(request, /return service\.request\(config\)/)
  assert.match(request, /网络暂时不可用，请检查网络后重试/)
})
