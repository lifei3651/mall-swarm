import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'
const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8')
test('购物车标题和件数使用独立左对齐区域，空车不误套按钮样式', () => {
  const view = read('pages/cart/index.wxml'), css = read('pages/cart/index.wxss')
  assert.match(view, /class="cart-heading-title">购物车/)
  assert.match(view, /class="cart-heading-count">\{\{totalCount\}\} 件/)
  assert.match(view, /wx:if="\{\{rows.length\}\}" class="cart-header-actions"/)
  assert.doesNotMatch(css, /cart-header\s*>\s*view:last-child/)
  assert.match(css, /\.cart-heading\s*\{[^}]*min-width: 0;[^}]*text-align: left/)
  assert.match(css, /\.cart-heading-count\s*\{[^}]*display: block;[^}]*margin: 6rpx 0 0/)
  assert.match(css, /\.cart-header-actions button\s*\{[^}]*flex: none;[^}]*width: 100rpx/)
})
test('数量校验不插入文字、不替换加号，保留互斥和结算门禁', () => {
  const view = read('pages/cart/index.wxml')
  assert.doesNotMatch(view, /正在核对最新价格|loading="\{\{quantityChecking|quantityChecking === item.key/)
  assert.match(view, /disabled="\{\{checkoutChecking\}\}" aria-label="增加商品数量">＋/)
  assert.match(view, /!count \|\| checkError/)
  assert.doesNotMatch(view, /quantityChecking|\|\| checking/)
  assert.match(view, /wx:if="\{\{checkError\}\}"/)
})
test('加减数量期间保留商品列表及管理状态，服务端限购校验继续执行', async () => {
  let finish
  const detail = { product: { id: '1', status: 1, productName: '商品', salePrice: 12, stock: 20, purchaseLimit: 10 }, skus: [] }
  const env = commerceEnv(({ method }) => method === 'POST' ? new Promise(resolve => { finish = resolve }) : detail)
  const cart = env.load('utils/cart'), page = env.page('cart')
  cart.add({ productId: '1', salePrice: 12, quantity: 1 })
  page.renderRows(cart.list()); page.setData({ manageMode: true })
  const event = delta => ({ currentTarget: { dataset: { key: '1:0', delta } } })
  const pending = page.quantity(event(1)); await new Promise(resolve => setImmediate(resolve))
  assert.equal(page.data.rows.length, 1); assert.equal(page.data.totalCount, 1)
  assert.equal(page.data.manageMode, true); assert.equal(page.quantityChecking, '1:0')
  await page.quantity(event(1)); assert.equal(env.calls.filter(c => c.method === 'POST').length, 1)
  finish({ allowed: true }); await pending
  assert.equal(page.data.rows.length, 1); assert.equal(page.data.totalCount, 2); assert.equal(page.quantityChecking, '')
  await page.quantity(event(-1)); assert.equal(page.data.totalCount, 1); assert.equal(page.data.manageMode, true)
  assert.equal(env.notices.length, 0)
  page.setData({ checking: true }); await page.checkout()
  assert.equal(env.routes.length, 0, '后台校验未结束时仍阻止结算')
  assert.equal(env.notices.length, 0, '后台校验期间不另弹进度文字')
})

for (const manageMode of [false, true]) test((manageMode ? '管理态' : '普通态') + '连续增加/减少只补丁数量金额，图片和整套导航不重设', async () => {
  let finish
  const detail = { product: { id: '1', status: 1, productName: '商品', coverUrl: 'https://lingqimall.com/item.png', salePrice: 12, stock: 20 }, skus: [] }
  const env = commerceEnv(({ method }) => method === 'POST' ? new Promise(resolve => { finish = resolve }) : detail)
  const cart = env.load('utils/cart'), page = env.page('cart')
  const badges = []
  page.getTabBar = () => ({ refresh: () => assert.fail('不刷新整套导航'), refreshCartCount: () => badges.push(cart.count()) })
  cart.add({ productId: '1', quantity: 2, salePrice: 12 })
  await page.refresh()
  if (manageMode) {
    page.toggleManage(); await new Promise(resolve => setImmediate(resolve))
    page.toggleAll({ detail: { value: ['selected'] } }); await new Promise(resolve => setImmediate(resolve))
  }
  const originalRows = page.data.rows, originalRow = page.data.rows[0], patches = []
  const setData = page.setData
  page.setData = function(patch) { patches.push(patch); setData.call(this, patch) }
  const event = delta => ({ currentTarget: { dataset: { key: '1:0', delta } } })
  for (let quantity = 3; quantity <= 4; quantity++) {
    const pending = page.quantity(event(1)); await new Promise(resolve => setImmediate(resolve))
    assert.equal(page.data.rows, originalRows)
    finish({ allowed: true }); await pending
    assert.equal(page.data.rows[0], originalRow)
    assert.equal(page.data.rows[0].quantity, quantity)
    assert.equal(page.data.total, (12 * quantity).toFixed(2))
    assert.equal(page.data.manageMode, manageMode); assert.equal(page.data.allSelected, true)
  }
  await page.quantity(event(-1))
  assert.equal(page.data.rows, originalRows); assert.equal(page.data.total, '36.00')
  assert.equal(page.data.rows[0].quantity, 3)
  assert.ok(patches.some(p => p['rows[0].quantity'] === 4))
  assert.ok(patches.every(p => !Object.keys(p).some(k => k === 'rows' || k.endsWith('.coverUrl'))))
  assert.equal(badges.at(-1), 3); assert.equal(env.notices.length, 0)
})

test('局部刷新仍展示服务端价格/库存变化，删除商品时才重建列表', async () => {
  const detail = { product: { id: '1', status: 1, productName: '商品', salePrice: 12, stock: 20 }, skus: [] }
  const env = commerceEnv(() => detail), cart = env.load('utils/cart'), page = env.page('cart')
  cart.add({ productId: '1', quantity: 2, salePrice: 12 })
  await page.refresh()
  const originalRows = page.data.rows
  detail.product.salePrice = 15; detail.product.stock = 1
  await page.refresh()
  assert.equal(page.data.rows, originalRows)
  assert.equal(page.data.total, '30.00'); assert.equal(page.data.rows[0].priceText, '15.00')
  assert.match(page.data.rows[0].unavailable, /库存/)
  detail.product.stock = 20; await page.refresh()
  assert.equal(page.data.rows[0].unavailable, '')
  cart.remove('1:0'); await page.refresh()
  assert.equal(page.data.rows.length, 0); assert.equal(page.data.totalCount, 0); assert.equal(page.data.total, '0.00')
})

test('短时校验不会让数量/有选中商品的结算按钮变淡，最小数量仍弱化', () => {
  const view = read('pages/cart/index.wxml'), css = read('pages/cart/index.wxss')
  assert.doesNotMatch(css, /\.counter button\[disabled\]/)
  assert.match(css, /\.counter button\.is-minimum\s*\{ opacity: \.4/)
  assert.doesNotMatch(view, /quantityChecking|has-items/)
  assert.doesNotMatch(view, /<checkbox\b/, '选择状态由页面控制，忽略在途点击不会触发原生自动勾选')
  assert.match(view, /aria-role="checkbox" aria-checked="\{\{item.selected\}\}"/)
  for (const button of view.match(/<button\b[^>]*>/g)) assert.match(button, /hover-class="none"/)
})

test('慢加购锁只在逻辑层：管理/勾选/删除/结算都阻断，不切换任何可见状态', async () => {
  let finish
  const detail = { product: { id: '1', status: 1, productName: '商品', salePrice: 12, stock: 30 }, skus: [] }
  const env = commerceEnv(({ method }) => method === 'POST' ? new Promise(resolve => { finish = resolve }) : detail)
  const cart = env.load('utils/cart'), page = env.page('cart')
  cart.add({ productId: '1', quantity: 2, salePrice: 12 }); await page.refresh()
  page.toggleManage(); page.toggleAll({})
  const patches = [], oldSetData = page.setData
  page.setData = function(patch) { patches.push(patch); oldSetData.call(this, patch) }
  const snapshot = JSON.stringify(cart.list()), view = JSON.stringify(page.data)
  const pending = page.quantity({ currentTarget: { dataset: { key: '1:0', delta: 1 } } }); await new Promise(resolve => setImmediate(resolve))
  page.toggleManage(); page.toggle({ currentTarget: { dataset: { key: '1:0' } } }); page.toggleAll({})
  page.removeSelected(); page.clearCart(); await page.checkout()
  assert.equal(JSON.stringify(cart.list()), snapshot); assert.equal(JSON.stringify(page.data), view)
  assert.equal(patches.length, 0); assert.equal(env.notices.length, 0); assert.equal(env.routes.length, 0)
  finish({ allowed: true }); await pending
  assert.equal(patches.length, 1, '批准后一次局部补丁，不反复切换控制状态')
  assert.equal(page.data.rows[0].quantity, 3)
})

test('成功加购仅读取当前商品一次；管理选择不读取；减少只核对同商品规格', async () => {
  const env = commerceEnv(({ url, method }) => method === 'POST' ? { allowed: true } : { product: { id: url.split('/').pop(), status: 1, stock: 20, salePrice: 12 }, skus: [] })
  const cart = env.load('utils/cart'), page = env.page('cart')
  for (const id of ['1', '2']) cart.add({ productId: id, quantity: 2, salePrice: 12 })
  await page.refresh(); env.calls.length = 0
  page.toggleManage(); page.toggleAll({}); page.toggle({ currentTarget: { dataset: { key: '2:0' } } })
  assert.equal(env.calls.length, 0)
  const event = delta => ({ currentTarget: { dataset: { key: '1:0', delta } } })
  await page.quantity(event(1))
  assert.deepEqual(env.calls.map(c => c.url), ['/shop/products/1', '/shop/products/1/purchase-limit/check'])
  env.calls.length = 0; await page.quantity(event(-1))
  assert.deepEqual(env.calls.map(c => c.url), ['/shop/products/1'])
})

test('旧库存提示在校验期间保留，不先消失再出现；服务端确认恢复才移除', async () => {
  let finish
  const detail = { product: { id: '1', status: 1, stock: 1, salePrice: 12 }, skus: [] }
  const env = commerceEnv(() => new Promise(resolve => { finish = resolve })), cart = env.load('utils/cart'), page = env.page('cart')
  cart.add({ productId: '1', quantity: 2, salePrice: 12 })
  let pending = page.refresh(); finish(detail); await pending
  const warning = page.data.rows[0].unavailable; assert.match(warning, /库存/)
  pending = page.refresh(); assert.equal(page.data.rows[0].unavailable, warning)
  finish(detail); await pending; assert.equal(page.data.rows[0].unavailable, warning)
  pending = page.refresh(); finish({ ...detail, product: { ...detail.product, stock: 5 } }); await pending
  assert.equal(page.data.rows[0].unavailable, '')
})
