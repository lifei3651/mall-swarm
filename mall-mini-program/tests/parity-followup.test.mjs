import test from 'node:test'
import assert from 'node:assert/strict'
import { commerceEnv } from './helpers/commerce-env.mjs'
import { readFileSync } from 'node:fs'
import { runMiniScript } from './helpers/run-mini-script.mjs'

const event = id => ({ currentTarget: { dataset: { id } } })
const row = (id, extra = {}) => ({ order: { id, status: 0, payType: 'WECHAT', payAmount: 12, ...extra }, items: [{ id: '71', quantity: 2 }], afterSaleWindowMode: 'RECEIVED' })
const tick = () => new Promise(resolve => setImmediate(resolve))

test('R02：微信与余额待付款列表都可进入同一订单详情，不直接扣款', async () => {
  for (const payType of ['WECHAT', 'BALANCE']) {
    const env = commerceEnv(() => ({ list: [row('9', { payType })], total: 1 })), page = env.page('orders')
    await page.load(true)
    assert.equal(page.data.rows[0].canPay, true)
    page.pay(event('9')); page.pay(event('9'))
    assert.deepEqual(env.routes, ['/pages/order-detail/index?id=9'])
    assert.equal(env.calls.some(call => call.method && call.method !== 'GET'), false)
  }
})

test('R02：联合订单仅首个可见子单提供整体付款/取消，不支持渠道不冒充微信付款', async () => {
  const env = commerceEnv(() => ({ list: [row('9', { tradeId: '8' }), row('10', { tradeId: '8' }), row('11', { payType: 'ALIPAY' })], total: 3 })), page = env.page('orders')
  await page.load(true)
  assert.deepEqual(page.data.rows.map(item => item.canCancel), [true, false, true])
  assert.deepEqual(page.data.rows.map(item => item.canPay), [true, false, false])
  page.pay(event('10')); page.pay(event('11'))
  assert.equal(env.routes.length, 0)
})

test('R03：发货后列表显示自动收货时间、售后入口；过期/处理中/禁用不显示', async () => {
  const good = { ...row('9', { status: 2 }), autoReceiveEnabled: true, autoReceiveDeadline: '2099-10-01 12:00:00' }
  const values = [good, { ...good, order: { ...good.order, id: '10' }, afterSaleDeadline: '2000-01-01' }, { ...good, order: { ...good.order, id: '11' }, afterSales: [{ status: 4 }] }, { ...good, order: { ...good.order, id: '12' }, afterSaleSelfServiceEnabled: false }]
  const env = commerceEnv(() => ({ list: values, total: 4 })), page = env.page('orders'); await page.load(true)
  assert.deepEqual(page.data.rows.map(item => item.canApplyAfterSale), [true, false, false, false])
  assert.match(page.data.rows[0].autoReceiveText, /2099-10-01 12:00/)
  assert.equal(page.data.rows[2].autoReceiveText, '')
  page.applyAfterSale(event('9')); page.applyAfterSale(event('10'))
  assert.deepEqual(env.routes, ['/pages/after-sale/index?orderId=9'])
})

test('R04：订单静默刷新保留已加载页/原卡片，离页旧响应不回写', async () => {
  let finish
  const env = commerceEnv(() => new Promise(resolve => { finish = resolve })), page = env.page('orders')
  page.setData({ loading: false, rows: [row('9')], pageNum: 2, total: 20 })
  const task = page.refreshQuietly(); await tick()
  assert.equal(page.data.rows.length, 1); assert.equal(page.data.loading, false)
  page.onHide(); finish({ list: [row('10')], total: 1 }); await task
  assert.equal(page.data.rows[0].order.id, '9')
})

test('R04：消息刷新保持分类和已加载页，不清屏；旧分类响应不能覆盖新分类', async () => {
  const env = commerceEnv(({ url, params }) => url.endsWith('/unread') ? { total: 2, categories: { SERVICE: 2 } } : { list: Array.from({ length: 20 }, (_, index) => ({ id: String((params.pageNum - 1) * 20 + index + 1) })), pageNum: params.pageNum, totalPage: 3 })
  const page = env.page('messages'); page.setData({ category: 'SERVICE', loading: false, pageNum: 2, rows: [{ id: 'old' }] })
  await page.refreshQuietly()
  assert.equal(page.data.rows.length, 40); assert.equal(page.data.pageNum, 2)
  assert.equal(page.data.loading, false); assert.equal(page.data.category, 'SERVICE')
  assert.equal(env.calls.filter(call => call.url === '/shop/messages').every(call => call.params.category === 'SERVICE'), true)
  let finish
  const stale = commerceEnv(() => new Promise(resolve => { finish = resolve })), other = stale.page('messages')
  other.setData({ loading: false, category: 'SERVICE', rows: [{ id: 'old' }] })
  const task = other.refreshQuietly(); await tick(); other.setData({ category: 'ORDER_LOGISTICS' }); finish({ list: [{ id: 'new' }] }); await task
  assert.equal(other.data.rows[0].id, 'old')
})

test('R04：详情定时更新不得打断支付密码、售后物流编辑或后台页面', async () => {
  for (const state of [{ balanceDialog: true }, { balanceBusy: true }, { paying: true }, { editingSaleId: '8' }, { actingId: '9' }]) {
    const env = commerceEnv(), page = env.page('order-detail'); page.onLoad({ id: '9' }); page.setData({ loading: false, ...state })
    await page.load(true); assert.equal(env.calls.length, 0)
  }
  const env = commerceEnv(), page = env.page('order-detail'); page.onLoad({ id: '9' }); page.setData({ loading: false }); page.onHide()
  await page.load(true); assert.equal(env.calls.length, 0)
})

test('R04：原生轮询30秒、不重叠；隐藏/换号停止；忙碌时跳过', async () => {
  const timers = new Map(); let next = 0, token = 'a', calls = 0, busy = true
  const module = { exports: {} }
  runMiniScript(readFileSync(new URL('../utils/foreground-refresh.js', import.meta.url), 'utf8'), {
    module, require: () => ({ getToken: () => token }),
    setTimeout: (fn, delay) => { assert.equal(delay, 30000); timers.set(++next, fn); return next }, clearTimeout: id => timers.delete(id)
  })
  const page = {}, api = module.exports
  const fire = async () => { const [id, fn] = [...timers.entries()][0]; timers.delete(id); await fn() }
  api.start(page, async () => { calls++ }, () => busy)
  await fire(); assert.equal(calls, 0); assert.equal(timers.size, 1)
  busy = false; await fire(); assert.equal(calls, 1); assert.equal(timers.size, 1)
  token = 'b'; await fire(); assert.equal(calls, 1); assert.equal(timers.size, 0)
  api.start(page, async () => { calls++ }); api.stop(page); assert.equal(timers.size, 0)
  let finish
  api.start(page, () => new Promise(resolve => { finish = resolve })); const pending = fire(); await tick(); assert.equal(timers.size, 0)
  api.stop(page); finish(); await pending; assert.equal(timers.size, 0)
})

test('R04：已发出的详情刷新在用户开始编辑或切号后不覆盖现有状态', async () => {
  for (const mode of ['edit', 'account']) {
    let finish
    const env = commerceEnv(() => new Promise(resolve => { finish = resolve })), page = env.page('order-detail')
    page.onLoad({ id: '9' }); page.setData({ loading: false, rows: [row('9')] })
    const task = page.load(true); await tick()
    if (mode === 'edit') page.setData({ editingSaleId: '8', deliveryNo: 'fixture-draft' })
    else env.token('other')
    finish({ ...row('9', { status: 2 }), items: [] }); await task
    if (mode === 'edit') { assert.equal(page.data.rows[0].order.status, 0); assert.equal(page.data.deliveryNo, 'fixture-draft') }
    else assert.equal(page.data.rows.length, 0)
  }
})

test('R04：列表/消息连续刷新失败保留数据且只提示一次，恢复后清除旧错误', async () => {
  for (const name of ['orders', 'messages']) {
    let fail = true
    const env = commerceEnv(({ url }) => {
      if (fail) throw new Error('fixture-network')
      return url.endsWith('/unread') ? { total: 0, categories: {} } : { list: [], total: 0, pageNum: 1, totalPage: 1 }
    }), page = env.page(name)
    page.setData({ loading: false, rows: name === 'orders' ? [row('9')] : [{ id: '9' }], error: '旧错误' })
    await page.refreshQuietly(); await page.refreshQuietly()
    assert.equal(page.data.rows.length, 1); assert.equal(env.notices.length, 1)
    fail = false; await page.refreshQuietly()
    assert.equal(page.data.rows.length, 0); assert.equal(page.data.error, '')
  }
})
