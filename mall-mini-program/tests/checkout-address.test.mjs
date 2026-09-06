import { runMiniScript } from './helpers/run-mini-script.mjs'
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const plain = (value) => JSON.parse(JSON.stringify(value))
const address = (id = '9223372036854775701', isDefault = 1) => ({ id, isDefault, receiverName: '测试收货人', receiverPhone: '13800000000', province: '湖南省', city: '长沙市', district: '岳麓区', detailAddress: '测试路 1 号' })
const goods = [{ key: 'product-1', productId: '9223372036854775710', skuId: '9223372036854775711', quantity: 2, salePrice: '12.00', coverUrl: '', selected: true }]
const deferred = () => {
  let resolve, reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

function harness(name, { respond, loggedIn = true, selected = goods, stack = [] } = {}) {
  let definition, cleared = 0
  const calls = [], nav = [], modals = [], toasts = [], loginChecks = [], timers = []
  const wx = {
    navigateTo: (options) => nav.push({ type: 'to', ...options }),
    navigateBack: (options) => nav.push({ type: 'back', ...options }),
    redirectTo: (options) => nav.push({ type: 'redirect', ...options }),
    showToast: (options) => toasts.push(options), showModal: (options) => modals.push(options),
    showLoading() {}, hideLoading() {}
  }
  const mocks = {
    '../../utils/request': async (options) => { calls.push(plain(options)); return respond ? respond(options) : undefined },
    '../../utils/cart': { selected: () => selected, directItems: () => selected, clearDirectCheckout() {}, clearSelected: () => { cleared++ } },
    '../../utils/session': { getToken: () => loggedIn ? 'test-session' : '' },
    '../../utils/wechat-address': { choose: async () => { throw new Error('请显式配置地址导入测试') } },
    '../../utils/catalog': { refresh: async (rows) => rows },
    '../../utils/auth': { requireLogin: (route) => { loginChecks.push(route); return loggedIn } },
    '../../utils/format': { money: (value) => Number(value).toFixed(2), mediaUrl: (value) => value,
      identifier: (value) => typeof value === 'number' && (!Number.isSafeInteger(value) || value <= 0) ? '' : /^[1-9]\d{0,18}$/.test(String(value ?? '').trim()) ? String(value).trim() : '' },
    '../../utils/payment': { payOrder: async () => true, isUserCancel: () => false },
    '../../utils/theme': { pageData: () => ({}), apply() {}, sync() {} }
  }
  const source = new URL(`../pages/${name}/index.js`, import.meta.url)
  runMiniScript(readFileSync(source, 'utf8'), {
    Page: (value) => { definition = value },
    require: (id) => { assert.ok(Object.hasOwn(mocks, id), `Explicit mock for ${id}`); return mocks[id] },
    wx, getCurrentPages: () => stack, setTimeout: (fn) => { timers.push(fn); return timers.length },
    setInterval: () => 1, clearInterval() {}
  }, { filename: source.pathname })
  const page = { ...definition, data: plain(definition.data), setData(patch) {
    for (const [key, value] of Object.entries(plain(patch))) {
      const parts = key.split('.'); let target = this.data
      for (const part of parts.slice(0, -1)) target = target[part]
      target[parts.at(-1)] = value
    }
  } }
  return { page, calls, nav, modals, toasts, loginChecks, timers, cleared: () => cleared,
    setLoggedIn: (value) => { loggedIn = value } }
}

function checkoutRespond({ addresses = [address()], quote = { productAmount: '24.00', freightAmount: '6.00', payAmount: '30.00' }, needVerify = false } = {}) {
  return ({ url }) => {
    if (url === '/shop/addresses') return addresses
    if (url === '/shop/pay/config') return { wechatPayEnabled: true }
    if (url === '/shop/orders/freight-quote') return quote
    if (url === '/payment/checkVerify') return { needVerify }
    throw new Error(`Unexpected request ${url}`)
  }
}

test('地址字符串长 ID 可编辑、选择、删除，选择结算地址不修改默认地址', async () => {
  const row = address()
  const h = harness('address', { respond: () => [] })
  h.page.onLoad({ select: '1' })
  h.page.setData({ loading: false, rows: [row] })
  h.page.edit({ currentTarget: { dataset: { id: row.id } } })
  assert.equal(h.page.data.form.id, row.id)
  const emissions = []
  h.page.getOpenerEventChannel = () => ({ emit: (...args) => emissions.push(args) })
  await h.page.choose({ currentTarget: { dataset: { id: row.id } } })
  assert.deepEqual(plain(emissions), [['addressSelected', { id: row.id }]])
  assert.equal(h.calls.length, 0, '本次结算选择不写地址默认标记')
  h.page.returning = false
  await h.page.remove({ currentTarget: { dataset: { id: row.id } } })
  await h.modals[0].success({ confirm: true })
  assert.equal(h.calls[0].url, `/shop/addresses/${row.id}`)
  assert.equal(h.calls[0].method, 'DELETE')
  assert.equal(h.page.data.form.id, null)
})

test('新增非默认地址回传服务端新 ID，登录重建地址页仍返回原结算页', async () => {
  const saved = address('9223372036854775755', 0), selections = []
  const stack = [{ route: 'pages/checkout/index', acceptSelectedAddress: (value) => selections.push(value) }, { route: 'pages/address/index' }, { route: 'pages/address/index' }]
  const h = harness('address', { respond: () => saved, stack })
  h.page.onLoad({ select: '1' })
  h.page.setData({ loading: false, rows: [address()], form: { ...saved, id: null, region: [saved.province, saved.city, saved.district], isDefault: false } })
  await h.page.save()
  assert.equal(h.calls.length, 1)
  assert.equal(h.calls[0].data.isDefault, 0)
  assert.deepEqual(plain(selections), [{ id: saved.id }])
  assert.equal(h.nav[0].delta, 2)
})

test('地址页登录返回 onShow 重载并保留选择模式，避免 onLoad/onShow 双重跳登录', async () => {
  const h = harness('address', { loggedIn: false, respond: () => [address()] })
  h.page.onLoad({ select: '1' })
  assert.equal(h.loginChecks.length, 0)
  await h.page.onShow()
  assert.deepEqual(h.loginChecks, ['/pages/address/index?select=1'])
  assert.equal(h.calls.length, 0)
  h.setLoggedIn(true)
  await h.page.onShow()
  assert.equal(h.page.data.rows[0].id, address().id)
  assert.equal(h.page.data.loading, false)
})

test('地址保存连续点击只发出一次请求', async () => {
  const pending = deferred(), row = address()
  const h = harness('address', { respond: () => pending.promise })
  h.page.onLoad({ select: '1' })
  h.page.setData({ loading: false, rows: [row], form: { ...row, region: [row.province, row.city, row.district] } })
  const first = h.page.save()
  await h.page.save()
  assert.equal(h.calls.length, 1)
  pending.resolve(row)
  await first
})

test('地址加载失败不允许把旧列表误当空地址直接保存，并可重新加载', async () => {
  let fail = true
  const h = harness('address', { respond: () => { if (fail) throw new Error('网络故障'); return [address()] } })
  h.page.onLoad({})
  await h.page.onShow()
  assert.equal(h.page.data.loadError, '网络故障')
  await h.page.save()
  assert.equal(h.calls.length, 1)
  fail = false
  await h.page.load()
  assert.equal(h.page.data.loadError, '')
})

test('结算登录前已生成幂等键，返回登录后加载与支付风控均完成', async () => {
  const h = harness('checkout', { loggedIn: false, respond: checkoutRespond({ needVerify: true }) })
  h.page.onLoad()
  assert.match(h.page.submitKey, /^MINI-CHECKOUT-/)
  const originalKey = h.page.submitKey
  await h.page.onShow()
  assert.equal(h.calls.length, 0)
  h.setLoggedIn(true)
  await h.page.onShow()
  assert.equal(h.page.submitKey, originalKey)
  assert.equal(h.page.data.quoteReady, true)
  assert.equal(h.page.data.needSmsVerify, true)
  assert.equal(h.page.data.payTotal, '30.00')
})

test('本次选择的非默认地址优先，下一次回显仍保留，删除后安全回退本人默认地址', async () => {
  const selectedAddress = address('9223372036854775755', 0)
  let addresses = [address(), selectedAddress]
  const h = harness('checkout', { respond: (options) => checkoutRespond({ addresses })(options) })
  h.page.onLoad()
  await h.page.load()
  h.page.acceptSelectedAddress({ id: selectedAddress.id })
  await h.page.load()
  assert.equal(h.page.data.address.id, selectedAddress.id)
  const quoteCall = h.calls.filter(({ url }) => url.endsWith('freight-quote')).at(-1)
  assert.equal(quoteCall.data.addressId, selectedAddress.id)
  await h.page.load()
  assert.equal(h.page.data.address.id, selectedAddress.id)
  addresses = [address()]
  await h.page.load()
  assert.equal(h.page.data.address.id, address().id)
})

test('报价失败保持应付待确认，禁止创建订单并提供重新计算', async () => {
  let fail = true
  const h = harness('checkout', { respond: (options) => {
    if (options.url.endsWith('freight-quote') && fail) throw new Error('该地区暂不配送')
    return checkoutRespond()(options)
  } })
  h.page.onLoad()
  await h.page.load()
  assert.equal(h.page.data.quoteReady, false)
  assert.equal(h.page.data.payTotal, '--')
  assert.equal(h.page.data.quoteError, '该地区暂不配送')
  await h.page.submit()
  assert.ok(!h.calls.some(({ url }) => url === '/shop/orders'))
  fail = false
  await h.page.retryQuote()
  assert.equal(h.page.data.quoteReady, true)
  assert.equal(h.page.data.quoteError, '')
})

test('金额验证请求失败或不合法响应同样阻止提交，不能回退成无需短信', async () => {
  for (const result of [null, {}, { needVerify: 'false' }, new Error('风控超时')]) {
    const h = harness('checkout', { respond: (options) => {
      if (options.url === '/payment/checkVerify') { if (result instanceof Error) throw result; return result }
      return checkoutRespond()(options)
    } })
    h.page.onLoad()
    await h.page.load()
    assert.equal(h.page.data.quoteReady, false)
    await h.page.submit()
    assert.ok(!h.calls.some(({ url }) => url === '/shop/orders'))
  }
})

test('报价返回缺失、负数或非数字金额一律阻断', async () => {
  for (const value of [null, undefined, '', 'oops', -1, Infinity]) {
    const h = harness('checkout', { respond: checkoutRespond({ quote: { productAmount: 24, freightAmount: 6, payAmount: value } }) })
    h.page.onLoad()
    await h.page.load()
    assert.equal(h.page.data.quoteReady, false)
    assert.equal(h.page.data.payTotal, '--')
  }
})

test('重算期间禁止提交，旧报价晚到不能覆盖新地址的新报价', async () => {
  const old = deferred(), fresh = deferred()
  let index = 0
  const h = harness('checkout', { respond: (options) => {
    if (options.url.endsWith('freight-quote')) return ++index === 1 ? old.promise : fresh.promise
    return { needVerify: false }
  } })
  h.page.onLoad()
  h.page.setData({ loading: false, rows: goods, address: address(), wechatPayEnabled: true })
  const first = h.page.quoteFreight(address())
  await h.page.submit()
  assert.equal(h.calls.length, 1)
  const currentAddress = address('9223372036854775755', 0)
  h.page.setData({ address: currentAddress })
  const second = h.page.quoteFreight(currentAddress)
  fresh.resolve({ productAmount: 24, freightAmount: 10, payAmount: 34 })
  await second
  old.resolve({ productAmount: 24, freightAmount: 6, payAmount: 30 })
  await first
  assert.equal(h.page.data.payTotal, '34.00')
  assert.equal(h.page.data.quoteReady, true)
  assert.equal(h.calls.filter(({ url }) => url === '/payment/checkVerify').length, 1)
})

test('旧短信风控响应晚到不能覆盖当前报价的验证要求', async () => {
  const oldVerify = deferred()
  let index = 0
  const h = harness('checkout', { respond: (options) => {
    if (options.url.endsWith('freight-quote')) return { productAmount: 24, freightAmount: 6, payAmount: 30 }
    if (options.url === '/payment/checkVerify') return ++index === 1 ? oldVerify.promise : { needVerify: true }
  } })
  h.page.setData({ rows: goods })
  const first = h.page.quoteFreight(address())
  await new Promise((resolve) => setImmediate(resolve))
  await h.page.quoteFreight(address('9223372036854775755', 0))
  oldVerify.resolve({ needVerify: false })
  await first
  assert.equal(h.page.data.needSmsVerify, true)
  assert.equal(h.page.data.quoteReady, true)
})

test('大额短信门禁保留，正确报价和验证码提交原始 ID 但不信任客户端金额', async () => {
  const submit = deferred()
  const h = harness('checkout', { respond: (options) => options.url === '/shop/orders' ? submit.promise : checkoutRespond({ needVerify: true })(options) })
  h.page.onLoad()
  await h.page.load()
  await h.page.submit()
  assert.ok(!h.calls.some(({ url }) => url === '/shop/orders'))
  h.page.setData({ smsCode: '123456' })
  const first = h.page.submit()
  await h.page.submit()
  const orders = h.calls.filter(({ url }) => url === '/shop/orders')
  assert.equal(orders.length, 1)
  assert.equal(orders[0].idempotencyKey, h.page.submitKey)
  assert.equal(orders[0].data.addressId, address().id)
  assert.equal(orders[0].data.items[0].productId, goods[0].productId)
  assert.equal(orders[0].data.smsCode, '123456')
  assert.ok(!Object.hasOwn(orders[0].data, 'payAmount'))
  submit.resolve({ order: { id: '9223372036854775788' } })
  await first
  await h.page.submit()
  assert.equal(h.calls.filter(({ url }) => url === '/shop/orders').length, 1, '创建成功后即使跳转尚未完成也不能再次创建')
  assert.equal(h.cleared(), 1)
})

test('商品数量或地址改变后旧报价不允许提交', async () => {
  const h = harness('checkout', { respond: checkoutRespond() })
  h.page.onLoad()
  await h.page.load()
  h.page.data.rows[0].quantity = 3
  await h.page.submit()
  assert.ok(!h.calls.some(({ url }) => url === '/shop/orders'))
})

test('页面离开后旧加载和报价不再恢复可支付状态', async () => {
  const pending = deferred()
  const h = harness('checkout', { respond: (options) => options.url === '/shop/addresses' ? pending.promise : { wechatPayEnabled: true } })
  h.page.onLoad()
  const loading = h.page.load()
  h.page.onHide()
  pending.resolve([address()])
  await loading
  assert.equal(h.page.data.quoteReady, false)
  assert.ok(!h.calls.some(({ url }) => url.endsWith('freight-quote')))
})

test('结算金额未知不伪装为商品小计，重算和提交按钮都有状态门禁', () => {
  const wxml = readFileSync(new URL('../pages/checkout/index.wxml', import.meta.url), 'utf8')
  assert.match(wxml, /bindtap="retryQuote"/)
  assert.match(wxml, /disabled="\{\{submitting \|\| loading \|\| quoteLoading \|\| !quoteReady\}\}"/)
  assert.doesNotMatch(wxml, /payTotal \|\| total/)
})

const flashId = '9223372036854775766'
const flashRow = () => ({
  activityState: 'ACTIVE',
  activity: { id: flashId, productId: goods[0].productId, skuId: goods[0].skuId, activityName: '测试活动', perUserLimit: 3, availableStock: 8, flashPrice: '9.00' },
  product: { id: goods[0].productId, productName: '活动商品', salePrice: '12.00', coverUrl: '' },
  sku: { id: goods[0].skuId, skuName: '活动规格' }
})

test('活动结算重新读取活动，只按真实活动价格报价并提交专用接口，不清购物车', async () => {
  const row = flashRow()
  const h = harness('checkout', { respond: (options) => {
    if (options.url === '/shop/flash-sales') return [row]
    if (options.url === `/shop/flash-sales/${flashId}/orders`) return { order: { id: '9223372036854775777' } }
    return checkoutRespond({ quote: { productAmount: '18.00', freightAmount: '6.00', payAmount: '24.00' } })(options)
  } })
  h.page.onLoad({ activityId: flashId, quantity: '2' })
  await h.page.onShow()
  assert.equal(h.page.data.rows.length, 1)
  assert.equal(h.page.data.rows[0].salePrice, '9.00')
  assert.equal(h.page.data.activityName, '测试活动')
  assert.equal(h.page.data.payTotal, '24.00')
  const quote = h.calls.find(({ url }) => url.endsWith('freight-quote'))
  assert.equal(quote.data.businessType, 'FLASH_SALE')
  assert.equal(quote.data.businessSourceId, flashId)
  assert.equal(quote.data.items[0].skuId, goods[0].skuId)
  await h.page.submit()
  const order = h.calls.find(({ url }) => url.endsWith(`${flashId}/orders`))
  assert.ok(order)
  assert.equal(order.data.businessSourceId, flashId)
  assert.equal(order.data.items[0].quantity, 2)
  assert.ok(!h.calls.some(({ url }) => url === '/shop/orders'))
  assert.equal(h.cleared(), 0)
})

test('活动结算登录回跳携带原始活动 ID 与数量，不变成普通结算', async () => {
  const h = harness('checkout', { loggedIn: false })
  h.page.onLoad({ activityId: flashId, quantity: '2' })
  await h.page.onShow()
  assert.equal(h.loginChecks[0], `/pages/checkout/index?activityId=${flashId}&quantity=2`)
  assert.equal(h.calls.length, 0)
})

test('不存在、未开始、结束、售罄、停用或超限活动禁止报价与创建普通订单', async () => {
  for (const state of ['MISSING', 'UPCOMING', 'ENDED', 'SOLD_OUT', 'DISABLED', 'EXCESS']) {
    const row = flashRow()
    row.activityState = state === 'EXCESS' ? 'ACTIVE' : state
    const h = harness('checkout', { respond: () => state === 'MISSING' ? [] : [row] })
    h.page.onLoad({ activityId: flashId, quantity: state === 'EXCESS' ? '4' : '1' })
    await h.page.load()
    assert.equal(h.page.data.quoteReady, false, state)
    assert.ok(h.page.data.loadError, state)
    await h.page.submit()
    assert.deepEqual(h.calls.map(({ url }) => url), ['/shop/flash-sales'])
  }
})

test('活动路由无效、数量不是正整数或已经失真 ID 不得降级为普通订单', async () => {
  for (const options of [{ activityId: '' }, { activityId: 'abc' }, { activityId: 9223372036854775766 },
    { activityId: flashId, quantity: '0' }, { activityId: flashId, quantity: '1.5' }, { activityId: flashId, quantity: '100' }]) {
    const h = harness('checkout')
    h.page.onLoad(options)
    await h.page.load()
    assert.equal(h.page.flashSaleMode, true)
    assert.equal(h.page.data.quoteReady, false)
    assert.ok(h.page.data.loadError)
    assert.equal(h.calls.length, 0)
  }
})

test('活动商品或 SKU 与活动信息不一致时禁止结算', async () => {
  for (const changed of ['product', 'sku', 'missingSku', 'invalidPrice']) {
    const row = flashRow()
    if (changed === 'product') row.product.id = '7'
    if (changed === 'sku') row.sku.id = '7'
    if (changed === 'missingSku') row.sku = null
    if (changed === 'invalidPrice') row.activity.flashPrice = 0
    const h = harness('checkout', { respond: () => [row] })
    h.page.onLoad({ activityId: flashId })
    await h.page.load()
    assert.equal(h.page.data.quoteReady, false)
    assert.ok(h.page.data.loadError)
    assert.equal(h.calls.length, 1)
  }
})

test('活动重新加载失败不能继续使用上一轮报价提交', async () => {
  let fail = false
  const h = harness('checkout', { respond: (options) => {
    if (options.url === '/shop/flash-sales') { if (fail) throw new Error('活动更新失败'); return [flashRow()] }
    return checkoutRespond()(options)
  } })
  h.page.onLoad({ activityId: flashId })
  await h.page.load()
  assert.equal(h.page.data.quoteReady, true)
  fail = true
  await h.page.load()
  await h.page.submit()
  assert.equal(h.page.data.quoteReady, false)
  assert.ok(!h.calls.some(({ method, url }) => method === 'POST' && url.endsWith('/orders')))
})

test('普通购物车已失真的商品数字 ID 不发送给服务器', async () => {
  const h = harness('checkout', { selected: [{ ...goods[0], productId: 9223372036854775766 }] })
  h.page.onLoad()
  await h.page.load()
  assert.equal(h.page.data.quoteReady, false)
  assert.equal(h.calls.length, 0)
  assert.match(h.page.data.loadError, /商品信息无效/)
})

test('创建响应中的订单 ID 已失真时不付款、不重复下单，引导去订单列表核对', async () => {
  const h = harness('checkout', { respond: (options) => options.url === '/shop/orders'
    ? { order: { id: 9223372036854775788 } } : checkoutRespond()(options) })
  h.page.onLoad()
  await h.page.load()
  await h.page.submit()
  await h.page.submit()
  assert.equal(h.calls.filter(({ url }) => url === '/shop/orders').length, 1)
  assert.equal(h.cleared(), 0)
  assert.match(h.modals[0].content, /订单标识异常/)
  h.modals[0].success()
  assert.equal(h.nav.at(-1).url, '/pages/orders/index')
})
