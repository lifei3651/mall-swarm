import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolveQuickCartItem } from '../src/utils/quickCart.js'
import { extractModuleEntry } from '../src/utils/buildFreshness.js'
import { normalizeLoginAccountInput, resolveRegistrationErrorField, validateLoginAccount } from '../src/utils/loginAccount.js'
import { normalizeNicknameInput, validateNickname } from '../src/utils/nickname.js'
import { readDisplayExtraConfig, resolveDisplayColors, resolveHomeModules } from '../src/utils/displayConfig.js'

const readView = (name) => readFile(new URL(`../src/views/${name}`, import.meta.url), 'utf8')
const readStyles = () => readFile(new URL('../src/assets/styles.css', import.meta.url), 'utf8')

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

test('mobile interactive areas suppress the browser tap highlight without disabling form input', async () => {
  const styles = await readStyles()
  const login = await readView('LoginView.vue')

  assert.match(styles, /input\[type="checkbox"\][\s\S]*-webkit-tap-highlight-color: transparent/)
  assert.match(styles, /\[role="button"\][\s\S]*user-select: none/)
  assert.doesNotMatch(styles, /input,\s*textarea[\s\S]{0,120}user-select: none/)
  assert.match(login, /\.agreement-check \{[^}]*-webkit-tap-highlight-color: transparent;[^}]*user-select: none;/)
})

test('registration page does not expose internal membership activation copy', async () => {
  const login = await readView('LoginView.vue')

  assert.doesNotMatch(login, /注册后是商城用户/)
  assert.doesNotMatch(login, /完成首笔有效支付订单后正式成为会员/)
  assert.match(login, /<p v-if="mode === 'login'">登录后可管理地址、订单和售后。<\/p>/)
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
  assert.match(login, /<label>登录账号/)
  assert.match(login, /showRegisterServerError/)
  assert.match(login, /resolveRegistrationErrorField\(text\)/)
  assert.match(login, /class="form-item">\s*<label>登录密码/)

  assert.match(app, /const isAuthPage = computed/)
  assert.match(app, /<footer v-if="isHome"/)
  assert.match(app, /!isCheckout\.value && !isAuthPage\.value/)
  assert.match(app, /watch\(\(\) => route\.fullPath[\s\S]*authPrompt\.value = ''/)
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
  assert.doesNotMatch(address, /已自动填入，请核对后保存/)
  assert.match(address, /删除收货地址？/)
  assert.match(address, /confirm-overlay/)
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

test('checkout only exposes configured payment channels', async () => {
  const source = await readView('CheckoutView.vue')
  assert.match(source, /当前已开通余额支付；微信支付、支付宝通道完成商户配置后会自动显示。/)
  assert.match(source, /payType: 'BALANCE'/)
  assert.doesNotMatch(source, /value: 'WECHAT'/)
  assert.match(source, /payConfig\.value\.alipayEnabled/)
  assert.match(source, /getPayConfig\(\)/)
})

test('customers can cancel only pending after-sale applications', async () => {
  const api = await readFile(new URL('../src/api/shop.js', import.meta.url), 'utf8')
  const source = await readView('OrderDetailView.vue')
  assert.match(api, /url: `\/shop\/after-sales\/\$\{id\}\/cancel`/)
  assert.match(source, /取消申请/)
  assert.match(source, /物流公司：\{\{ sale\.returnDeliveryCompany/)
  assert.match(source, /查看物流轨迹/)
  assert.match(source, /sale\.status === 0/)
  assert.match(source, /不会产生退款，仍可在售后期限内重新申请/)
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

test('order detail prioritizes a compact logistics summary and signed status', async () => {
  const source = await readView('OrderDetailView.vue')
  assert.match(source, /logistics-overview-panel/)
  assert.match(source, /courierInitial/)
  assert.match(source, /logisticsStatus/)
  assert.match(source, /已签收/)
  assert.ok(source.indexOf('logistics-overview-panel') < source.indexOf('product-detail-head'))
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
  assert.match(request, /RETRYABLE_METHODS = new Set\(\['get', 'head', 'options'\]\)/)
  assert.match(request, /retryCount < 1 && isTransientTransportError\(error\)/)
  assert.match(request, /return service\.request\(config\)/)
  assert.match(request, /网络暂时不可用，请检查网络后重试/)
})
