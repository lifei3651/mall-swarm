import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'

const require = createRequire(import.meta.url)
const { paymentSummary, amountLabel } = require('../pages/order-detail/policy.js')
const ID = '9212345678901234567', CHILD = '9212345678901234568', TRADE = '9212345678901234569'
const row = (patch = {}) => ({ order: { id: ID, orderNo: 'SO-QA-1', status: 0, payType: 'WECHAT', payAmount: '99.00', totalAmount: '120.00', ...patch }, items: [], afterSales: [] })
const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes }); return { promise, resolve } }
const parameters = { timeStamp: '1', nonceStr: 'local', packageValue: 'prepay_id=LOCAL_TEST', paySign: 'LOCAL_TEST', signType: 'RSA' }

function setup({ initial = row(), group, config = { wechatPayEnabled: true }, native = 'success', reconcile = true, after, intercept } = {}) {
  let details = initial, payRequests = 0, nativeCalls = 0
  const env = commerceEnv(async options => {
    const custom = intercept && intercept(options)
    if (custom !== undefined) return custom
    if (options.url === '/shop/pay/config') { if (config instanceof Error) throw config; return config }
    if (options.url === '/shop/orders/payment-detail') return group
    if (options.url === `/shop/orders/${ID}`) return details
    if (options.url === '/shop/pay/wechat/create') { payRequests++; return parameters }
    if (options.url === '/shop/pay/wechat/query') { if (after) details = after; return reconcile }
    throw new Error(`Unexpected request: ${options.url}`)
  })
  env.wx.showLoading = () => {}; env.wx.hideLoading = () => {}
  env.wx.requestPayment = options => {
    nativeCalls++
    if (native === 'cancel') options.fail({ errMsg: 'requestPayment:fail cancel' })
    else if (native === 'fail') options.fail({ errMsg: 'requestPayment:fail network' })
    else options.success({})
  }
  const page = env.page('order-detail')
  page.onLoad({ id: ID })
  return { ...env, page, setDetails: value => { details = value }, get payRequests() { return payRequests }, get nativeCalls() { return nativeCalls } }
}

test('订单金额标签区分待付款、实付、关闭及混合状态，保留零元而非回退原价', () => {
  for (const status of [0, 1, 2, 3, 4, 5]) assert.equal(amountLabel({ status }), status === 0 ? '待付金额' : status === 4 ? '订单金额' : '实付金额')
  assert.equal(paymentSummary([row({ payAmount: 0 })]).totalText, '0.00')
  assert.equal(paymentSummary([row({ payAmount: null })]).totalText, '120.00')
  assert.equal(paymentSummary([row({ payAmount: '.10' }), row({ id: CHILD, payAmount: '.20' })]).totalText, '0.30')
  assert.equal(paymentSummary([row(), row({ status: 1 })]).summaryLabel, '订单金额')
})

test('非微信、非待付款、重复ID、非法金额或不相关订单均不提供继续支付', () => {
  for (const rows of [[], [row({ payType: 'BALANCE' })], [row({ payType: 'ALIPAY' })], [row({ status: 1 })], [row({ status: 4 })], [row({ id: Number(ID) })], [row({ payAmount: 'bad' })], [row({ payAmount: 0 })], [row(), row()], [row(), row({ id: CHILD })]]) {
    assert.equal(paymentSummary(rows).payOrderId, '')
  }
})

test('继续支付使用原始订单ID、真实支付模块及查单，不重新创建业务订单', async () => {
  const h = setup({ after: row({ status: 1, payTime: '2026-09-06T18:00:00' }) })
  await h.page.onShow(); await h.page.pay()
  assert.equal(h.payRequests, 1); assert.equal(h.nativeCalls, 1)
  assert.deepEqual(h.calls.filter(call => call.method === 'POST'), [{ url: '/shop/pay/wechat/create', method: 'POST', params: { orderId: ID } }])
  assert.equal(h.calls.find(call => call.url.endsWith('/query')).params.orderId, ID)
  assert.equal(h.page.data.rows[0].order.status, 1)
  assert.equal(h.page.data.payOrderId, '')
  assert.ok(h.notices.some(message => /支付已确认/.test(message)))
  assert.equal(h.page.data.paying, false); assert.equal(h.page.data.actingId, null)
})

test('从子单进入先取完整合并交易，显示全部金额而非子单金额，取消提示涉及全部子单', async () => {
  const child = row({ tradeId: TRADE, paymentOrderNo: 'TRADE-QA' })
  const h = setup({ initial: child, group: [child, row({ id: CHILD, payAmount: '29.90', tradeId: TRADE })], native: 'cancel' })
  await h.page.onShow()
  assert.equal(h.page.data.totalText, '128.90'); assert.equal(h.page.data.rows.length, 2)
  assert.match(h.page.data.summaryMeta, /合并交易.*2 个订单/)
  await h.page.pay()
  assert.equal(h.payRequests, 1)
  assert.equal(h.calls.find(call => call.url.endsWith('/create')).params.orderId, ID)
  h.wx.showModal = dialog => h.notices.push(dialog.content)
  h.page.cancelOrder({ currentTarget: { dataset: { id: ID } } })
  assert.ok(h.notices.some(message => /所有待付款子订单/.test(message)))
})

test('微信订单中心商户单号入口也能恢复同一合并订单付款', async () => {
  const orders = [row({ tradeId: TRADE }), row({ id: CHILD, tradeId: TRADE, payAmount: '1.00' })]
  const h = setup({ group: orders, native: 'cancel' })
  h.page.onLoad({ paymentNo: 'TRADE-QA' })
  await h.page.onShow(); await h.page.pay()
  assert.equal(h.page.data.totalText, '100.00')
  assert.equal(h.payRequests, 1)
  assert.equal(h.calls.filter(call => call.url === `/shop/orders/${ID}`).length, 0)
})

test('合并信息缺失或返回无关子单时失败关闭，不以单子单金额发起付款', async () => {
  for (const [initial, group] of [[row({ tradeId: TRADE }), []], [row({ tradeId: TRADE, paymentOrderNo: 'TRADE-QA' }), [row({ id: CHILD, tradeId: '123' })]]]) {
    const h = setup({ initial, group }); await h.page.onShow(); await h.page.pay()
    assert.equal(h.page.data.payOrderId, ''); assert.equal(h.payRequests, 0); assert.ok(h.page.data.error)
  }
})

test('未开通或支付配置查询失败显示明确反馈，不调起收银台', async () => {
  for (const config of [{ wechatPayEnabled: false }, new Error('支付配置查询失败')]) {
    const h = setup({ config }); await h.page.onShow(); await h.page.pay()
    assert.equal(h.payRequests, 0); assert.equal(h.nativeCalls, 0)
    assert.ok(h.notices.length); assert.equal(h.page.data.actingId, null)
  }
})

test('点击续付时重取订单，金额或状态变化须重新核对，不按旧金额发起', async () => {
  for (const changed of [row({ payAmount: '109.00' }), row({ status: 1 }), row({ status: 4 })]) {
    const h = setup(); await h.page.onShow(); h.setDetails(changed); await h.page.pay()
    assert.equal(h.payRequests, 0)
    assert.ok(h.notices.some(message => /订单状态或付款金额已更新/.test(message)))
  }
})

test('支付期间防重复点击及取消，下拉刷新和收银台返回不打断当前核对', async () => {
  const pending = deferred()
  const h = setup({ intercept: options => options.url === '/shop/pay/config' ? pending.promise : undefined, native: 'cancel' })
  await h.page.onShow(); const paying = h.page.pay()
  await h.page.pay(); await h.page.onShow(); h.page.onPullDownRefresh()
  h.page.cancelOrder({ currentTarget: { dataset: { id: ID } } })
  assert.equal(h.calls.filter(call => call.url === '/shop/pay/config').length, 1)
  pending.resolve({ wechatPayEnabled: true }); await paying
  assert.equal(h.payRequests, 1)
  assert.ok(h.notices.some(message => /已取消本次支付/.test(message)))
  assert.equal(h.page.data.payOrderId, ID)
})

test('取消/失败不伪造已付款，查询期间断网提示结果待核对', async () => {
  for (const native of ['cancel', 'fail']) {
    const h = setup({ native }); await h.page.onShow(); await h.page.pay()
    assert.equal(h.page.data.rows[0].order.status, 0)
    assert.ok(h.notices.some(message => native === 'cancel' ? /已取消本次/.test(message) : /勿重复付款/.test(message)))
  }
  const h = setup({ intercept: options => options.url.endsWith('/query') ? Promise.reject(new Error('查询超时')) : undefined })
  await h.page.onShow(); await h.page.pay()
  assert.ok(h.notices.some(message => /查询超时.*勿重复付款/.test(message)))
})

test('查单返回true但详情仍待付款或已关闭，不误报支付成功', async () => {
  for (const status of [0, 4]) {
    const h = setup({ after: row({ status }) }); await h.page.onShow(); await h.page.pay()
    assert.equal(h.notices.some(message => /支付已确认/.test(message)), false)
    assert.ok(h.notices.some(message => /勿重复付款/.test(message)))
  }
})

test('换号或页面销毁时忽略旧核对结果，不能调起旧账号支付', async () => {
  for (const action of ['logout', 'unload']) {
    const pending = deferred()
    const h = setup({ intercept: options => options.url === '/shop/pay/config' ? pending.promise : undefined })
    await h.page.onShow(); const paying = h.page.pay()
    if (action === 'logout') h.token('another-member'); else h.page.onUnload()
    pending.resolve({ wechatPayEnabled: true }); await paying
    assert.equal(h.payRequests, 0)
    if (action === 'logout') assert.equal(h.page.data.rows.length, 0)
  }
})

test('预支付请求发出后换号，晚返回的参数也不能调起微信收银台', async () => {
  const pending = deferred(), started = deferred()
  const h = setup({ intercept: options => {
    if (options.url.endsWith('/create')) { started.resolve(); return pending.promise }
  } })
  await h.page.onShow(); const paying = h.page.pay(); await started.promise
  h.token('different-member'); pending.resolve(parameters); await paying
  assert.equal(h.nativeCalls, 0)
  assert.equal(h.calls.some(call => call.url.endsWith('/query')), false)
  assert.equal(h.page.data.rows.length, 0)
})

test('收银台已返回但查询失败时只保留待确认提示，五次未确认不会当成功', async () => {
  const h = setup({ reconcile: false })
  await h.page.onShow(); await h.page.pay()
  assert.equal(h.calls.filter(call => call.url.endsWith('/query')).length, 5)
  assert.equal(h.page.data.rows[0].order.status, 0)
  assert.ok(h.notices.some(message => /支付结果还在确认中/.test(message)))
})

test('页面只提供一个主要付款按钮，金额标签动态绑定且不再写死本次已付款', () => {
  const markup = readFileSync(new URL('../pages/order-detail/index.wxml', import.meta.url), 'utf8')
  assert.equal((markup.match(/bindtap="pay"/g) || []).length, 1)
  assert.match(markup, /item.order.amountLabel/)
  assert.match(markup, /summaryLabel/)
  assert.doesNotMatch(markup, /本次付款包含|<text>实付金额<\/text>/)
})
