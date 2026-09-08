import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'
import { resolveQuickCartItem } from '../../mall-shop-web/src/utils/quickCart.js'
import { sanitizePositiveIntegerInput, resolvePositiveIntegerQuantity } from '../../mall-shop-web/src/utils/quantityInput.js'
import { parseChineseAddress as h5Address } from '../../mall-shop-web/src/utils/addressParser.js'
const require = createRequire(import.meta.url)
const quantity = require('../utils/quantity'), nativeAddress = require('../utils/address-parser')
const plain = value => JSON.parse(JSON.stringify(value))
const event = (field, value) => ({ currentTarget: { dataset: { [field]: value } } })
const tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const detail = { product: { id: '7', productName: '测试商品', status: 1, stock: 500, salePrice: 12, purchaseLimit: 0 }, skus: [{ id: '70', stock: 0, salePrice: 1 }, { id: '71', stock: 500, salePrice: 0, skuName: '免费规格' }, { id: '72', stock: 500, salePrice: 20 }] }
const respondGoods = ({ url }) => url.endsWith('/purchase-limit/check') ? { allowed: true } : detail

test('逐例调用H5与原生数量解析：全角、空值、负数、小数、指数及100件以上一致', () => {
  for (const value of ['', '０１２', '  00100', '1e3', '12.5', '-12', 'ab12', '99', '100', '１２３４', '999999999999', null, undefined]) {
    assert.equal(quantity.sanitize(value), sanitizePositiveIntegerInput(value))
    for (const max of [1, 2, 99, 100, 500, 2147483647]) assert.equal(quantity.resolve(value, max), resolvePositiveIntegerQuantity(value, max))
  }
  assert.equal(quantity.maximum(500, 0), 500)
  assert.equal(quantity.maximum(500, 3), 3)
  assert.equal(quantity.valid(2147483648), false)
})

test('首页/分类直接加购：与H5同选第一个有库存SKU，保留零价，不再要求第二次选规格', async () => {
  for (const name of ['home', 'category']) {
    const env = commerceEnv(respondGoods), page = env.page(name), expected = resolveQuickCartItem(detail.product, detail)
    await page.quickAdd(event('id', '7'))
    const item = env.load('utils/cart').list()[0]
    assert.equal(item.skuId, expected.skuId); assert.equal(item.salePrice, expected.salePrice); assert.equal(item.quantity, 1)
    assert.equal(env.calls.filter(call => call.url.endsWith('/purchase-limit/check')).length, 1)
    assert.equal(env.tabHidden, false)
    assert.equal(page.data.quickCartVisible, undefined)
  }
})

test('SKU全部缺货时H5返回不可加购、原生不写购物车；下架规格不能被误选', async () => {
  const empty = { ...detail, skus: detail.skus.map(item => ({ ...item, stock: 0 })) }
  assert.equal(resolveQuickCartItem(detail.product, empty), null)
  const env = commerceEnv(() => empty); await env.page('category').quickAdd(event('id', '7'))
  assert.equal(env.load('utils/cart').list().length, 0)
  assert.match(env.notices.join(), /售罄|库存|缺货/)
})

test('原生不额外限制99件，库存和不同SKU累计限购仍拦截', async () => {
  const env = commerceEnv(respondGoods), cart = env.load('utils/cart'), limits = env.load('utils/purchase-limit')
  const selection = await limits.checkAddition('7', '71', 100); cart.add(selection.item)
  assert.equal(cart.count(), 100)
  await assert.rejects(limits.checkAddition('7', '71', 401), /上限/)
  await assert.rejects(limits.checkAddition('7', '72', 1, { detail: { ...detail, product: { ...detail.product, purchaseLimit: 100 } } }), /限购/)
  assert.equal(cart.count(), 100)
})

test('历史订单限购拒绝、限购接口故障、换号晚响应均不得写入新账号购物车', async () => {
  for (const result of [{ allowed: false, message: '历史订单已达限购上限' }, null]) {
    const env = commerceEnv(({ url }) => url.endsWith('/check') ? result : detail)
    await env.page('category').quickAdd(event('id', '7'))
    assert.equal(env.load('utils/cart').count(), 0)
  }
  const pending = deferred(), env = commerceEnv(({ url }) => url.endsWith('/check') ? pending.promise : detail)
  const task = env.page('home').quickAdd(event('id', '7')); await tick(); env.token('other'); pending.resolve({ allowed: true }); await task
  assert.equal(env.load('utils/cart').count(), 0)
})

test('购物车按H5默认全选，进入管理清空选择，批删先确认，退出管理恢复全量结算', async () => {
  const env = commerceEnv(respondGoods), cart = env.load('utils/cart'), page = env.page('cart')
  cart.add({ productId: '7', skuId: '71', quantity: 2, salePrice: 0 }); cart.add({ productId: '7', skuId: '72', quantity: 1, salePrice: 20 })
  page.onShow(); await tick(); assert.equal(page.data.count, 3)
  page.toggleManage(); await tick(); assert.equal(page.data.count, 0); assert.equal(page.data.manageMode, true)
  page.toggle({ currentTarget: { dataset: { key: '7:71' } }, detail: { value: ['selected'] } }); await tick()
  page.removeSelected(); await tick(); assert.equal(cart.list().length, 1); assert.equal(cart.list()[0].skuId, '72')
  assert.match(env.notices.join(), /删除选中的 1 种商品，共 2 件/)
  page.toggleManage(); await tick(); assert.equal(page.data.count, 1); assert.equal(page.data.manageMode, false)
})

test('清空确认取消或弹窗期间换号不清购物车；正常退出仅清本账号，过期保留', async () => {
  const env = commerceEnv(respondGoods), cart = env.load('utils/cart'), page = env.page('cart'), session = env.load('utils/session')
  cart.add({ productId: '7', skuId: '71', quantity: 2, salePrice: 0 }); page.toggleManage(); await tick()
  let modal; env.wx.showModal = value => { modal = value }; page.clearCart(); modal.success({ confirm: false }); assert.equal(cart.count(), 2)
  page.clearCart(); env.token('other'); modal.success({ confirm: true }); assert.equal(cart.count(), 2)
  session.clearSession(); env.token('member'); env.storage.set('mall_mini_member', { id: '1' }); assert.equal(cart.count(), 2)
  env.storage.set('mall_mini_member', { id: '1' }); session.clearSession({ clearCart: true }); assert.equal(env.storage.has('mall_mini_cart_v2:member:1'), false)
})

test('地址识别与H5同源且逐例结果相同；只处理主动粘贴，不读取剪贴板', async () => {
  for (const input of ['', '张三 13800000000 湖南省长沙市岳麓区测试路1号', '收件人：李四 电话：13900000000 收货地址：北京市朝阳区测试路2号', '王五 13700000000 广西南宁市青秀区测试街3号', '赵六 13600000000 测试路4号']) assert.deepEqual(nativeAddress.parseChineseAddress(input), h5Address(input))
  const native = readFileSync(new URL('../utils/address-parser.js', import.meta.url), 'utf8')
  const h5 = readFileSync(new URL('../../mall-shop-web/src/utils/addressParser.js', import.meta.url), 'utf8')
  assert.equal(native.replace('// Generated from H5; run scripts/sync-address-parser.mjs.\n', '').replace("const pcaTextArr = require('./address-regions')", "import { pcaTextArr } from 'element-china-area-data'").replace('function parseChineseAddress(', 'export function parseChineseAddress(').replace('\nmodule.exports = { parseChineseAddress }\n', ''), h5)
  const env = commerceEnv(() => []), page = env.page('address')
  page.setData({ loading: false }); page.startAdd(); page.pasteInput({ detail: { value: '张三 13800000000 湖南省长沙市岳麓区测试路1号' } }); await page.recognizeAddress()
  assert.equal(page.data.form.receiverName, '张三'); assert.equal(env.calls.length, 0)
  assert.deepEqual(page.data.form.region, ['湖南省', '长沙市', '岳麓区'])
})

test('管理地址点击行不改变默认；仅显式设为默认才提交同一地址', async () => {
  const row = { id: '9', receiverName: '张三', receiverPhone: '13800000000', province: '湖南省', city: '长沙市', district: '岳麓区', detailAddress: '测试路1号', isDefault: 0 }
  const env = commerceEnv(({ method }) => method === 'POST' ? row : [{ ...row, isDefault: 1 }]), page = env.page('address')
  page.setData({ rows: [row], loading: false }); await page.choose(event('id', '9')); assert.equal(env.calls.length, 0)
  await page.makeDefault(event('id', '9')); assert.equal(env.calls[0].method, 'POST'); assert.equal(env.calls[0].data.id, '9'); assert.equal(env.calls[0].data.isDefault, 1)
})

const reviewSummary = { canReview: true, reviewCount: 1, averageRating: 5, star5Count: 1, page: { list: [{ id: '1', reviewerName: '张*', content: '很好', rating: 5 }], total: 2 } }
test('分类排序按钮不被后置等宽零宽样式覆盖，隐私说明覆盖新增直播互动统计', () => {
  const style = readFileSync(new URL('../pages/category/index.wxss', import.meta.url), 'utf8')
  assert.match(style, /\.result-toolbar \.sort-tabs \.sort-tab\s*\{[^}]*flex:none;[^}]*width:auto;/)
  const legal = commerceEnv().load('utils/legal').miniPrivacy
  for (const purpose of ['支付密码', '实名认证授权', '直播预约记录', '评论内容', '随机访客标识', '可能同时识别当前账号']) assert.ok(legal.includes(purpose), purpose)
})
test('评价沿用H5接口与指定订单项；缺资格不打开表单，失败保留输入，成功才清空', async () => {
  let fail = true, allowed = false
  const env = commerceEnv(({ method }) => { if (method === 'POST') { if (fail) throw new Error('稍后重试'); return {} } return { ...reviewSummary, canReview: allowed } })
  const page = env.page('product'); page.productId = '7'; page.reviewOrderItemId = '700'
  await page.openReviewForm(); assert.equal(page.data.reviewFormVisible, false)
  allowed = true; await page.openReviewForm(); assert.equal(page.data.reviewFormVisible, true)
  page.reviewInput({ detail: { value: '测试商品评价' } }); page.chooseRating(event('rating', 4)); await page.submitReview()
  assert.equal(page.data.reviewContent, '测试商品评价')
  fail = false; await page.submitReview(); assert.equal(page.data.reviewContent, '')
  const post = env.calls.find(item => item.method === 'POST'); assert.deepEqual(post.data, { rating: 4, content: '测试商品评价', orderItemId: '700' })
  assert.ok(env.calls.filter(item => item.method !== 'POST').every(item => item.params.orderItemId === '700'))
})

test('评价翻页失败不跳页，离开或切号的评价响应不回写', async () => {
  let fail = true
  const env = commerceEnv(({ params }) => { if (params.pageNum === 2 && fail) throw new Error('分页失败'); return reviewSummary }), page = env.page('product'); page.productId = '7'
  await page.loadReviews(); await page.loadMoreReviews(); assert.equal(page.data.reviewPage, 1); assert.equal(page.data.reviews.length, 1)
  fail = false; await page.loadMoreReviews(); assert.equal(page.data.reviewPage, 2)
  const pending = deferred(), late = commerceEnv(() => pending.promise), other = late.page('product'); other.productId = '7'
  const task = other.loadReviews(); other.onHide(); late.token('other'); pending.resolve(reviewSummary); await task; assert.equal(other.data.reviews.length, 0)
})

test('订单待评价目标含商品ID与订单项ID，客服消息直达真实工单而非联系说明', () => {
  const env = commerceEnv(), page = env.page('orders')
  page.setData({ rows: [{ order: { id: '9' }, pendingReviewProductId: '7', pendingReviewOrderItemId: '700' }] }); page.review(event('id', '9'))
  assert.equal(env.routes[0], '/pages/product/index?id=7&orderItemId=700')
  const message = env.page('message-detail'); message.owner = 'member'; message.setData({ message: { targetType: 'SERVICE_TICKET', targetId: '99' } }); message.openTarget()
  assert.equal(env.routes[1], '/pages/support-detail/index?id=99')
})

test('售后原因与H5相同，物流原因切仅退款，补充说明拼入原字段', async () => {
  const env = commerceEnv(() => ({})), page = env.page('after-sale')
  page.selectReason({ detail: { value: 6 } }); assert.equal(page.data.reason, '物流停滞 / 未收到货'); assert.equal(page.data.applyType, 1)
  page.reasonDetailInput({ detail: { value: '十天没有物流更新' } }); page.setData({ allowed: true, orderId: '9', items: [{ id: '90', selectedQuantity: 1 }] })
  await page.submit(); assert.equal(env.calls[0].data.reason, '物流停滞 / 未收到货：十天没有物流更新')
})
