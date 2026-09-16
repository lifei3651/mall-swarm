import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'
import { runMiniScript } from './helpers/run-mini-script.mjs'
const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8')
const event = { currentTarget: { dataset: { id: '1' } } }
const tick = () => new Promise(resolve => setImmediate(resolve))

test('首页和分类加购后原位显示数量器，临时请求锁不传入按钮状态', () => {
  for (const name of ['home', 'category']) {
    const source = read('pages/' + name + '/index.wxml')
    const button = source.split('\n').find(line => line.includes('class="' + (name === 'home' ? 'home-quick-cart' : 'quick-cart-button')))
    assert.ok(button)
    assert.match(button, /disabled="\{\{item.soldOut\}\}"/)
    assert.match(button, /hover-class="none"/)
    assert.match(button, /item.soldOut \? 'is-disabled' : ''/)
    assert.doesNotMatch(button, /loading=|加购中|addingId/)
    assert.match(source, /item\.cartQuantity > 0/)
    assert.match(source, /catchtap="quickDecrease"/)
    assert.match(source, /\{\{item\.cartQuantity\}\}/)
  }
})

test('商品详情加购与立即购买保留互斥锁，但不再互相改变按钮外观', () => {
  const source = read('pages/product/index.wxml')
  const addButton = source.match(/<button class="add-cart-button"[^>]*>/)?.[0] || ''
  const buyButton = source.match(/<button class="primary-button"[^>]*bindtap="buyNow"[^>]*>/)?.[0] || ''
  assert.match(addButton, /disabled="\{\{soldOut\}\}"/)
  assert.match(addButton, /aria-busy="\{\{purchaseAction === 'cart'\}\}"/)
  assert.doesNotMatch(addButton, /purchasePending|loading=/)
  assert.match(buyButton, /disabled="\{\{soldOut\}\}"/)
  assert.match(buyButton, /aria-busy="\{\{purchaseAction === 'buy'\}\}"/)
  assert.doesNotMatch(buyButton, /purchasePending|正在核对/)
  assert.match(read('pages/product/index.js'), /if \(this\.data\.purchasePending \|\| this\.purchaseInactive\) return/)
})

for (const name of ['home', 'category']) test(name + '：慢请求和连续加购只更新数量，不重刷列表/整套导航', async () => {
  let approve
  const detail = { product: { id: '1', status: 1, productName: '商品', stock: 20, salePrice: 12 }, skus: [] }
  const env = commerceEnv(({method}) => method === 'POST' ? new Promise(resolve => { approve = resolve }) : detail)
  const page = env.page(name), cart = env.load('utils/cart')
  page.setData({ products: [{ id: '1', productName: '商品' }] })
  const products = page.data.products, patches = [], badges = []
  const original = page.setData
  page.setData = function(patch) { patches.push(patch); original.call(this, patch) }
  page.getTabBar = () => ({
    refresh: () => assert.fail('不能重设主题和整套导航'),
    refreshCartCount: () => badges.push(cart.count()),
  })
  for (let count = 1; count <= 2; count++) {
    const pending = page.quickAdd(event); await tick()
    assert.equal(page.data.products, products)
    assert.equal(cart.count(), count - 1, '验证完成前不虚增数量')
    await page.quickAdd(event)
    assert.equal(env.calls.filter(c => c.method === 'POST').length, count, '在途重复点击不重复加购')
    approve({ allowed: true }); await pending
    assert.equal(cart.count(), count)
    assert.equal(page.addingId, '')
    assert.equal(page.data.products, products)
  }
  assert.deepEqual(badges, [1, 2])
  assert.deepEqual(JSON.parse(JSON.stringify(patches)), [
    {'products[0].cartQuantity': 1},
    {'products[0].cartQuantity': 2},
  ], '只局部更新对应商品数量，不重刷商品列表')
  assert.equal(env.notices.length, 0)
})

for (const name of ['home', 'category']) test(name + '：数量减到零后恢复立即加购，购物车角标同步', async () => {
  const detail = { product: { id: '1', status: 1, productName: '商品', stock: 20, salePrice: 12 }, skus: [] }
  const env = commerceEnv(({method}) => method === 'POST' ? { allowed: true } : detail)
  const page = env.page(name), cart = env.load('utils/cart'), badges = []
  page.setData({ products: [{ id: '1', productName: '商品', cartQuantity: 0 }] })
  page.getTabBar = () => ({ refreshCartCount: () => badges.push(cart.count()) })
  await page.quickAdd(event)
  assert.equal(page.data.products[0].cartQuantity, 1)
  page.quickDecrease(event)
  assert.equal(page.data.products[0].cartQuantity, 0)
  assert.equal(cart.list().length, 0)
  assert.deepEqual(badges, [1, 0])
})

test('导航数量更新仅提交cartCount，数量未变不重复setData', () => {
  let definition, count = 1
  runMiniScript(read('custom-tab-bar/index.js'), {
    Component: value => { definition = value },
    require: name => name.endsWith('/cart') ? { count: () => count }
      : name.endsWith('/theme') ? { pageData: () => ({ bottomNav: ['original'] }) } : {},
  })
  const patches = [], tab = { data: { cartCount: 0, bottomNav: ['original'] }, setData(patch) { patches.push(patch); Object.assign(this.data, patch) } }
  definition.methods.refreshCartCount.call(tab)
  definition.methods.refreshCartCount.call(tab)
  count = 2; definition.methods.refreshCartCount.call(tab)
  assert.deepEqual(JSON.parse(JSON.stringify(patches)), [{cartCount: 1}, {cartCount: 2}])
  assert.deepEqual(tab.data.bottomNav, ['original'])
})
