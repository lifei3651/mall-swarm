import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'
import { runMiniScript } from './helpers/run-mini-script.mjs'
const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8')
const event = { currentTarget: { dataset: { id: '1' } } }
const tick = () => new Promise(resolve => setImmediate(resolve))

test('首页和分类加购不切换灰色/文字/转圈，保留禁用锁并关闭默认点击灰闪', () => {
  for (const name of ['home', 'category']) {
    const button = read('pages/' + name + '/index.wxml').split('\n').find(line => line.includes('catchtap="quickAdd"'))
    assert.ok(button)
    assert.match(button, /disabled="\{\{item.soldOut \|\| !!addingId\}\}"/)
    assert.match(button, /hover-class="none"/)
    assert.match(button, /item.soldOut \? 'is-disabled' : ''/)
    assert.doesNotMatch(button, /loading=|加购中|addingId \? 'is-disabled'/)
  }
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
    assert.equal(page.data.addingId, '')
    assert.equal(page.data.products, products)
  }
  assert.deepEqual(badges, [1, 2])
  assert.ok(patches.every(patch => Object.keys(patch).every(key => key === 'addingId')))
  assert.equal(env.notices.length, 0)
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
