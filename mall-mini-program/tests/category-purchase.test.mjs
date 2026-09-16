import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { commerceEnv } from './helpers/commerce-env.mjs'
const require = createRequire(import.meta.url)
const product = require('../utils/category-product')
const detail = () => ({ product: { id: '1', productName: '测试商品', salePrice: 9, stock: 5, status: 1 }, skus: [] })
const event = (id) => ({ currentTarget: { dataset: { id } } })
const tick = () => new Promise(resolve => setImmediate(resolve))
function harness(respond = options => options.method === 'POST' ? { allowed: true } : detail()) {
  const e = commerceEnv(respond), page = e.page('category'), cart = e.load('utils/cart')
  return { ...e, page, get rows() { return cart.list() }, get tabHidden() { return e.tabHidden } }
}

test('分类真实销量与拆分价格，缺失销量不编造零和榜单', () => {
  const card = product.card({ id: '1', salePrice: 0, stock: 1, status: 1, salesCount: 8 })
  assert.equal(card.priceInteger, '0'); assert.equal(card.priceDecimal, '00'); assert.equal(card.salesText, '已售 8 件')
  assert.equal(product.card({}).salesText, '')
  assert.equal(product.card({salesCount: -1}).salesText, '')
})
test('新鲜价格及零价规格不回落到主商品价格', () => {
  const d = detail(); d.skus = [{id: '8',salePrice: 0,stock: 2}]
  assert.equal(product.purchase(d, '8', []).item.salePrice, 0)
  assert.throws(() => product.purchase(d, '', []), /选择/)
  d.product.status = 0; assert.throws(() => product.purchase(d, '8', []), /下架/)
})
test('同规格库存、跨规格限购拦截，不添加H5不存在的99件限制', () => {
  const d = detail(); d.product.purchaseLimit = 2
  assert.throws(() => product.purchase(d, null, [{productId: '1',skuId: '8',quantity: 2}]), /限购/)
  d.product.purchaseLimit = 0
  assert.throws(() => product.purchase(d, null, [{productId: '1',quantity: 5}]), /上限/)
  d.product.stock = 500
  assert.equal(product.purchase(d, null, [{productId: '1',quantity: 99}]).productQuantity, 100)
})
test('无规格商品核对详情后直接加购，不弹成功提示', async () => {
  const h = harness(); await h.page.quickAdd(event('1'))
  assert.equal(h.rows.length, 1); assert.equal(h.rows[0].salePrice, 9)
  assert.equal(h.notices.length, 0, '加购成功只更新购物车，不弹确认框')
})
test('多规格列表加购与H5一致：直接加入首个有库存规格，零价不回退', async () => {
  const d = detail(); d.skus = [{id:'8',skuName:'赠品',salePrice:0,stock:1},{id:'9',salePrice:19,stock:0}]
  const h = harness(options => options.method === 'POST' ? {allowed:true} : d); await h.page.quickAdd(event('1'))
  assert.equal(h.rows.length, 1); assert.equal(h.tabHidden, false)
  assert.equal(h.rows[0].skuId, '8'); assert.equal(h.rows[0].salePrice, 0)
  assert.equal(h.tabHidden, false); assert.equal(h.calls.length, 2)
})
test('登录用户历史限购不允许时不加入购物车', async () => {
  const h = harness(({method}) => method === 'POST' ? {allowed:false,message:'已达到累计限购'} : detail()); h.token('member')
  await h.page.quickAdd(event('1'))
  assert.equal(h.rows.length, 0); assert.equal(h.calls[1].params.quantity, 1)
  assert.ok(h.notices.includes('已达到累计限购'))
})
test('失败、错商品响应不会静默成功', async () => {
  const h = harness(() => ({product: {id: '2'}})); await h.page.quickAdd(event('1'))
  assert.equal(h.rows.length, 0); assert.ok(h.notices[0].includes('不一致'))
  assert.equal(h.page.addingId, '')
})
test('重复点击只请求一次，离开页面或换号后晚到响应不加购', async () => {
  for (const leave of [h => h.page.onHide(), h => h.token('changed')]) {
    let resolve; const h = harness(() => new Promise(r => { resolve = r }))
    const pending = h.page.quickAdd(event('1')); await h.page.quickAdd(event('1'))
    assert.equal(h.calls.length, 1); leave(h); resolve(detail()); await pending
    assert.equal(h.rows.length, 0)
  }
})
test('排序参数跟随完整分页，未提交输入不混入后续页', async () => {
  const h = harness(({params}) => ({list:[{id:String(params.pageNum)}],total:3,totalPage:3}))
  h.page.data.keyword = '原搜索'; h.page.data.searchedKeyword = '原搜索'; h.page.changeSort({currentTarget:{dataset:{mode:'price'}}}); await tick()
  h.page.data.keyword = '草稿'; await h.page.loadProducts(false)
  assert.equal(h.calls[1].params.sortMode, 'priceAsc'); assert.equal(h.calls[1].params.keyword, '原搜索')
  h.page.changeSort({currentTarget:{dataset:{mode:'price'}}}); await tick()
  assert.equal(h.calls.at(-1).params.sortMode, 'priceDesc'); assert.equal(h.calls.at(-1).params.pageNum, 1)
  assert.equal(h.calls.at(-1).params.keyword, '原搜索')
})
test('已选规格从详情中消失时不改为默认无规格商品', () => {
  assert.throws(() => product.purchase(detail(), '8', []), /规格已失效/)
})

const sortEvent = mode => ({ currentTarget: { dataset: { mode } } })
test('综合、销量及价格两方向沿用服务端全目录顺序，不在当前页再次重排', async () => {
  const orders = { default: ['3', '1', '2'], sales: ['2', '1', '3'], salesAsc: ['3', '1', '2'], priceAsc: ['3', '2', '1'], priceDesc: ['1', '2', '3'] }
  const h = harness(({ params }) => ({ list: orders[params.sortMode].map(id => ({ id, salePrice: 100 })), total: 3 }))
  for (const [mode, expected] of [['price', 'priceAsc'], ['price', 'priceDesc'], ['sales', 'sales'], ['sales', 'salesAsc'], ['sales', 'sales'], ['default', 'default']]) {
    h.page.changeSort(sortEvent(mode)); await tick()
    assert.equal(h.calls.at(-1).params.sortMode, expected)
    assert.equal(h.calls.at(-1).params.pageNum, 1)
    assert.deepEqual(h.page.data.products.map(p => p.id), orders[expected])
  }
  const count = h.calls.length
  h.page.changeSort(sortEvent('default')); await tick()
  assert.equal(h.calls.length, count, '重复综合点击不清空列表或重新加载')
})

test('换排序两次均回到顶部，加载下一页不重置滚动位置', async () => {
  const h = harness(({ params }) => ({ list: [{ id: String(params.pageNum) }], totalPage: 3, total: 3 }))
  const patches = [], originalSetData = h.page.setData
  h.page.setData = function (patch, done) { patches.push(patch); originalSetData.call(this, patch, done) }
  for (const top of [580, 920]) {
    h.page.onProductScroll({ detail: { scrollTop: top } })
    h.page.changeSort(sortEvent('price')); await tick()
    assert.equal(h.page.data.productScrollTop, 0)
    assert.ok(patches.some(p => p.productScrollTop === top))
  }
  patches.length = 0
  h.page.onProductScroll({ detail: { scrollTop: 600 } })
  await h.page.loadProducts(false)
  assert.equal(patches.some(p => 'productScrollTop' in p), false)
})

test('旧排序下一页及快速切换的晚到响应不覆盖当前综合列表', async () => {
  const pending = []
  const h = harness(options => new Promise(resolve => pending.push({ options, resolve })))
  h.page.changeSort(sortEvent('price'))
  pending[0].resolve({ list: [{ id: '1' }], total: 2, totalPage: 2 }); await tick()
  const oldPage = h.page.loadProducts(false)
  h.page.changeSort(sortEvent('sales'))
  h.page.changeSort(sortEvent('default'))
  pending[3].resolve({ list: [{ id: '9' }], total: 1 }); await tick()
  pending[2].resolve({ list: [{ id: '8' }], total: 1 }); await tick()
  pending[1].resolve({ list: [{ id: '2' }], total: 2, totalPage: 2 }); await oldPage
  assert.deepEqual(h.page.data.products.map(p => p.id), ['9'])
  assert.equal(h.page.data.sortMode, 'default'); assert.equal(h.page.data.loading, false)
  assert.equal(h.page.data.hasMore, false)
})

test('排序失败显示失败态，再点同一排序允许重试', async () => {
  let fail = true
  const h = harness(() => { if (fail) throw new Error('网络暂不可用'); return { list: [{ id: '1' }], total: 1 } })
  h.page.changeSort(sortEvent('default')); await tick()
  assert.equal(h.page.data.error, '网络暂不可用')
  fail = false; h.page.changeSort(sortEvent('default')); await tick()
  assert.equal(h.calls.length, 2); assert.equal(h.page.data.error, '')
  assert.deepEqual(h.page.data.products.map(p => p.id), ['1'])
})

test('排序按钮至少44px宽，销量和价格双箭头仅高亮当前方向，滚动位置绑定原生组件', () => {
  const view = readFileSync(new URL('../pages/category/index.wxml', import.meta.url), 'utf8')
  const styles = readFileSync(new URL('../pages/category/index.wxss', import.meta.url), 'utf8')
  assert.match(styles, /\.result-toolbar \.sort-tabs \.sort-tab \{[^}]*min-width:44px/)
  for (const mode of ['sales', 'salesAsc', 'priceAsc', 'priceDesc']) {
    assert.ok(view.includes(`sortMode === '${mode}' ? 'direction-active' : ''`))
  }
  assert.equal((view.match(/class="sort-arrows"/g) || []).length, 2)
  assert.match(view, /scroll-top="\{\{productScrollTop\}\}" bindscroll="onProductScroll"/)
})
test('列表文字加购与详情分开点击，不再挂额外规格弹层', () => {
  const view = readFileSync(new URL('../pages/category/index.wxml', import.meta.url), 'utf8')
  const styles = readFileSync(new URL('../pages/category/index.wxss', import.meta.url), 'utf8')
  assert.match(view, /catchtap="quickAdd"/); assert.doesNotMatch(view, /quick-cart.wxml/)
  assert.match(view, /立即加购/); assert.match(view, /class="category-quantity-stepper"/); assert.match(view, /catchtap="quickDecrease"/); assert.doesNotMatch(view, /近期销量|回购|好评率|榜第/)
  assert.match(styles, /quick-cart-button, \.sort-tabs \.sort-tab \{ min-height: 44px/)
  assert.doesNotMatch(styles, /\.quick-cart-button\[disabled\]/)
})
