import test from 'node:test'
import assert from 'node:assert/strict'
import { commerceEnv } from './helpers/commerce-env.mjs'
const tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes }); return { promise, resolve } }
const event = (key, value) => ({ currentTarget: { dataset: { [key]: value } } })
const ticket = { id: '91', type: 'CONSULTATION', status: 'OPEN', subject: '测试问题', ticketNo: 'ST-91', orderId: '9' }
const detail = { ticket, replies: [] }

test('工单分类、翻页及失败重试使用H5同一路由，不混入旧筛选', async () => {
  const old = deferred()
  const env = commerceEnv(({ params }) => params.status === 'OPEN' ? old.promise : { list: [ticket], totalPage: 2 }), page = env.page('support')
  page.setData({ status: 'OPEN' }); const first = page.load(); page.setData({ status: 'CLOSED' }); await page.load()
  old.resolve({ list: [{ ...ticket, id: '92' }], totalPage: 1 }); await first
  assert.equal(page.data.rows[0].id, '91'); assert.equal(page.data.totalPage, 2)
  await page.loadMore(); assert.equal(env.calls.at(-1).params.pageNum, 2); assert.equal(env.calls.at(-1).params.status, 'CLOSED')
})

test('售后争议必须关联售后；订单切换清除旧售后，不关联其他人的订单', async () => {
  const env = commerceEnv(({ url }) => url === '/shop/orders' ? { list: [{ order: { id: '9', orderNo: 'O9' }, items: [], afterSales: [{ id: '90', afterSaleNo: 'AS90' }] }] } : {}), page = env.page('support')
  await page.loadContext(); page.chooseOrder({ detail: { value: 1 } }); page.chooseAfterSale({ detail: { value: 1 } })
  assert.equal(page.data.form.afterSaleId, '90'); page.chooseOrder({ detail: { value: 0 } }); assert.equal(page.data.form.afterSaleId, '')
  page.setData({ form: { type: 'AFTER_SALE_DISPUTE', subject: '测试争议', content: '测试说明', orderId: '', afterSaleId: '' } })
  await page.submit(); assert.equal(env.calls.filter(item => item.method === 'POST').length, 0); assert.match(env.notices.join(), /必须选择/)
})

test('新工单失败保留草稿与幂等键，相同内容重试不重复创建，成功后跳工单详情', async () => {
  let fail = true
  const env = commerceEnv(() => { if (fail) throw new Error('网络中断'); return detail }), page = env.page('support')
  page.setData({ form: { type: 'CONSULTATION', subject: '测试标题', content: '测试说明', orderId: '9', afterSaleId: '' }, creating: true })
  await page.submit(); assert.equal(page.data.form.content, '测试说明'); assert.equal(env.routes.length, 0)
  fail = false; await page.submit(); assert.equal(env.calls[0].idempotencyKey, env.calls[1].idempotencyKey)
  assert.deepEqual(env.calls[1].data, { type: 'CONSULTATION', subject: '测试标题', content: '测试说明', orderId: '9', afterSaleId: null })
  assert.equal(env.routes[0], '/pages/support-detail/index?id=91'); assert.equal(page.data.form.content, '')
})

test('工单发送中连点仅一次、换号晚响应不跳转或清除当前账号草稿', async () => {
  const pending = deferred(), env = commerceEnv(() => pending.promise), page = env.page('support')
  page.setData({ form: { type: 'ACCOUNT', subject: '账号问题', content: '测试说明', orderId: '', afterSaleId: '' } })
  const task = page.submit(); await page.submit(); assert.equal(env.calls.length, 1)
  env.token('other'); pending.resolve(detail); await task; assert.equal(env.routes.length, 0); assert.equal(page.data.form.content, '测试说明')
})

test('工单回复与关闭使用H5接口，关闭必须确认，取消保留输入且不发写请求', async () => {
  const env = commerceEnv(({ url }) => url.endsWith('/close') ? { ...detail, ticket: { ...ticket, status: 'CLOSED' } } : { ...detail, replies: [{ id: '1', senderType: 'MEMBER', content: '补充说明' }] }), page = env.page('support-detail')
  page.ticketId = '91'; await page.load(); page.input({ detail: { value: '补充说明' } }); await page.reply()
  assert.equal(env.calls[1].url, '/shop/service-tickets/91/replies'); assert.equal(env.calls[1].method, 'POST'); assert.ok(env.calls[1].idempotencyKey); assert.equal(page.data.content, '')
  env.wx.showModal = ({ success }) => success({ confirm: false }); await page.closeTicket(); assert.equal(env.calls.length, 2)
  env.wx.showModal = ({ success }) => success({ confirm: true }); await page.closeTicket(); assert.equal(env.calls[2].method, 'PUT'); assert.equal(page.data.ticket.status, 'CLOSED')
  page.input({ detail: { value: '关闭后不发送' } }); await page.reply(); assert.equal(env.calls.length, 3)
})

test('工单回复失败保留内容；轮询不覆盖用户输入，离页清理轮询并丢弃晚结果', async () => {
  const pending = deferred(), env = commerceEnv(({ method }) => method === 'POST' ? Promise.reject(new Error('失败')) : pending.promise), page = env.page('support-detail')
  page.ticketId = '91'; page.applyDetail(detail); page.input({ detail: { value: '我的草稿' } }); await page.reply(); assert.equal(page.data.content, '我的草稿')
  const task = page.load(true); page.onHide(); pending.resolve({ ...detail, replies: [{ id: '2', senderType: 'ADMIN', content: '旧回复' }] }); await task; assert.equal(page.data.replies.length, 0)
})

test('短信不开启不写入；取消授权零写请求；同意开启与关闭参数与H5一致', async () => {
  const env = commerceEnv(({ data }) => data ? { available: true, enabled: data.enabled } : { available: true, enabled: false }), page = env.page('messages')
  await page.loadSmsPreference(); assert.equal(env.calls.length, 1)
  env.wx.showModal = ({ success }) => success({ confirm: false }); await page.changeSmsPreference(); assert.equal(env.calls.length, 1)
  env.wx.showModal = ({ success }) => success({ confirm: true }); await page.changeSmsPreference()
  assert.deepEqual(env.calls[1].data, { enabled: true, consent: true }); await page.changeSmsPreference(); assert.deepEqual(env.calls[2].data, { enabled: false, consent: false })
})

test('本分类已读只提交当前合法分类；晚到换号确认不能开启新账号短信', async () => {
  const env = commerceEnv(({ url }) => url === '/shop/messages' ? { list: [] } : {}), page = env.page('messages')
  page.setData({ category: 'AFTER_SALE_REFUND' }); await page.readCategory(); assert.equal(env.calls[0].url, '/shop/messages/read-category'); assert.deepEqual(env.calls[0].params, { category: 'AFTER_SALE_REFUND' })
  const count = env.calls.length; page.setData({ category: 'ATTACK' }); await page.readCategory(); assert.equal(env.calls.length, count)
  page.setData({ smsPreference: { available: true, enabled: false } }); let modal; env.wx.showModal = options => { modal = options }
  const change = page.changeSmsPreference(); env.token('other'); modal.success({ confirm: true }); await change; assert.equal(env.calls.length, count)
})

test('订单列表取消/确认收货前重新检查状态，合并订单提示不能漏掉其他子单', async () => {
  let status = 2
  const env = commerceEnv(({ url }) => url === '/shop/orders/9' ? { order: { id: '9', status }, afterSales: [] } : url === '/shop/orders' ? { list: [], total: 0 } : {}), page = env.page('orders')
  page.setData({ rows: [{ order: { id: '9', status: 0, tradeId: '8' } }] }); await page.cancelOrder(event('id', '9'))
  assert.match(env.notices[0], /所有待付款子订单/); assert.equal(env.calls.some(item => item.method === 'PUT'), false)
  page.setData({ rows: [{ order: { id: '9', status: 2 }, canReceive: true }] }); await page.receive(event('id', '9')); assert.ok(env.calls.some(item => item.url === '/shop/orders/9/receive' && item.method === 'PUT'))
})
