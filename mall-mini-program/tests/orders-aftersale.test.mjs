import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const policy = require('../pages/order-detail/policy.js')
const ID = '9212345678901234567'
const ITEM = '9212345678901234568'
const SALE = '9212345678901234569'
const plain = (value) => JSON.parse(JSON.stringify(value))
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const detail = (patch = {}) => ({ order: { id: ID, orderNo: 'SO-test', status: 2, payAmount: '20.00', createTime: new Date().toISOString() },
  items: [{ id: ITEM, quantity: 2, productName: '测试商品' }], afterSales: [], afterSaleWindowMode: 'RECEIVED', ...patch })
const list = (id = ID) => ({ list: [detail({ order: { id, status: 0, payType: 'WECHAT', payAmount: 0, totalAmount: 20 } })], total: 1, pageNum: 1 })
const event = (data) => ({ currentTarget: { dataset: data } })

function harness(name, { respond = async () => ({}), loggedIn = true, payOrder = async () => true, upload, choose } = {}) {
  let definition, cleared = 0
  const calls = [], routes = [], modals = [], toasts = [], uploads = []
  const auth = { requireLogin: (redirect) => { if (!loggedIn) routes.push(`login:${redirect}`); return loggedIn } }
  const dependencies = {
    '../../utils/request': async (options) => { calls.push(plain(options)); return respond(options) },
    '../../utils/auth': auth,
    '../../utils/session': { getToken: () => loggedIn ? 'test-token' : '', clearSession() { cleared++; loggedIn = false } },
    '../../config/runtime': { API_BASE_URL: 'https://example.test/api' },
    '../../utils/format': { mediaUrl: (url) => url || '', money: (value) => Number(value || 0).toFixed(2) },
    '../../utils/payment': { payOrder, isUserCancel: (error) => /cancel/i.test(error.message || '') },
    '../../utils/order-center': { normalizePaymentNo: (value) => /^[A-Za-z0-9_-]{6,64}$/.test(value || '') ? value : '', detailPath: (value) => `/pages/order-detail/index?orderNo=${value}` },
    '../../utils/theme': { pageData: () => ({}), apply() {}, sync() {} },
    './policy': policy,
    '../order-detail/policy': policy
  }
  vm.runInNewContext(readFileSync(new URL(`../pages/${name}/index.js`, import.meta.url), 'utf8'), {
    Page: (value) => { definition = value }, require: (key) => {
      assert.ok(Object.hasOwn(dependencies, key), key); return dependencies[key]
    },
    wx: { navigateTo: ({ url }) => routes.push(url), redirectTo: ({ url }) => routes.push(url), switchTab: ({ url }) => routes.push(url),
      showModal: (value) => modals.push(value), showToast: (value) => toasts.push(value), showLoading() {}, hideLoading() {}, stopPullDownRefresh() {},
      chooseMedia: choose,
      uploadFile: (options) => { uploads.push(options); if (upload) upload(options) }
    }
  })
  const page = { ...definition, data: plain(definition.data), setData(patch, callback) { Object.assign(this.data, plain(patch)); if (callback) callback() } }
  return { page, calls, routes, modals, toasts, uploads, setLogin: (value) => { loggedIn = value }, cleared: () => cleared }
}

test('订单ID全程保留安全字符串，拒绝已经失真的Number和非法路径', () => {
  assert.equal(policy.identifier(ID), ID)
  assert.equal(policy.identifier(123), '123')
  for (const value of [Number(ID), '1/receive', '-2', '0', '', null, Infinity]) assert.equal(policy.identifier(value), '')
  const h = harness('orders')
  h.page.openDetail(event({ id: ID }))
  h.page.openDetail(event({ id: Number(ID) }))
  assert.deepEqual(h.routes, [`/pages/order-detail/index?id=${ID}`])
})

test('订单未登录进入后返回页面会重新初始化，同时保留筛选条件', async () => {
  const h = harness('orders', { loggedIn: false, respond: ({ url }) => url === '/shop/orders' ? list() : {} })
  h.page.onLoad({ tab: 'pending-payment' })
  await h.page.onShow()
  assert.deepEqual(h.calls, [])
  assert.equal(h.routes[0], 'login:/pages/orders/index?tab=pending-payment')
  h.setLogin(true)
  await h.page.onShow()
  assert.equal(h.page.data.rows[0].order.id, ID)
  assert.equal(h.page.data.rows[0].amountText, '0.00', '零元实付不回退商品原价')
  assert.equal(h.calls.find((call) => call.url === '/shop/orders').params.orderState, 'PENDING_PAYMENT')
})

test('快速切换筛选忽略旧列表响应，旧请求不能提前关闭新请求加载状态', async () => {
  const old = deferred(), next = deferred()
  const h = harness('orders', { respond: ({ params }) => params.orderState ? next.promise : old.promise })
  const first = h.page.load(true)
  h.page.selectTab(event({ key: 'pending-receipt' }))
  old.resolve(list('101'))
  await first
  assert.equal(h.page.data.loading, true)
  assert.equal(h.page.data.rows.length, 0)
  next.resolve(list('102'))
  await new Promise((resolve) => setImmediate(resolve))
  assert.equal(h.page.data.rows[0].order.id, '102')
  assert.equal(h.page.data.loading, false)
})

test('加载更多尚未返回时重置可发新请求，旧分页结果不会追加到新列表', async () => {
  const more = deferred()
  const h = harness('orders', { respond: ({ params }) => params.pageNum === 2 ? more.promise : list('103') })
  h.page.setData({ loading: false, pageNum: 1, rows: [{ key: '100' }], total: 30 })
  const old = h.page.load(false)
  await h.page.load(true)
  more.resolve({ list: [detail({ order: { id: '104' } })], total: 30, pageNum: 2 })
  await old
  assert.deepEqual(h.page.data.rows.map((row) => row.order.id), ['103'])
  assert.equal(h.page.data.pageNum, 1)
})

test('支付成功或取消都刷新当前筛选第一页和计数，不误翻下一页', async () => {
  for (const cancelled of [false, true]) {
    const paid = []
    const h = harness('orders', { respond: ({ url }) => url === '/shop/orders' ? list() : {}, payOrder: async (id) => {
      paid.push(id); if (cancelled) throw new Error('cancel'); return true
    } })
    h.page.setData({ wechatPayEnabled: true, pageNum: 3, loading: false, activeTab: 'pending-payment' })
    await h.page.pay(event({ id: ID }))
    assert.deepEqual(paid, [ID])
    assert.equal(h.calls.find((call) => call.url === '/shop/orders').params.pageNum, 1)
    assert.equal(h.calls.find((call) => call.url === '/shop/orders').params.orderState, 'PENDING_PAYMENT')
    assert.ok(h.calls.some((call) => call.url === '/shop/profile/order-summary'))
    assert.equal(h.page.data.payingId, null)
  }
})

test('订单详情支持字符串ID登录回跳与ID下拉刷新', async () => {
  const h = harness('order-detail', { loggedIn: false, respond: () => detail() })
  h.page.onLoad({ id: ID })
  await h.page.onShow()
  assert.equal(h.page.orderId, ID)
  h.setLogin(true)
  await h.page.onShow()
  assert.equal(h.calls[0].url, `/shop/orders/${ID}`)
  h.page.onPullDownRefresh()
  await new Promise((resolve) => setImmediate(resolve))
  assert.equal(h.calls.length, 2)
})

test('详情重载忽略过时响应和卸载后的响应', async () => {
  const first = deferred(), second = deferred()
  let index = 0
  const h = harness('order-detail', { respond: () => (++index === 1 ? first.promise : second.promise) })
  h.page.onLoad({ id: ID })
  const a = h.page.load(), b = h.page.load()
  second.resolve(detail())
  await b
  first.reject(new Error('stale'))
  await a
  assert.equal(h.page.data.error, '')
  assert.equal(h.page.data.rows[0].order.id, ID)
})

test('取消订单和确认收货使用原始字符串ID，重复确认不重复请求', async () => {
  for (const [handler, action] of [['cancelOrder', 'cancel'], ['receive', 'receive']]) {
    const pending = deferred()
    const h = harness('order-detail', { respond: ({ method }) => method === 'PUT' ? pending.promise : detail() })
    h.page.onLoad({ id: ID })
    h.page[handler](event({ id: ID }))
    const confirm = h.modals[0].success({ confirm: true })
    await h.modals[0].success({ confirm: true })
    assert.deepEqual(h.calls, [{ url: `/shop/orders/${ID}/${action}`, method: 'PUT' }])
    pending.resolve({})
    await confirm
    assert.equal(h.calls[1].url, `/shop/orders/${ID}`)
  }
})

test('售后可申请范围考虑开关、窗口、进行中记录与剩余数量', () => {
  assert.equal(policy.afterSaleEligibility(detail()).allowed, true)
  assert.equal(policy.afterSaleEligibility(detail({ afterSaleSelfServiceEnabled: false })).allowed, false)
  assert.equal(policy.afterSaleEligibility(detail({ afterSaleDeadline: '2020-01-01 00:00:00' })).allowed, false)
  assert.equal(policy.afterSaleEligibility(detail({ afterSales: [{ applyType: 2, status: 4 }] })).allowed, false)
  assert.equal(policy.afterSaleEligibility(detail({ order: { id: ID, status: 0 } })).allowed, false)
  const refund = { applyType: 1, status: 1, items: [{ orderItemId: ITEM, refundQuantity: 1 }] }
  assert.equal(policy.remainingItems(detail({ afterSales: [refund] }))[0].remaining, 1)
  assert.equal(policy.remainingItems(detail({ afterSales: [{ ...refund, applyType: 3 }] }))[0].remaining, 2, '已完成换货不会重复扣减原商品可售后数')
})

test('售后详情展示并提供寄回物流、换货确认和申请入口', async () => {
  const h = harness('order-detail', { respond: () => detail({ afterSales: [
    { id: SALE, applyType: '3', status: '8' }, { id: '111', applyType: '3', status: '1' }, { id: '112', applyType: 2, status: 5 }
  ] }) })
  h.page.onLoad({ id: ID })
  await h.page.load()
  const sales = h.page.data.rows[0].afterSales
  assert.equal(sales[0].canReceiveExchange, true)
  assert.equal(sales[1].statusText, '换货完成')
  assert.equal(sales[2].canReturn, true)
  assert.equal(h.page.data.rows[0].canReceive, false, '进行中售后不展示普通订单收货')
  h.page.receiveExchange(event({ id: SALE }))
  await h.modals[0].success({ confirm: true })
  assert.ok(h.calls.some((call) => call.url === `/shop/after-sales/${SALE}/exchange-received` && call.method === 'PUT'))
})

test('退货物流校验与提交失败保留草稿，成功后刷新详情', async () => {
  let fail = true
  const h = harness('order-detail', { respond: ({ method }) => {
    if (method === 'PUT') { if (fail) throw new Error('网络异常'); return {} }
    return detail({ afterSales: [{ id: SALE, applyType: 2, status: 5, returnDeliveryCompany: '顺丰速运', returnDeliveryNo: 'SF123456' }] })
  } })
  h.page.onLoad({ id: ID }); await h.page.load()
  h.page.editShipment(event({ id: SALE }))
  assert.equal(h.page.data.deliveryNo, 'SF123456')
  h.page.setData({ deliveryNo: 'bad!' }); await h.page.submitShipment()
  assert.equal(h.calls.length, 1)
  h.page.setData({ deliveryNo: 'SF123456' }); await h.page.submitShipment()
  assert.equal(h.page.data.editingSaleId, SALE)
  assert.equal(h.page.data.shipmentError, '网络异常')
  fail = false; await h.page.submitShipment()
  assert.equal(h.page.data.editingSaleId, '')
  assert.deepEqual(h.calls.find((call) => call.method === 'PUT').data, { deliveryCompany: '顺丰速运', deliveryNo: 'SF123456' })
})

test('售后申请无凭证也可提交，ID不转Number且不提交客户端退款金额', async () => {
  const h = harness('after-sale', { respond: ({ method }) => method === 'POST' ? {} : detail() })
  h.page.onLoad({ orderId: ID }); await h.page.onShow()
  h.page.reasonInput({ detail: { value: '商品不合适' } })
  await h.page.submit()
  const payload = h.calls.find((call) => call.method === 'POST').data
  assert.deepEqual(payload, { orderId: ID, applyType: 1, reason: '商品不合适', items: [{ orderItemId: ITEM, quantity: 2 }], proofImages: null })
  assert.equal(h.uploads.length, 0)
  assert.equal(h.routes.at(-1), `/pages/order-detail/index?id=${ID}`)
  await h.page.submit()
  assert.equal(h.calls.filter((call) => call.method === 'POST').length, 1)
})

test('选择图片返回页面不覆盖申请草稿；未发货不能选择换货', async () => {
  const h = harness('after-sale', { respond: () => detail({ order: { id: ID, status: 1 } }) })
  h.page.onLoad({ orderId: ID }); await h.page.onShow()
  h.page.reasonInput({ detail: { value: '保留原因' } })
  h.page.changeQuantity(event({ id: ITEM, delta: -1 }))
  h.page.selectType(event({ type: 3 }))
  await h.page.onShow()
  assert.equal(h.page.data.applyType, 1)
  assert.equal(h.page.data.reason, '保留原因')
  assert.equal(h.page.data.items[0].selectedQuantity, 1)
  assert.equal(h.calls.length, 1)
})

test('零商品/空原因不会提交，提交期间重复点击只有一次请求', async () => {
  const pending = deferred()
  const h = harness('after-sale', { respond: ({ method }) => method === 'POST' ? pending.promise : detail() })
  h.page.onLoad({ orderId: ID }); await h.page.onShow()
  await h.page.submit()
  assert.equal(h.calls.length, 1)
  h.page.reasonInput({ detail: { value: '测试' } })
  h.page.changeQuantity(event({ id: ITEM, delta: -1 })); h.page.changeQuantity(event({ id: ITEM, delta: -1 }))
  await h.page.submit(); assert.equal(h.calls.length, 1)
  h.page.changeQuantity(event({ id: ITEM, delta: 1 }))
  const first = h.page.submit(); await h.page.submit()
  assert.equal(h.calls.length, 2)
  pending.resolve({}); await first
})

test('图片选择取消无报错，过大图片不添加，图片不是必填', async () => {
  const h = harness('after-sale', { choose: (options) => {
    options.success({ tempFiles: [{ tempFilePath: '/tmp/image.jpg', size: 200 }, { tempFilePath: '/tmp/large.jpg', size: 6 * 1024 * 1024 }] })
    options.complete()
  } })
  h.page.chooseProof()
  assert.equal(h.page.data.proofs.length, 1)
  assert.match(h.page.data.submitError, /5MB/)
  assert.equal(h.page.data.selectingProof, false)
  const cancelled = harness('after-sale', { choose: (options) => { options.fail({ errMsg: 'chooseMedia:fail cancel' }); options.complete() } })
  cancelled.page.chooseProof()
  assert.equal(cancelled.page.data.submitError, '')
})

test('凭证采用鉴权上传，申请失败重试不重复上传已完成图片', async () => {
  let failed = true
  const h = harness('after-sale', { respond: ({ method }) => {
    if (method === 'POST') { if (failed) throw new Error('重试'); return {} }
    return detail()
  }, upload: (options) => options.success({ statusCode: 200, data: JSON.stringify({ code: 200, data: 'proof-1.jpg' }) }) })
  h.page.onLoad({ orderId: ID }); await h.page.onShow()
  h.page.setData({ reason: '测试', proofs: [{ path: '/tmp/proof.jpg', filename: '' }] })
  await h.page.submit()
  assert.equal(h.page.data.submitError, '重试')
  assert.equal(h.page.data.proofs[0].filename, 'proof-1.jpg')
  failed = false; await h.page.submit()
  assert.equal(h.uploads.length, 1)
  assert.equal(h.uploads[0].name, 'file')
  assert.equal(h.uploads[0].header.Authorization, 'Bearer test-token')
  assert.equal(h.uploads[0].url, `https://example.test/api/shop/media/after-sale-proofs?orderId=${ID}`)
  assert.equal(h.calls.find((call) => call.method === 'POST').data.proofImages, '["proof-1.jpg"]')
})

test('凭证401清理会话，失败不得继续提交售后申请', async () => {
  const h = harness('after-sale', { respond: () => detail(), upload: (options) => options.success({ statusCode: 401, data: JSON.stringify({ code: 401, message: '请先登录' }) }) })
  h.page.onLoad({ orderId: ID }); await h.page.onShow()
  h.page.setData({ reason: '测试', proofs: [{ path: '/tmp/proof.jpg', filename: '' }] })
  await h.page.submit()
  assert.equal(h.cleared(), 1)
  assert.equal(h.calls.length, 1)
  assert.equal(h.page.data.submitting, false)
})

test('原生售后表单及详情提供完整实际绑定，不只显示进度文案', () => {
  const form = readFileSync(new URL('../pages/after-sale/index.wxml', import.meta.url), 'utf8')
  for (const handler of ['changeQuantity', 'selectType', 'reasonInput', 'chooseProof', 'removeProof', 'submit']) assert.match(form, new RegExp(`bind(?:tap|input)="${handler}"`))
  const view = readFileSync(new URL('../pages/order-detail/index.wxml', import.meta.url), 'utf8')
  for (const handler of ['applyAfterSale', 'cancelAfterSale', 'editShipment', 'submitShipment', 'receiveExchange']) assert.ok(view.includes(`bindtap="${handler}"`))
})
