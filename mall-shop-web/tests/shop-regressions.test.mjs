import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolveQuickCartItem } from '../src/utils/quickCart.js'
import { extractModuleEntry } from '../src/utils/buildFreshness.js'
import { normalizeLoginAccountInput, resolveRegistrationErrorField, validateLoginAccount } from '../src/utils/loginAccount.js'
import { normalizeNicknameInput, validateNickname } from '../src/utils/nickname.js'
import { localPurchaseLimitViolation, purchaseLimitMessage } from '../src/utils/purchaseLimitRules.js'
import { readDisplayExtraConfig, resolveCategoryGuideConfig, resolveDirectoryGuideLayout, resolveDisplayColors, resolveHomeModules } from '../src/utils/displayConfig.js'
import { resolveBottomNav } from '../src/utils/bottomNav.js'
import { resolveCurrentStock, stockAdditionViolation, stockQuantityViolation } from '../src/utils/stockRules.js'
import { isGatewayRecoveryError, resolveRequestErrorMessage } from '../src/utils/requestErrors.js'
import { resolveFixedBottomShift } from '../src/utils/visualViewportFixedBottom.js'
import { resolveBrandCssVariables, themePresets } from '../src/utils/brand.js'

const readView = (name) => readFile(new URL(`../src/views/${name}`, import.meta.url), 'utf8')
const readStyles = () => readFile(new URL('../src/assets/styles.css', import.meta.url), 'utf8')

test('主题预设、颜色细节和分类导购在刷新后使用同一份确定颜色', async () => {
  const [app, home, category] = await Promise.all([
    readFile(new URL('../src/App.vue', import.meta.url), 'utf8'),
    readView('HomeView.vue'),
    readView('CategoryView.vue'),
  ])
  const themeColors = {
    'retail-red': '#e7193f',
    'fresh-green': '#0f766e',
    'premium-gold': '#9a6a22',
    'soft-purple': '#7c3aed',
  }
  for (const [productTemplate, themeColor] of Object.entries(themeColors)) {
    assert.ok(themePresets[productTemplate])
    const variables = resolveBrandCssVariables({
      themeColor,
      productTemplate,
      displayConfig: { extraConfigJson: JSON.stringify({ colors: { accentColor: themeColor, buttonBg: themeColor } }) },
    })
    assert.equal(variables['--brand-primary'], themeColor)
    assert.equal(variables['--accent'], themeColor)
    assert.equal(variables['--shop-button-bg'], themeColor)
  }

  const legacyBlue = {
    priceColor: '#E5484D', pageBg: '#F6F7F9', headerBg: '#FFFFFF', cardBg: '#FFFFFF',
    textColor: '#1B2430', mutedColor: '#6B7280', accentColor: '#1556A3',
    lineColor: '#E8ECF1', buttonBg: '#1556A3',
  }
  const migrated = resolveBrandCssVariables({
    themeColor: '#e7193f',
    productTemplate: 'retail-red',
    displayConfig: { extraConfigJson: JSON.stringify({ colors: legacyBlue }) },
  })
  assert.equal(migrated['--brand-primary'], '#e7193f')
  assert.equal(migrated['--accent'], '#e7193f')
  assert.equal(migrated['--shop-button-bg'], '#e7193f')

  const custom = resolveBrandCssVariables({
    themeColor: '#345678',
    productTemplate: 'retail-red',
    displayConfig: { extraConfigJson: JSON.stringify({ colors: { accentColor: '#123456', buttonBg: '#654321' } }) },
  })
  assert.equal(custom['--brand-primary'], '#345678')
  assert.equal(custom['--accent'], '#123456')
  assert.equal(custom['--shop-button-bg'], '#654321')
  assert.doesNotMatch(app, /\.app-shell\.layout-category-focus\s*\{[^}]*--brand-primary:\s*#1556a3/s)
  assert.doesNotMatch(home, /applyExtraColors/)
  assert.match(category, /--guide-blue:\s*var\(--accent,var\(--brand-primary\)\)/)
})

test('category guide supports three real templates while bottom navigation stays independent', async () => {
  const categoryView = await readView('CategoryView.vue')
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')
  const legacy = resolveCategoryGuideConfig({ extraConfigJson: '{}' })
  const configured = resolveCategoryGuideConfig({
    categoryGuideTemplate: 'scenario',
    categoryGuideScenariosEnabled: 1,
    categoryGuideQuickEntriesEnabled: 0,
    categoryGuidePopularProductsEnabled: 1,
  })
  const nav = resolveBottomNav([
    { type: 'home', enabled: false },
    { type: 'cart', enabled: false },
    { type: 'profile', enabled: false },
    { type: 'category', enabled: false },
    { type: 'orders', enabled: true },
  ])
  const migratedNav = resolveBottomNav([{ type: 'home', enabled: true, futureStyle: 'keep' }])

  assert.equal(legacy.template, 'directory')
  assert.equal(configured.template, 'scenario')
  assert.equal(configured.modules.quickEntries, false)
  assert.equal(configured.modules.popularProducts, true)
  assert.equal(nav.find((item) => item.type === 'home').enabled, true)
  assert.equal(nav.find((item) => item.type === 'cart').enabled, true)
  assert.equal(nav.find((item) => item.type === 'profile').enabled, true)
  assert.equal(nav.find((item) => item.type === 'category').enabled, false)
  assert.equal(nav.find((item) => item.type === 'orders').enabled, true)
  assert.equal(migratedNav.find((item) => item.type === 'category').enabled, true)
  assert.equal(migratedNav.find((item) => item.type === 'orders').enabled, false)
  assert.equal(migratedNav.find((item) => item.type === 'cart').enabled, true)
  assert.equal(migratedNav.find((item) => item.type === 'profile').enabled, true)
  assert.equal(migratedNav[0].futureStyle, 'keep')
  assert.deepEqual(migratedNav.map((item) => item.type), ['home', 'category', 'cart', 'orders', 'profile'])
  assert.match(categoryView, /guide-directory-shell/)
  assert.match(categoryView, /guide-showcase-grid/)
  assert.match(categoryView, /guide-scenarios/)
  assert.match(categoryView, /请至少开启一个分类导购模块/)
  assert.match(app, /resolveBottomNav/)
  assert.match(app, /bottomNavIndependent/)
  assert.match(app, /Object\.prototype\.hasOwnProperty\.call\(extra, 'bottomNavIndependent'\)/)
})

test('directory category guide renders all eight module combinations without empty grid tracks', async () => {
  const categoryView = await readView('CategoryView.vue')
  const matrix = [
    [false, false, false, 'empty'],
    [true, false, false, 'primary-only'],
    [false, true, false, 'content-only'],
    [false, false, true, 'content-only'],
    [false, true, true, 'content-only'],
    [true, true, false, 'split'],
    [true, false, true, 'split'],
    [true, true, true, 'split'],
  ]
  for (const [primaryCategories, subcategories, hotProducts, expected] of matrix) {
    assert.equal(resolveDirectoryGuideLayout({ primaryCategories, subcategories, hotProducts }), expected)
  }
  assert.match(categoryView, /directoryGuideLayout === 'split'/)
  assert.match(categoryView, /directoryGuideLayout === 'primary-only'/)
  assert.match(categoryView, /categoryGuide\.modules\.subcategories \|\| categoryGuide\.modules\.hotProducts/)
  assert.match(categoryView, /guide-directory-shell\.is-content-only,\.guide-directory-shell\.is-primary-only \{ display: block/)
  assert.doesNotMatch(categoryView, /guide-directory-hero/)
  assert.ok(categoryView.indexOf('class="cat-search"') < categoryView.indexOf('class="category-guide"'))
})

test('mobile bottom navigation follows the iOS visual viewport after browser chrome changes', async () => {
  const [app, teamApp, styles, index] = await Promise.all([
    readFile(new URL('../src/App.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/surfaces/team/TeamApp.vue', import.meta.url), 'utf8'),
    readStyles(),
    readFile(new URL('../index.html', import.meta.url), 'utf8'),
  ])

  assert.equal(resolveFixedBottomShift(0, 640, 740), 100)
  assert.equal(resolveFixedBottomShift(100, 740, 740), 100)
  assert.equal(resolveFixedBottomShift(0, 700, 640), -60)
  assert.match(app, /ref="bottomNavRef"/)
  assert.match(app, /useVisualViewportFixedBottom\(bottomNavRef\)/)
  assert.match(teamApp, /useVisualViewportFixedBottom\(teamBottomNavRef\)/)
  assert.match(styles, /--bottom-nav-viewport-shift/)
  assert.match(index, /viewport-fit=cover/)
})

test('concurrent 401 responses share one login redirect and return shipment company length matches outbound shipping', async () => {
  const request = await readFile(new URL('../src/api/request.js', import.meta.url), 'utf8')
  const orderDetail = await readView('OrderDetailView.vue')

  assert.match(request, /let isRedirectingToLogin = false/)
  assert.match(request, /!isAuthPage && !isRedirectingToLogin/)
  assert.match(request, /finally \{\s*isRedirectingToLogin = false\s*\}/)
  assert.match(orderDetail, /placeholder="物流公司" maxlength="50"/)
  assert.doesNotMatch(orderDetail, /placeholder="物流公司" maxlength="64"/)
})

test('gateway restart errors use customer-facing Chinese copy and safe reads retry once', async () => {
  const request = await readFile(new URL('../src/api/request.js', import.meta.url), 'utf8')
  const gatewayError = { response: { status: 502, data: '<html>Bad Gateway</html>' } }

  assert.equal(isGatewayRecoveryError(gatewayError), true)
  assert.equal(resolveRequestErrorMessage(gatewayError), '系统正在更新或连接正在恢复，请稍后重试')
  assert.equal(
    resolveRequestErrorMessage({ response: { status: 500, data: {} }, message: 'Request failed with status code 500' }),
    '系统服务暂时异常，请稍后重试',
  )
  assert.match(request, /isTransientTransportError\(error\) \|\| isGatewayRecoveryError\(error\)/)
  assert.match(request, /RETRYABLE_METHODS\.has\(method\)/)
  assert.match(request, /isGatewayRecoveryError\(error\) \? 600 : 250/)
})

test('order realtime stops retrying permanent client errors while retaining network fallback', async () => {
  const realtime = await readFile(new URL('../src/utils/orderRealtime.js', import.meta.url), 'utf8')

  assert.match(realtime, /\[400, 401, 403, 404\]\.includes\(response\.status\)\) return/)
  assert.match(realtime, /retry = Math\.min\(retry \* 2, 30000\)/)
})

test('sensitive forms block duplicate submits and order-detail balance payment sends an idempotency key', async () => {
  const [login, forgot, loginPassword, paymentPassword, realName, orderDetail, payloadEncryption] = await Promise.all([
    readView('LoginView.vue'),
    readView('ForgotPasswordView.vue'),
    readView('ChangeLoginPasswordView.vue'),
    readView('ChangePaymentPasswordView.vue'),
    readView('RealNameVerificationView.vue'),
    readView('OrderDetailView.vue'),
    readFile(new URL('../src/utils/payloadEncryption.js', import.meta.url), 'utf8'),
  ])

  assert.match(login, /const submit = async \(\) => \{\s*if \(loading\.value\) return/)
  assert.match(forgot, /const doResetPassword = async \(\) => \{\s*if \(loading\.value\) return/)
  assert.match(loginPassword, /const save = async \(\) => \{\s*if \(saving\.value\) return/)
  assert.match(paymentPassword, /const save = async \(\) => \{\s*if \(saving\.value\) return/)
  assert.match(realName, /const submit = async \(\) => \{\s*if \(saving\.value\) return/)
  assert.match(payloadEncryption, /'realname'/)
  assert.match(payloadEncryption, /'idcard'/)
  assert.match(orderDetail, /createIdempotencyKey\('balance-pay'\)/)
  assert.match(orderDetail, /payOrderWithBalance\(order\.value\.id, paymentPassword\.value, balancePaymentRequestKey\.value\)/)
  assert.match(orderDetail, /balancePaymentRequestKey\.value = ''/)
})

test('checkout limits address fields, avoids persisting recipient PII and clears sensitive payment state', async () => {
  const checkout = await readView('CheckoutView.vue')
  const address = await readView('AddressView.vue')
  const session = await readFile(new URL('../src/utils/shopSession.js', import.meta.url), 'utf8')

  assert.match(checkout, /v-model="form\.receiverName"[^>]*maxlength="30"/)
  assert.match(checkout, /v-model="form\.receiverDetailAddress"[^>]*maxlength="200"/)
  assert.match(checkout, /v-model="form\.remark"[^>]*maxlength="500"/)
  assert.match(address, /v-model="form\.receiverName"[^>]*maxlength="30"/)
  assert.match(address, /v-model="form\.detailAddress"[^>]*maxlength="200"/)
  assert.doesNotMatch(checkout, /sessionStorage\.setItem\('checkout_draft',[\s\S]{0,180}receiverName/)
  assert.match(checkout, /finally \{\s*payPasswordInput\.value = ''\s*payPasswordSubmitting\.value = false/)
  assert.match(session, /let legacyShopToken = localStorage\.getItem\(LEGACY_TOKEN_KEY\)\s*localStorage\.removeItem\(LEGACY_TOKEN_KEY\)/)
  assert.match(session, /export const getLegacyShopToken = \(\) => legacyShopToken/)
})

test('storefront product images use one loop-safe fallback when remote media fails', async () => {
  const fallback = await readFile(new URL('../src/utils/imageFallback.js', import.meta.url), 'utf8')
  const views = await Promise.all([
    readView('HomeView.vue'),
    readView('ProductDetailView.vue'),
    readView('CartView.vue'),
    readView('CheckoutView.vue'),
    readView('OrdersView.vue'),
  ])

  assert.match(fallback, /fallbackApplied/)
  assert.match(fallback, /图片暂不可用/)
  for (const source of views) {
    assert.match(source, /applyImageFallback/)
    assert.match(source, /@error="applyImageFallback"/)
  }
})

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

test('stock checks block every cart entry point and report the current remaining quantity', async () => {
  assert.equal(stockAdditionViolation(5, 3, 3), '库存不足，最多可购买2件')
  assert.equal(stockAdditionViolation(5, 2, 3), '')
  assert.equal(stockAdditionViolation(3, 1, 3), '库存不足，最多可购买0件')
  assert.equal(stockQuantityViolation(2, 3), '库存不足，最多可购买2件')
  assert.equal(resolveCurrentStock({ id: 7, skuId: 71 }, {
    product: { id: 7, stock: 9 },
    skus: [{ id: 71, stock: 2 }, { id: 72, stock: 8 }],
  }), 2)

  const store = await readFile(new URL('../src/store/cart.js', import.meta.url), 'utf8')
  const home = await readView('HomeView.vue')
  const category = await readView('CategoryView.vue')
  const detail = await readView('ProductDetailView.vue')
  const cart = await readView('CartView.vue')
  assert.match(store, /stockAdditionViolation\(product\.stock, requestedQuantity, existing\?\.quantity \|\| 0\)/)
  assert.match(home, /stockAdditionViolation\(cartItem\.stock, 1, getQuantity\(cartItemKey\(cartItem\)\)\)/)
  assert.match(category, /stockAdditionViolation\(cartItem\.stock, 1, getQuantity\(cartItemKey\(cartItem\)\)\)/)
  assert.match(detail, /resolveCurrentStock\(displayProduct\.value, detail\)/)
  assert.match(cart, /const latestStock = resolveCurrentStock\(item, detail\)/)
  assert.match(cart, /stockQuantityViolation\(latestStock, item\.quantity\)/)
})

test('cart refreshes display price and inventory from the server before showing totals', async () => {
  const cart = await readView('CartView.vue')
  assert.match(cart, /const syncCartItemFromDetail = \(item, detail\) =>/)
  assert.match(cart, /item\.salePrice = salePrice/)
  assert.match(cart, /item\.stock = resolveCurrentStock\(item, detail\)/)
  assert.match(cart, /if \(item\.skuId && !sku\) \{[\s\S]*item\.stock = 0/)
  assert.match(cart, /const refreshCartFromServer = async/)
  assert.match(cart, /refreshCartFromServer\(\)/)
  assert.match(cart, /商品价格和库存以结算页服务端确认为准/)
})

test('purchase limits are checked before add-to-cart and still kept as a server-side fallback', async () => {
  const helper = await readFile(new URL('../src/utils/purchaseLimit.js', import.meta.url), 'utf8')
  const home = await readView('HomeView.vue')
  const category = await readView('CategoryView.vue')
  const detail = await readView('ProductDetailView.vue')
  const cart = await readView('CartView.vue')

  assert.match(helper, /checkPurchaseLimit\(product\.id, quantity\)/)
  assert.match(helper, /existingCartQuantity/)
  assert.match(helper, /localPurchaseLimitViolation/)
  assert.ok(
    helper.indexOf('const localViolation = localPurchaseLimitViolation') < helper.indexOf('hasShopSession()'),
    '游客也应先按购物车数量执行本地限购拦截',
  )
  assert.match(home, /await checkCartPurchaseLimit\(cartItem, 1, getProductQuantity\(cartItem\.id\)\)/)
  assert.match(category, /await checkCartPurchaseLimit\(cartItem, 1, getProductQuantity\(cartItem\.id\)\)/)
  assert.match(detail, /await checkCartPurchaseLimit\(latestProduct, quantity\.value, getProductQuantity\(latestProduct\.id\)\)/)
  assert.match(detail, /const addToCart = async/)
  assert.match(detail, /const buyNow = async/)
  assert.doesNotMatch(helper, /limit <= 0/)
  assert.match(cart, /const changeQuantity = async/)
  assert.match(cart, /await checkCartPurchaseLimit\(item, 1, getProductQuantity\(item\.id\)\)/)
  assert.match(cart, /const validateCheckoutItems = async/)
  assert.match(cart, /await validateCheckoutItems\(rows\)/)
  assert.match(cart, /await validateCheckoutItems\(items\)/)
  assert.doesNotMatch(cart, /@click="update\(item\.cartKey \|\| item\.id, item\.quantity \+ 1\)"/)
  assert.match(home, /isAddingProduct\(product\.id\)/)
  assert.match(category, /isAddingProduct\(product\.id\)/)
  assert.match(detail, /purchaseActionPending/)
})

test('purchase-limit messaging is immediate and accurately explains exhausted quota', () => {
  const product = { id: 7, productName: '轻奢焕活礼盒', purchaseLimit: 1 }

  assert.equal(
    localPurchaseLimitViolation(product, 1, 1),
    '轻奢焕活礼盒每位会员限购 1 件，您已达到限购数量，无法继续加购',
  )
  assert.equal(localPurchaseLimitViolation(product, 1, 0), '')
  assert.equal(purchaseLimitMessage('测试商品', 3, 1), '测试商品每位会员限购 3 件，您还可购买 1 件')
})

test('buy now creates an isolated checkout and never merges an existing cart row', async () => {
  const detail = await readView('ProductDetailView.vue')
  const cartStore = await readFile(new URL('../src/store/cart.js', import.meta.url), 'utf8')

  assert.match(detail, /beginDirectCheckout\(latestProduct, quantity\.value\)/)
  assert.match(detail, /checkCartPurchaseLimit\(latestProduct, quantity\.value, 0\)/)
  assert.doesNotMatch(detail, /const cartKey = add\(displayProduct\.value, quantity\.value\)[\s\S]{0,100}beginCheckout/)
  assert.match(cartStore, /directCheckoutItems: null/)
  assert.match(cartStore, /if \(Array\.isArray\(state\.directCheckoutItems\)\) return state\.directCheckoutItems/)
  assert.match(cartStore, /if \(Array\.isArray\(state\.directCheckoutItems\)\) \{[\s\S]*state\.directCheckoutItems = null[\s\S]*return/)
})

test('cart feedback uses a centered transient toast instead of a confirmation dialog', async () => {
  const styles = await readStyles()
  const home = await readView('HomeView.vue')
  const category = await readView('CategoryView.vue')

  assert.match(styles, /\.toast \{[\s\S]*top: 50%;[\s\S]*transform: translate\(-50%, -50%\)/)
  assert.match(styles, /pointer-events: none/)
  assert.match(home, /window\.setTimeout\(\(\) => \{ toast\.value = '' \}, 2200\)/)
  assert.match(category, /window\.setTimeout\(\(\) => \{ toast\.value = '' \}, 2200\)/)
})

test('profile opens one compact invite dialog instead of navigating away', async () => {
  const source = await readView('ProfileView.vue')
  assert.doesNotMatch(source, /注册用户/)
  assert.doesNotMatch(source, /to="\/orders">全部/)
  assert.match(source, /inviteDialogVisible\.value = true/)
  assert.match(source, /<InviteDialog :visible="inviteDialogVisible"/)
  assert.doesNotMatch(source, /window\.location\.assign\('\/invite'\)/)
})

test('invite content keeps QR code, invitation code and member data in one card', async () => {
  const view = await readView('InviteView.vue')
  const card = await readFile(new URL('../src/components/InviteCard.vue', import.meta.url), 'utf8')
  const dialog = await readFile(new URL('../src/components/InviteDialog.vue', import.meta.url), 'utf8')

  assert.equal((view.match(/<section class="panel invite-panel">/g) || []).length, 1)
  assert.match(view, /<InviteCard \/>/)
  assert.match(card, /alt="邀请注册二维码"/)
  assert.match(card, /<span>邀请码<\/span>/)
  assert.match(card, /directAccountCount/)
  assert.match(card, /directMemberCount/)
  assert.match(card, /<span>注册账号<\/span>/)
  assert.match(card, /<span>正式会员<\/span>/)
  assert.match(dialog, /role="dialog" aria-modal="true"/)
})

test('account forms and product quantity controls expose semantic labels', async () => {
  const login = await readView('LoginView.vue')
  const forgot = await readView('ForgotPasswordView.vue')
  const product = await readView('ProductDetailView.vue')
  assert.match(login, /for="login-account"/)
  assert.match(login, /id="login-password"[\s\S]*autocomplete="current-password"/)
  assert.match(login, /id="register-agreement"[\s\S]*type="checkbox"/)
  assert.match(forgot, /for="forgot-phone"/)
  assert.match(forgot, /autocomplete="one-time-code"/)
  assert.match(product, /aria-label="减少购买数量"/)
  assert.match(product, /aria-label="增加购买数量"/)
})

test('homepage exposes the legal qualification link while internal APK builds stay unpublished', async () => {
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')
  const download = await readView('AppDownloadView.vue')
  const release = JSON.parse(await readFile(new URL('../public/downloads/android-release.json', import.meta.url), 'utf8'))
  assert.match(app, /to="\/legal\/license">经营资质/)
  assert.match(download, /releaseAvailable/)
  assert.match(download, /shopBrand\?\.value\?\.logoUrl/)
  assert.match(download, /currentBrandLogo\(\)/)
  assert.match(download, /alt="`\$\{brandName\} APP 图标`"/)
  assert.doesNotMatch(download, />LQ<\/div>/)
  assert.equal(release.published, false)
  assert.equal(release.channel, 'internal-test')
})

test('mobile interactive areas suppress the browser tap highlight without disabling form input', async () => {
  const styles = await readStyles()
  const login = await readView('LoginView.vue')

  assert.match(styles, /input\[type="checkbox"\][\s\S]*-webkit-tap-highlight-color: transparent/)
  assert.match(styles, /\[role="button"\][\s\S]*user-select: none/)
  assert.doesNotMatch(styles, /input,\s*textarea[\s\S]{0,120}user-select: none/)
  assert.match(login, /\.agreement-check \{[^}]*-webkit-tap-highlight-color: transparent;/)
  assert.match(login, /\.agreement-consent \{[^}]*user-select:none;/)
})

test('registration page does not expose internal membership activation copy', async () => {
  const login = await readView('LoginView.vue')

  assert.doesNotMatch(login, /注册后是商城用户/)
  assert.doesNotMatch(login, /完成首笔有效支付订单后正式成为会员/)
  assert.match(login, /<p v-if="mode === 'login'">登录后可管理地址、订单和售后。<\/p>/)
})

test('protected routes restore a valid HttpOnly session when the local login hint is missing', async () => {
  const session = await readFile(new URL('../src/utils/shopSession.js', import.meta.url), 'utf8')
  const publicRouter = await readFile(new URL('../src/router/index.js', import.meta.url), 'utf8')
  const teamRouter = await readFile(new URL('../src/surfaces/team/router.js', import.meta.url), 'utf8')
  const integratedRouter = await readFile(new URL('../src/surfaces/integrated/router.js', import.meta.url), 'utf8')

  assert.match(session, /credentials: 'include'/)
  assert.match(session, /'\/shop\/public\/profile'/)
  assert.match(session, /'\/shop\/auth\/me'/)
  assert.match(session, /payload\?\.data\?\.member \|\| payload\?\.data/)
  assert.match(session, /applyShopSession\(member\)/)
  assert.match(session, /let sessionVerified = false/)
  assert.match(session, /export const hasShopSession = \(\) => sessionVerified && Boolean/)
  assert.match(session, /export const clearShopSession[\s\S]{0,180}sessionVerified = false/)
  assert.match(publicRouter, /await restoreShopSession\('public'\)/)
  assert.match(teamRouter, /await restoreShopSession\('team'\)/)
  assert.match(integratedRouter, /await restoreShopSession\('integrated'\)/)
})

test('captcha can be visibly refreshed when it is hard to read', async () => {
  const login = await readView('LoginView.vue')

  assert.match(login, /aria-label="刷新图形验证码"/)
  assert.match(login, /<span>换一张<\/span>/)
  assert.match(login, /@click="refreshCaptcha"/)
  assert.match(login, /onMounted\(\(\) => \{[\s\S]*refreshCaptcha\(\)[\s\S]*\}\)/)
  assert.doesNotMatch(login, /if \(mode\.value === 'login'\) refreshCaptcha\(\)/)
  assert.match(login, /\.auth-page \{ position: relative; width:min\(560px,calc\(100% - 40px\)\); \}/)
})

test('login errors stay beside their field, expire quickly, and auth pages use a compact layout', async () => {
  const login = await readView('LoginView.vue')
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.match(login, /loginFieldErrors\.account/)
  assert.match(login, /showLoginFieldError\('account', '请输入手机号或登录账号'\)/)
  assert.match(login, /fieldErrorTimer = window\.setTimeout\(\(\) => \{ loginFieldErrors\.value = \{\} \}, 2000\)/)
  assert.match(login, /feedbackTimer = window\.setTimeout[\s\S]*1800/)
  assert.match(login, /watch\(\(\) => route\.fullPath, \(\) => clearFeedback\(\)\)/)
  assert.match(login, /class="auth-feedback-toast"/)
  assert.doesNotMatch(login, /<p v-if="error"/)
  assert.match(login, /\.register-page \.form-grid \{ grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/)
  assert.doesNotMatch(login, /class="form-item">\s*<label>昵称/)
  assert.match(login, /<label[^>]*>登录账号/)
  assert.match(login, /showRegisterServerError/)
  assert.match(login, /resolveRegistrationErrorField\(text\)/)
  assert.match(login, /class="form-item">\s*<label[^>]*>登录密码/)

  assert.match(app, /const isAuthPage = computed/)
  assert.match(app, /<footer v-if="isHome"/)
  assert.match(app, /!isCheckout\.value && !isAuthPage\.value/)
  assert.match(app, /watch\(\(\) => route\.fullPath[\s\S]*authPrompt\.value = ''/)
})

test('guest purchase actions are blocked before cart mutation or checkout', async () => {
  const [authNavigation, app, cart, home, category, detail, router] = await Promise.all([
    readFile(new URL('../src/utils/authNavigation.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/App.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/store/cart.js', import.meta.url), 'utf8'),
    readView('HomeView.vue'),
    readView('CategoryView.vue'),
    readView('ProductDetailView.vue'),
    readFile(new URL('../src/router/index.js', import.meta.url), 'utf8'),
  ])

  assert.match(authNavigation, /export const requireShopSession/)
  assert.match(authNavigation, /authRequired: '1'/)
  assert.match(app, /preserveAuthPrompt = route\.name === 'Login' && route\.query\.authRequired === '1'/)
  assert.match(cart, /const assertAuthenticatedCartAction/)
  assert.match(cart, /const add = \(product, quantity = 1\) => \{\s*assertAuthenticatedCartAction\(\)/)
  assert.match(cart, /const beginCheckout = \(keys\) => \{\s*assertAuthenticatedCartAction\(\)/)
  assert.match(cart, /const beginDirectCheckout = \(product, quantity = 1\) => \{\s*assertAuthenticatedCartAction\(\)/)
  assert.match(home, /const addProduct = async \(product\) => \{\s*if \(!requireShopSession\(router, route\.fullPath, '请先登录后再加入购物车'\)\) return/)
  assert.match(category, /const addProduct = async \(product\) => \{\s*if \(!requireShopSession\(router, route\.fullPath, '请先登录后再加入购物车'\)\) return/)
  assert.match(detail, /const addToCart = async \(\) => \{\s*if \(!requireShopSession\(router, route\.fullPath, '请先登录后再加入购物车'\)\) return/)
  assert.match(detail, /const buyNow = async \(\) => \{\s*if \(!requireShopSession\(router, route\.fullPath, '请先登录后再购买商品'\)\) return/)
  assert.match(router, /path: '\/cart'[\s\S]*requiresAuth: true/)
  assert.match(router, /path: '\/checkout'[\s\S]*requiresAuth: true/)
})

test('login page uses the configured shop logo and adapts to mainstream mobile heights', async () => {
  const login = await readView('LoginView.vue')
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.match(app, /provide\('shopBrand', brand\)/)
  assert.match(login, /class="auth-brand-header"/)
  assert.match(login, /class="auth-brand-logo"/)
  assert.match(login, /currentBrandLogo/)
  assert.match(login, /min-height:100dvh/)
  assert.match(login, /\.register-page \.auth-brand-header \{ min-height:44px; margin:0 auto 12px; \}/)
  assert.match(login, /@media \(max-width: 920px\) and \(max-height: 700px\)/)
  assert.match(login, /@media \(max-width: 380px\), \(max-height: 600px\)/)
})

test('desktop shell exposes the same configured navigation and account entry as mobile', async () => {
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.match(app, /class="site-header desktop-site-header"/)
  assert.match(app, /v-for="item in bottomNavItems"/)
  assert.match(app, /aria-label="电脑端商城导航"/)
  assert.match(app, /v-if="isLoggedIn" class="desktop-account-link" to="\/profile"/)
  assert.match(app, /class="desktop-login-link" :to="loginLocation">登录/)
  assert.match(app, /class="desktop-register-link" to="\/register">注册/)
  assert.match(app, /window\.addEventListener\('storage', syncAuthState\)/)
  assert.match(app, /@media \(max-width: 920px\) \{ \.desktop-site-header \{ display:none; \} \}/)
})

test('registration login account filters illegal characters and validates its structure', () => {
  assert.equal(normalizeLoginAccountInput(' 蜗牛 A-b@c_12345678901234567890 '), 'Abc_1234567890123456')
  assert.equal(validateLoginAccount(''), '请输入登录账号')
  assert.equal(validateLoginAccount('1234'), '登录账号必须以英文字母开头')
  assert.equal(validateLoginAccount('abc'), '登录账号至少4位')
  assert.equal(validateLoginAccount('user@123'), '仅支持英文字母、数字和下划线')
  assert.equal(validateLoginAccount('user_123'), '')
  assert.equal(resolveRegistrationErrorField('该手机号已注册，请直接登录'), 'phone')
  assert.equal(resolveRegistrationErrorField('该登录账号已被使用'), 'username')
  assert.equal(resolveRegistrationErrorField('邀请码无效'), 'inviteCode')
  assert.equal(resolveRegistrationErrorField('短信验证码错误'), 'smsCode')
  assert.equal(resolveRegistrationErrorField('登录密码需为6至32位'), 'password')
  assert.equal(resolveRegistrationErrorField('网络错误'), '')
})

test('registration submit uses accurate popup feedback instead of a red bottom login hint', async () => {
  const login = await readView('LoginView.vue')

  assert.doesNotMatch(login, /已有账号，返回登录/)
  assert.match(login, /class="register-back-login"/)
  assert.match(login, /class="register-popup-mask"/)
  assert.match(login, /短信验证码应为6位/)
  assert.match(login, /短信验证码错误，请重新输入/)
  assert.match(login, /短信验证码已过期，请重新获取/)
  assert.match(login, /showRegisterPopup\('账号注册成功', 'success'/)
})

test('checkout shows payment-password lock state before opening the balance payment sheet', async () => {
  const checkout = await readView('CheckoutView.vue')

  assert.match(checkout, /paymentPasswordLockRemainingSeconds/)
  assert.match(checkout, /支付密码已锁定，请\$\{remaining\}后再试；如需立即处理，请联系客服/)
  assert.match(checkout, /form\.payType === 'BALANCE' && paymentPasswordLocked/)
  assert.match(checkout, /:disabled="submitting \|\| \(form\.payType === 'BALANCE' && paymentPasswordLocked\)"/)
  assert.match(checkout, /if \(String\(e\.message \|\| ''\)\.includes\('锁定30分钟'\)\) await fetchWallet\(\)/)
})

test('nickname is edited in account settings with the same front and back compatible rules', async () => {
  const profile = await readView('ProfileView.vue')
  const settings = await readView('ProfileSettingsView.vue')
  assert.match(profile, /to="\/profile\/settings"/)
  assert.doesNotMatch(profile, /class="panel account-panel"/)
  assert.match(settings, /修改昵称/)
  assert.match(settings, /更换绑定手机号/)
  assert.match(settings, /currentPhoneSmsCode/)
  assert.match(settings, /newPhoneSmsCode/)
  assert.equal(normalizeNicknameInput(' 小李🙂  A@_ '), ' 小李 A_ ')
  assert.equal(validateNickname('小李'), '')
  assert.equal(validateNickname('A'), '昵称需为2至20个字符')
  assert.match(validateNickname('小李🙂'), /昵称仅支持/)
})

test('profile renders immediately and loads order counts, wallet and performance separately', async () => {
  const profile = await readView('ProfileView.vue')
  const orders = await readView('OrdersView.vue')

  assert.match(profile, /profile\.value\.orderSummary/)
  assert.doesNotMatch(profile, /profile\.value\.orders/)
  assert.match(profile, /const fetchWallet = async/)
  assert.match(profile, /const fetchPerformance = async/)
  assert.match(profile, /fetchProfile\(\)/)
  assert.match(profile, /fetchWallet\(\)/)
  assert.match(profile, /fetchPerformance\(\)/)
  assert.doesNotMatch(profile, /Promise\.all\(\[getProfile/)

  assert.match(orders, /const pageSize = 10/)
  assert.match(orders, /orderState: orderStateMap\[activeTab\.value\]/)
  assert.match(orders, /getProfileOrderSummary\(\)/)
  assert.match(orders, /load-more-orders/)
  assert.doesNotMatch(orders, /pageSize: 500/)
})

test('pending shipment and active after-sale use mutually exclusive current states', async () => {
  const orders = await readView('OrdersView.vue')

  assert.match(orders, /\[0, 4, 5, 6\]\.includes\(Number\(sale\.status\)\)/)
  assert.match(orders, /activeAfterSales\(item\)\.length > 0/)
  assert.match(orders, /afterSaleStatus\(activeAfterSales\(item\)\[0\]\?\.status\)/)
})

test('profile shows every team performance entry only when backend grants access', async () => {
  const profile = await readView('ProfileView.vue')

  assert.match(profile, /v-if="showTeamPerformance" to="\/profile\/team"><span>本月团队业绩<\/span>/)
  assert.match(profile, /v-if="showTeamPerformance" to="\/profile\/team" class="menu-tile"/)
  assert.match(profile, /identity-stats" :class="\{ 'without-team-performance': !showTeamPerformance \}"/)
  assert.match(profile, /profile-menu" :class="\{ 'without-team-performance': !showTeamPerformance \}"/)
  assert.match(profile, /performanceProfile\.value\.canViewTeamPerformance === true/)
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
  assert.match(address, /@click="startEdit\(address\)"/)
  assert.match(address, /@click="removeAddress\(address\)"/)
  assert.doesNotMatch(address, /已自动填入，请核对后保存/)
  assert.match(address, /删除收货地址？/)
  assert.match(address, /<ConfirmDialog/)
  assert.match(address, /fieldErrors\.receiverName/)
  assert.match(address, /请选择完整的省、市、区\/县/)
  assert.match(address, /form-toast/)
  assert.doesNotMatch(address, /class="page-message"/)
  assert.doesNotMatch(address, /window\.confirm\(/)
  assert.match(source, /checkout-toast/)
  assert.doesNotMatch(source, /class="page-message"/)
})

test('home shows a dedicated retry state when initial data loading fails', async () => {
  const source = await readView('HomeView.vue')
  assert.match(source, /homeLoadError/)
  assert.match(source, /商城首页暂时加载失败/)
  assert.match(source, /重新加载/)
  assert.match(source, /const reloadHome = async/)
  assert.match(source, /await reloadHome\(\)/)
})

test('home modules and colors honor the saved visual-workbench extra configuration', async () => {
  const config = {
    extraConfigJson: JSON.stringify({
      homeModules: [
        { type: 'products', enabled: true, sort: 2 },
        { type: 'banner', enabled: 'false', sort: 1 },
      ],
      colors: { priceColor: '#cc0000' },
      showTrustStrip: 0,
    }),
  }
  const modules = resolveHomeModules(config, [])
  assert.equal(modules[0].type, 'banner')
  assert.equal(modules[0].enabled, false)
  assert.equal(modules[1].enabled, true)
  assert.equal(resolveDisplayColors(config).priceColor, '#cc0000')
  assert.equal(readDisplayExtraConfig(config).showTrustStrip, 0)

  const source = await readView('HomeView.vue')
  assert.match(source, /resolveHomeModules\(displayConfig\.value, defaultModules\)/)
  assert.match(source, /mod\.type === 'products' && mod\.enabled/)
})

test('all four home layouts keep one module order and only apply distinct visual styles', async () => {
  const source = await readView('HomeView.vue')
  const configured = [
    { type: 'products', enabled: true, sort: 1 },
    { type: 'category', enabled: false, sort: 2 },
    { type: 'banner', enabled: true, sort: 3 },
  ]
  const expected = resolveHomeModules({ homeModules: configured }, [])

  for (const layoutTemplate of ['standard', 'product-focus', 'category-focus', 'campaign-feed']) {
    const refreshed = resolveHomeModules({
      layoutTemplate,
      homeModules: JSON.parse(JSON.stringify(configured)),
    }, [])
    assert.deepEqual(refreshed, expected)
  }

  assert.match(source, /v-for="mod in homeModules"/)
  assert.match(source, /mod\.type === 'category' && mod\.enabled && showHomeCategories/)
  assert.match(source, /\['standard', 'product-focus', 'category-focus', 'campaign-feed'\]\.includes/)
  assert.match(source, /\.home-page\.layout-product-focus \.home-product-grid/)
  assert.match(source, /\.home-page\.layout-category-focus \.home-category-section/)
  assert.match(source, /\.home-page\.layout-campaign-feed \.home-product-card/)
  assert.doesNotMatch(source, /\.home-page\.layout-campaign-feed \.business-entry-nav\s*\{[^}]*display\s*:\s*none/s)
})

test('new homepage modules are merged into an existing visual-workbench configuration', () => {
  const defaults = [
    { type: 'banner', enabled: true, sort: 1 },
    { type: 'live', enabled: true, sort: 4 },
    { type: 'newArrivals', enabled: true, sort: 5 },
    { type: 'products', enabled: true, sort: 7 },
  ]
  const modules = resolveHomeModules({ homeModules: [
    { type: 'banner', enabled: false, sort: 1 },
    { type: 'products', enabled: true, sort: 6 },
  ] }, defaults)
  assert.deepEqual(modules.map((item) => item.type), ['banner', 'live', 'newArrivals', 'products'])
  assert.equal(modules.find((item) => item.type === 'banner').enabled, false)

  const migrated = resolveHomeModules({ homeModules: [
    { type: 'banner', enabled: true, sort: 1 },
    { type: 'discovery', enabled: false, sort: 4 },
    { type: 'products', enabled: true, sort: 6 },
  ] }, defaults)
  assert.deepEqual(migrated.map((item) => item.type), ['banner', 'live', 'newArrivals', 'products'])
  assert.equal(migrated.find((item) => item.type === 'live').enabled, false)
  assert.equal(migrated.find((item) => item.type === 'newArrivals').enabled, false)
})

test('checkout only exposes configured payment channels', async () => {
  const source = await readView('CheckoutView.vue')
  assert.match(source, /当前已开通余额支付；微信支付、支付宝通道完成商户配置后会自动显示。/)
  assert.match(source, /payType: 'BALANCE'/)
  assert.doesNotMatch(source, /value: 'WECHAT'/)
  assert.match(source, /payConfig\.value\.alipayEnabled/)
  assert.match(source, /getPayConfig\(\)/)
})

test('customers can cancel before return shipment and correct submitted tracking', async () => {
  const api = await readFile(new URL('../src/api/shop.js', import.meta.url), 'utf8')
  const source = await readView('OrderDetailView.vue')
  assert.match(api, /url: `\/shop\/after-sales\/\$\{id\}\/cancel`/)
  assert.match(source, /取消申请/)
  assert.match(source, /物流公司：\{\{ sale\.returnDeliveryCompany/)
  assert.match(source, /查询退货物流/)
  assert.match(source, /\[0, 4\]\.includes\(sale\.status\)/)
  assert.match(source, /修改退货物流/)
  assert.match(source, /\^\[A-Za-z0-9_-\]\{4,64\}\$/)
  assert.match(source, /取消后不会产生退款；如仍在售后期限内/)
  assert.doesNotMatch(source, /window\.confirm\(/)
})

test('simple confirmations use one branded accessible dialog across customer pages', async () => {
  const component = await readFile(new URL('../src/components/ConfirmDialog.vue', import.meta.url), 'utf8')
  const cart = await readView('CartView.vue')
  const address = await readView('AddressView.vue')
  const profile = await readView('ProfileView.vue')
  const detail = await readView('OrderDetailView.vue')
  const orders = await readView('OrdersView.vue')

  assert.match(component, /role="alertdialog"/)
  assert.match(component, /aria-modal="true"/)
  assert.match(component, /cancelButtonRef\.value\?\.focus\(\)/)
  assert.match(component, /loadingText/)
  assert.match(component, /iconMap/)
  assert.doesNotMatch(component, /⚠️|🗑️|🛒/)
  for (const source of [cart, address, profile, detail, orders]) {
    assert.match(source, /<ConfirmDialog/)
    assert.doesNotMatch(source, /window\.confirm\(/)
  }
  assert.match(detail, /确认已收到商品？/)
  assert.match(orders, /取消这笔订单？/)
})

test('alipay checkout reconstructs a safe official payment form and CSP allows only the official gateway', async () => {
  const source = await readFile(new URL('../src/utils/alipay.js', import.meta.url), 'utf8')
  const checkout = await readView('CheckoutView.vue')
  const detail = await readView('OrderDetailView.vue')
  const nginx = await readFile(new URL('../../scripts/nginx/lingqimall.conf', import.meta.url), 'utf8')
  assert.match(source, /new DOMParser\(\)/)
  assert.match(source, /allowedHosts = new Set\(\['openapi\.alipay\.com', 'openapi\.alipaydev\.com'\]\)/)
  assert.match(source, /sourceForm\.querySelectorAll\('input\[name\]'\)/)
  assert.doesNotMatch(source, /innerHTML\s*=/)
  assert.match(source, /form\.submit\(\)/)
  assert.match(checkout, /removeCheckedOutItems\(\)/)
  assert.equal((checkout.match(/removeCheckedOutItems\(\)/g) || []).length, 1)
  assert.ok(checkout.indexOf('detailOrderId = res.data.order.id') < checkout.indexOf('removeCheckedOutItems()'))
  assert.match(checkout, /let checkoutId = pendingCheckoutId\.value/)
  assert.match(checkout, /pendingCheckoutId\.value = checkoutId/)
  assert.match(checkout, /:disabled="Boolean\(pendingCheckoutId\)"/)
  assert.match(detail, /createAlipayOrder\(order\.value\.id\)/)
  assert.match(detail, /submitTrustedAlipayForm/)
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

test('wallet transfer is packaged only by integrated H5 and requires verified adult accounts', async () => {
  const wallet = await readView('WalletView.vue')
  const transfer = await readView('BalanceTransferView.vue')
  const integratedWallet = await readFile(new URL('../src/surfaces/integrated/IntegratedWalletView.vue', import.meta.url), 'utf8')
  const teamRouter = await readFile(new URL('../src/surfaces/team/router.js', import.meta.url), 'utf8')
  const integratedRouter = await readFile(new URL('../src/surfaces/integrated/router.js', import.meta.url), 'utf8')
  const team = await readView('TeamPerformanceView.vue')
  assert.doesNotMatch(wallet, /to="\/profile\/wallet\/transfer"/)
  assert.match(integratedWallet, /to="\/profile\/wallet\/transfer"/)
  assert.doesNotMatch(teamRouter, /BalanceTransferView|\/profile\/wallet\/transfer/)
  assert.match(integratedRouter, /BalanceTransferView|\/profile\/wallet\/transfer/)
  assert.match(wallet, /grid-template-columns:repeat\(3,minmax\(0,1fr\)\)/)
  assert.doesNotMatch(wallet, /activeTool === 'transfer'/)
  assert.match(wallet, /realNameVerified/)
  assert.match(transfer, /adultVerified/)
  assert.match(transfer, /转账金额只能为整数/)
  assert.match(transfer, /type="number" min="1" step="1"/)
  assert.match(transfer, /maskedLoginAccount/)
  assert.match(transfer, /memberNo/)
  assert.doesNotMatch(transfer, /\{\{ transferForm\.recipientPhone \}\}/)
  assert.match(team, /完成首单后开通业绩查询/)
  assert.match(team, /当前账号尚未开通代理身份/)
  assert.match(team, /总业绩/)
  assert.match(team, /currentMonthTeamPerformance/)
  assert.doesNotMatch(team, /团队分层业绩|level1Performance|level2Performance|level3Performance/)
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
  assert.match(source, /type="text" inputmode="decimal"/)
  assert.match(source, /提现金额只能填写普通数字/)
})

test('frontend retains configured service rules without forcing FAQ into the footer', async () => {
  const legal = await readView('LegalView.vue')
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')
  assert.match(legal, /type === 'faq'/)
  assert.match(legal, /JSON\.parse\(config\.value\.faqs/)
  assert.doesNotMatch(app, /to="\/legal\/faq"/)
  assert.match(app, /to="\/legal\/after-sale"/)
  assert.match(legal, /replaceTenantPlaceholders/)
})

test('legal pages replace every customer-specific placeholder from shop profile data', async () => {
  const source = await readView('LegalView.vue')
  for (const field of [
    'companyName',
    'brandName',
    'unifiedSocialCreditCode',
    'companyAddress',
    'servicePhone',
    'serviceEmail',
    'serviceHours',
    'thirdPartyServices',
  ]) {
    assert.match(source, new RegExp(field))
  }
  assert.match(source, /信用代码/)
  assert.match(source, /客服时间/)
})

test('build freshness guard can identify the current production entry', () => {
  assert.equal(
    extractModuleEntry('<script type="module" crossorigin src="/assets/index-new.js"></script>'),
    '/assets/index-new.js',
  )
})

test('cart deletion requires confirmation and checkout navigates after limit validation', async () => {
  const source = await readView('CartView.vue')
  assert.match(source, /@click="requestRemoveSelected"/)
  assert.match(source, /@click="requestCheckoutSelected"/)
  assert.match(source, /@click="requestCheckoutAll"/)
  assert.match(source, /确认删除选中商品/)
  assert.match(source, /await validateCheckoutItems\(rows\)[\s\S]*checkoutSelected\(\)/)
  assert.match(source, /await validateCheckoutItems\(items\)[\s\S]*checkoutAll\(\)/)
  assert.doesNotMatch(source, /确认进入结算/)
  assert.match(source, /confirmPendingAction/)
})

test('merchant products show their seller and mixed merchants use one parent checkout', async () => {
  const store = await readFile(new URL('../src/store/cart.js', import.meta.url), 'utf8')
  const cart = await readView('CartView.vue')
  const checkout = await readView('CheckoutView.vue')
  const detail = await readView('ProductDetailView.vue')
  assert.match(store, /merchantName: product\.merchantName \|\| ''/)
  assert.doesNotMatch(cart, /不同商户或平台自营商品请分开结算/)
  assert.match(cart, /item\.merchantName \|\| '平台自营'/)
  assert.match(checkout, /一次支付/)
  assert.match(checkout, /res\.data\.checkoutId \|\| res\.data\.order\.id/)
  assert.match(detail, /product\.merchantName \|\| '平台自营'/)
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

test('order detail describes only locally known delivery facts without inventing carrier progress', async () => {
  const source = await readView('OrderDetailView.vue')
  assert.match(source, /logistics-overview-panel/)
  assert.match(source, /courierInitial/)
  assert.match(source, /logisticsStatus/)
  assert.match(source, /已确认收货/)
  assert.match(source, /实际轨迹以承运商查询为准/)
  assert.match(source, /查询承运商/)
  assert.doesNotMatch(source, /运输中/)
  assert.doesNotMatch(source, /包裹正在运输/)
  assert.ok(source.indexOf('logistics-overview-panel') < source.indexOf('product-detail-head'))
  assert.match(source, /getOrderTracking/)
  assert.match(source, /trackingFor\(shipment\)\.events/)
  assert.match(source, /aria-label="真实物流轨迹"/)
})

test('fulfilled orders show auto-receipt protection, logistics after-sale reasons and per-package actions', async () => {
  const detail = await readView('OrderDetailView.vue')
  const orders = await readView('OrdersView.vue')
  assert.match(detail, /detail\.autoReceiveEnabled/)
  assert.match(detail, /自动确认收货/)
  assert.match(detail, /处理中不会自动确认收货/)
  assert.match(detail, /物流停滞 \/ 未收到货/)
  assert.match(detail, /拒收 \/ 退回商家/)
  assert.match(detail, /startLogisticsAfterSale/)
  assert.match(detail, /shipment\.shipmentQuantity/)
  assert.match(detail, /:href="trackingUrl\(shipment\)"/)
  assert.match(orders, /item\.autoReceiveEnabled/)
  assert.match(orders, /未收到 \/ 拒收/)
})

test('order detail does not display a generic after-sale deadline notice', async () => {
  const source = await readView('OrderDetailView.vue')
  assert.doesNotMatch(source, /售后入口期限/)
  assert.doesNotMatch(source, /具体截止时间以订单状态和商城规则为准/)
  assert.doesNotMatch(source, /afterSaleWindowLabel/)
  assert.match(source, /canApplyAfterSale/)
  assert.match(source, /afterSaleDeadline/)
})

test('refund flow defaults to all items and keeps the application form concise', async () => {
  const source = await readView('OrderDetailView.vue')
  assert.match(source, /after-sale-type-grid/)
  assert.match(source, /reason-sheet/)
  assert.match(source, /setRefundQuantity/)
  assert.match(source, /selectAllRefundableItems/)
  assert.match(source, /已默认全选/)
  assert.match(source, /aside v-if="!applyingAfterSale"/)
  assert.doesNotMatch(source, /退款金额以实际支付金额和审核结果为准/)
  assert.doesNotMatch(source, /class="estimate-meta"/)
  assert.match(source, /预计退款/)
  assert.doesNotMatch(source, /class="order-number-footer"/)
  assert.doesNotMatch(source, /<h3>申请退款 \/ 售后<\/h3>/)
  assert.match(source, /RouterLink v-if="!applyingAfterSale"/)
  assert.match(source, /reason: \[selectedReason\.value, afterSaleForm\.value\.reasonDetail\.trim\(\)\]/)
  assert.match(source, /选择商品和数量/)
  assert.match(source, /class="required-star"/)
  assert.match(source, /退款商品数量不能为 0，请至少选择 1 件商品/)
  assert.match(source, /scrollIntoView\(\{ behavior: 'smooth', block: 'center' \}\)/)
  assert.match(source, /after-sale-field-error/)
  assert.match(source, /uploadAfterSaleProof\(order\.value\.id, proof\.file\)/)
  assert.match(source, /最多6张，单张不超过5MB/)
  assert.match(source, /const proofFilenames = \[\]/)
  assert.match(source, /for \(const proof of proofUploads\.value\)/)
  assert.match(source, /JSON\.stringify\(proofFilenames\)/)
  assert.match(source, /memberProofUrl/)
})

test('order detail keeps four payment facts visible and collapses five order information fields', async () => {
  const source = await readView('OrderDetailView.vue')
  assert.doesNotMatch(source, /<h3>金额<\/h3>/)
  assert.match(source, /订单状态/)
  assert.match(source, /商品金额/)
  assert.match(source, /实付金额/)
  assert.match(source, /支付方式/)
  assert.match(source, /class="order-info-toggle"/)
  assert.match(source, /orderInfoExpanded/)
  assert.match(source, /订单信息 <small>共5项<\/small>/)
  assert.match(source, /订单号/)
  assert.match(source, /创建时间/)
  assert.match(source, /付款时间/)
  assert.match(source, /发货时间/)
  assert.match(source, /<span>运费<\/span>/)
})

test('home alone keeps customer rules and filing access without adding a footer to profile', async () => {
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.match(app, /<footer v-if="isHome"/)
  assert.doesNotMatch(app, /<footer v-if="!isAuthPage"/)
  assert.doesNotMatch(app, /to="\/legal\/faq">常见问题/)
  assert.match(app, /to="\/legal\/after-sale">交易与售后/)
  assert.match(app, /to="\/legal\/contact">联系客服/)
  assert.match(app, /to="\/legal\/agreement">用户协议/)
  assert.match(app, /to="\/legal\/privacy">隐私政策/)
  assert.match(app, /beian\.miit\.gov\.cn/)
})

test('profile fits its actions into short mobile viewports', async () => {
  const profile = await readView('ProfileView.vue')

  assert.match(profile, /class="profile-actions"/)
  assert.match(profile, />退出当前账号</)
  assert.match(profile, /@media \(max-width:560px\) and \(max-height:700px\)/)
  assert.match(profile, /\.profile-actions \{ margin-top:auto; \}/)
})

test('order queries retry one transient mobile network failure', async () => {
  const request = await readFile(new URL('../src/api/request.js', import.meta.url), 'utf8')
  const requestErrors = await readFile(new URL('../src/utils/requestErrors.js', import.meta.url), 'utf8')
  assert.match(request, /RETRYABLE_METHODS = new Set\(\['get', 'head', 'options'\]\)/)
  assert.match(request, /retryCount < 1[\s\S]{0,100}isTransientTransportError\(error\)/)
  assert.match(request, /return service\.request\(config\)/)
  assert.match(requestErrors, /网络暂时不可用，请检查网络后重试/)
})

test('storefront session uses an HttpOnly cookie instead of persisting a new bearer token', async () => {
  const request = await readFile(new URL('../src/api/request.js', import.meta.url), 'utf8')
  const session = await readFile(new URL('../src/utils/shopSession.js', import.meta.url), 'utf8')

  assert.match(request, /withCredentials: true/)
  assert.match(request, /X-Shop-Client.*storefront/)
  assert.match(request, /authPath === '\/shop\/auth\/me'/)
  assert.doesNotMatch(session, /localStorage\.setItem\(LEGACY_TOKEN_KEY/)
  assert.match(session, /localStorage\.removeItem\(LEGACY_TOKEN_KEY\)/)
})

test('SMS login uses the server-fixed endpoint while registration and reset retain captcha proof', async () => {
  const api = await readFile(new URL('../src/api/shop.js', import.meta.url), 'utf8')
  const login = await readView('LoginView.vue')
  const forgot = await readView('ForgotPasswordView.vue')
  const home = await readView('HomeView.vue')

  assert.match(api, /captchaId: captcha\.captchaId/)
  assert.match(api, /captchaCode: captcha\.captchaCode/)
  assert.match(api, /export function sendLoginSmsCode\(phone\)[\s\S]*url: '\/sms\/send\/login'/)
  assert.match(login, /sendLoginSmsCode\(smsForm\.value\.phone\)/)
  assert.doesNotMatch(login, /id="sms-login-captcha"/)
  assert.match(login, /sendSmsCode\(registerForm\.value\.phone, 1, loginForm\.value\)/)
  assert.match(forgot, /captchaId: captchaId\.value, captchaCode: captchaCode\.value/)
  assert.match(home, /window\.open\(banner\.linkValue, '_blank', 'noopener,noreferrer'\)/)
})

test('verified SMS login for an unregistered phone gives a direct registration path', async () => {
  const login = await readView('LoginView.vue')

  assert.match(login, /该手机号尚未注册，请先注册账号/)
  assert.match(login, /class="sms-register-notice"/)
  assert.match(login, /@click="startRegistrationFromSms">去注册</)
  assert.match(login, /registerForm\.value\.phone = normalizeMainlandPhone\(phone\)/)
  assert.match(login, /手机号\.\*\(\?:尚未注册\|未注册\)/)
})
