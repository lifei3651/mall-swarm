import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const invite = require('../utils/invite')
const { buildQuery } = require('../utils/request')
const payment = require('../utils/payment')
const format = require('../utils/format')
const orderCenter = require('../utils/order-center')

test('邀请码只接受八位字母数字并统一大写', () => {
  assert.equal(invite.normalizeInviteCode('ab12cd34'), 'AB12CD34')
  assert.equal(invite.normalizeInviteCode('bad-code'), '')
  assert.equal(invite.normalizeInviteCode('<script>'), '')
})

test('二维码 scene 可以承载直接邀请码或查询参数', () => {
  assert.equal(invite.decodeScene('ab12cd34'), 'AB12CD34')
  assert.equal(invite.decodeScene(encodeURIComponent('inviteCode=ZX12CV34')), 'ZX12CV34')
})

test('请求查询参数过滤空值并编码', () => {
  assert.equal(buildQuery({ keyword: '护肤 套装', page: 1, empty: '' }), 'keyword=%E6%8A%A4%E8%82%A4%20%E5%A5%97%E8%A3%85&page=1')
})

test('站内媒体相对地址转换为当前商城的完整HTTPS地址', () => {
  assert.equal(
    format.mediaUrl('/api/shop/media/images/abc.png'),
    'https://lingqimall.com/api/shop/media/images/abc.png'
  )
  assert.equal(
    format.mediaUrl('/shop/media/images/abc.png'),
    'https://lingqimall.com/api/shop/media/images/abc.png'
  )
  assert.equal(format.mediaUrl('https://images.example.com/abc.png'), 'https://images.example.com/abc.png')
  assert.equal(format.mediaUrl('/assets/local.png'), '/assets/local.png')
})

test('微信支付参数只接受服务端签发的完整字段并映射package', () => {
  assert.deepEqual(payment.normalizeParameters({
    timeStamp: '1788060000', nonceStr: 'nonce', packageValue: 'prepay_id=wx123',
    signType: 'RSA', paySign: 'signature'
  }), {
    timeStamp: '1788060000', nonceStr: 'nonce', package: 'prepay_id=wx123',
    signType: 'RSA', paySign: 'signature'
  })
  assert.throws(() => payment.normalizeParameters({ timeStamp: '1' }), /参数不完整/)
})

test('微信购物订单只接受安全商户单号并生成固定详情路径', () => {
  assert.equal(orderCenter.normalizePaymentNo(' T0000000000001 '), 'T0000000000001')
  assert.equal(orderCenter.normalizePaymentNo('../other-order'), '')
  assert.equal(orderCenter.detailPath('T0000000000001'), '/pages/order-detail/index?orderNo=T0000000000001')
})

test('用户取消微信支付与真实支付错误分开处理', () => {
  assert.equal(payment.isUserCancel({ errMsg: 'requestPayment:fail cancel' }), true)
  assert.equal(payment.isUserCancel(new Error('商户配置错误')), false)
})

test('小程序结算保留服务端大额支付短信验证', async () => {
  const fs = await import('node:fs/promises')
  const checkout = await fs.readFile(new URL('../pages/checkout/index.js', import.meta.url), 'utf8')
  assert.match(checkout, /url: '\/payment\/checkVerify'/)
  assert.match(checkout, /url: '\/sms\/send\/payment'/)
  assert.match(checkout, /smsCode: this\.data\.needSmsVerify/)
})

test('微信收款确认仅从本人提现单取参数并在原生确认后再次服务端核验', async () => {
  const fs = await import('node:fs/promises')
  const app = JSON.parse(await fs.readFile(new URL('../app.json', import.meta.url), 'utf8'))
  const page = await fs.readFile(new URL('../pages/payout/index.js', import.meta.url), 'utf8')
  assert.ok(app.pages.includes('pages/payout/index'))
  assert.match(page, /wx\.canIUse\('requestMerchantTransfer'\)/)
  assert.match(page, /wx\.requestMerchantTransfer/)
  assert.match(page, /withdrawals\/\$\{withdrawId\}\/wechat-confirmation/)
  assert.equal((page.match(/await this\.prepare\(withdrawId\)/g) || []).length, 2)
})

test('小程序提供个人消息中心与用户主动触发的微信订阅授权', async () => {
  const fs = await import('node:fs/promises')
  const app = JSON.parse(await fs.readFile(new URL('../app.json', import.meta.url), 'utf8'))
  const profile = await fs.readFile(new URL('../pages/profile/index.wxml', import.meta.url), 'utf8')
  const messages = await fs.readFile(new URL('../pages/messages/index.js', import.meta.url), 'utf8')
  const subscriptions = await fs.readFile(new URL('../pages/subscriptions/index.js', import.meta.url), 'utf8')
  assert.ok(app.pages.includes('pages/messages/index'))
  assert.ok(app.pages.includes('pages/message-detail/index'))
  assert.ok(app.pages.includes('pages/subscriptions/index'))
  assert.match(profile, /消息与提醒/)
  assert.match(messages, /\/shop\/messages\/unread/)
  assert.match(messages, /\/shop\/messages\/read-all/)
  assert.match(subscriptions, /wx\.requestSubscribeMessage/)
  assert.match(subscriptions, /result\[id\] === 'accept'/)
  assert.match(subscriptions, /\/subscriptions\/grants/)
})

test('微信购物订单可以按商户单号直达本人订单详情', async () => {
  const fs = await import('node:fs/promises')
  const app = JSON.parse(await fs.readFile(new URL('../app.json', import.meta.url), 'utf8'))
  const page = await fs.readFile(new URL('../pages/order-detail/index.js', import.meta.url), 'utf8')
  assert.ok(app.pages.includes('pages/order-detail/index'))
  assert.match(page, /options\.orderNo \|\| options\.paymentNo/)
  assert.match(page, /\/shop\/orders\/payment-detail/)
  assert.match(page, /auth\.requireLogin\(this\.redirect\)/)
  assert.match(page, /\/shop\/orders\/\$\{orderId\}\/receive/)
})

test('WXML 条件兜底与列表循环使用独立节点，避免微信编译器拒绝', async () => {
  const fs = await import('node:fs/promises')
  for (const page of ['orders', 'payout']) {
    const view = await fs.readFile(new URL(`../pages/${page}/index.wxml`, import.meta.url), 'utf8')
    for (const tag of view.match(/<[^>]+>/g) || []) {
      assert.equal(/\bwx:(?:elif|else)\b/.test(tag) && /\bwx:for=/.test(tag), false)
    }
  }
})

test('登录完成后可以正确返回购物车等 tab 页面', async () => {
  const fs = await import('node:fs/promises')
  const page = await fs.readFile(new URL('../pages/login/index.js', import.meta.url), 'utf8')
  assert.match(page, /tabPages\.has\(pagePath\)/)
  assert.match(page, /wx\.switchTab\(\{ url: pagePath \}\)/)
})

test('商品详情按规格展示价格库存并提供立即购买', async () => {
  const fs = await import('node:fs/promises')
  const page = await fs.readFile(new URL('../pages/product/index.js', import.meta.url), 'utf8')
  const view = await fs.readFile(new URL('../pages/product/index.wxml', import.meta.url), 'utf8')
  assert.match(page, /cart\.selectOnly\(key\)/)
  assert.match(page, /stock: Math\.max/)
  assert.match(view, /立即购买/)
  assert.match(view, /class="stock-text"/)
})

test('购物车支持全选并在删除前确认', async () => {
  const fs = await import('node:fs/promises')
  const page = await fs.readFile(new URL('../pages/cart/index.js', import.meta.url), 'utf8')
  const view = await fs.readFile(new URL('../pages/cart/index.wxml', import.meta.url), 'utf8')
  assert.match(page, /cart\.selectAll/)
  assert.match(page, /title: '移除商品'/)
  assert.match(view, /全选/)
})

test('订单列表提供状态筛选、分页和订单详情入口', async () => {
  const fs = await import('node:fs/promises')
  const page = await fs.readFile(new URL('../pages/orders/index.js', import.meta.url), 'utf8')
  const view = await fs.readFile(new URL('../pages/orders/index.wxml', import.meta.url), 'utf8')
  assert.match(page, /orderState: tab\.state/)
  assert.match(page, /pages\/order-detail\/index\?id=/)
  assert.match(page, /this\.data\.rows\.concat\(rows\)/)
  assert.match(page, /退款\/售后/)
  assert.match(view, /class="order-tabs"/)
})

test('订单详情同时支持微信订单找回和站内订单入口', async () => {
  const fs = await import('node:fs/promises')
  const page = await fs.readFile(new URL('../pages/order-detail/index.js', import.meta.url), 'utf8')
  const view = await fs.readFile(new URL('../pages/order-detail/index.wxml', import.meta.url), 'utf8')
  assert.match(page, /options\.id/)
  assert.match(page, /url: `\/shop\/orders\/\$\{this\.orderId\}`/)
  assert.match(page, /after-sales\/\$\{id\}\/cancel/)
  assert.match(view, /退款 \/ 售后进度/)
})

test('个人中心只在待确认提现存在时显示收款入口并提供微信客服', async () => {
  const fs = await import('node:fs/promises')
  const page = await fs.readFile(new URL('../pages/profile/index.js', import.meta.url), 'utf8')
  const view = await fs.readFile(new URL('../pages/profile/index.wxml', import.meta.url), 'utf8')
  assert.match(page, /payoutCount/)
  assert.match(view, /wx:if="\{\{payoutCount\}\}"/)
  assert.match(view, /open-type="contact"/)
})

test('地址支持编辑且删除前需要二次确认', async () => {
  const fs = await import('node:fs/promises')
  const page = await fs.readFile(new URL('../pages/address/index.js', import.meta.url), 'utf8')
  const view = await fs.readFile(new URL('../pages/address/index.wxml', import.meta.url), 'utf8')
  assert.match(page, /edit\(event\)/)
  assert.match(page, /title: '删除收货地址'/)
  assert.match(view, /保存修改/)
  assert.match(view, /设为默认地址/)
})

test('小程序主导航使用真实图标且登录主操作保持全宽', async () => {
  const fs = await import('node:fs/promises')
  const app = JSON.parse(await fs.readFile(new URL('../app.json', import.meta.url), 'utf8'))
  for (const item of app.tabBar.list) {
    assert.match(item.iconPath, /^assets\/tabbar\/.+\.png$/)
    assert.match(item.selectedIconPath, /^assets\/tabbar\/.+-selected\.png$/)
    const normal = await fs.stat(new URL(`../${item.iconPath}`, import.meta.url))
    const selected = await fs.stat(new URL(`../${item.selectedIconPath}`, import.meta.url))
    assert.ok(normal.size > 100)
    assert.ok(selected.size > 100)
  }
  const loginStyle = await fs.readFile(new URL('../pages/login/index.wxss', import.meta.url), 'utf8')
  assert.match(loginStyle, /\.login-button\s*\{[^}]*width:\s*100%/s)
})

test('小程序第二轮视觉收口保持清晰的登录、订单、地址与结算层级', async () => {
  const fs = await import('node:fs/promises')
  const loginConfig = JSON.parse(await fs.readFile(new URL('../pages/login/index.json', import.meta.url), 'utf8'))
  const loginView = await fs.readFile(new URL('../pages/login/index.wxml', import.meta.url), 'utf8')
  const profilePage = await fs.readFile(new URL('../pages/profile/index.js', import.meta.url), 'utf8')
  const profileView = await fs.readFile(new URL('../pages/profile/index.wxml', import.meta.url), 'utf8')
  const addressPage = await fs.readFile(new URL('../pages/address/index.js', import.meta.url), 'utf8')
  const addressView = await fs.readFile(new URL('../pages/address/index.wxml', import.meta.url), 'utf8')
  const checkoutView = await fs.readFile(new URL('../pages/checkout/index.wxml', import.meta.url), 'utf8')
  assert.equal(loginConfig.navigationBarTitleText, '商城账号登录')
  assert.match(loginView, /checkbox-group bindchange="agreementChange"/)
  assert.ok(loginView.indexOf('我已阅读并同意') < loginView.indexOf('商城账号登录<\/button>'))
  assert.match(profilePage, /\/shop\/profile\/order-summary/)
  for (const tab of ['pending-payment', 'pending-shipment', 'pending-receipt', 'after-sale']) {
    assert.match(profileView, new RegExp(`data-tab="${tab}"`))
  }
  assert.match(addressPage, /showForm: false/)
  assert.match(addressPage, /startAdd\(\)/)
  assert.match(addressView, /wx:if="\{\{showForm\}\}" class="address-form"/)
  assert.match(checkoutView, /应付金额/)
  assert.match(checkoutView, /<radio checked color="#07c160"/)
})

test('分类页使用适合侧边栏宽度的单列商品卡片', async () => {
  const fs = await import('node:fs/promises')
  const categoryView = await fs.readFile(new URL('../pages/category/index.wxml', import.meta.url), 'utf8')
  const categoryStyle = await fs.readFile(new URL('../pages/category/index.wxss', import.meta.url), 'utf8')
  assert.match(categoryView, /class="category-product-list"/)
  assert.match(categoryView, /class="category-product-card"/)
  assert.match(categoryView, /item\.subtitle/)
  assert.doesNotMatch(categoryView, /class="product-grid compact-grid"/)
  assert.match(categoryStyle, /\.category-product-card\s*\{[^}]*display:\s*flex;/s)
})
