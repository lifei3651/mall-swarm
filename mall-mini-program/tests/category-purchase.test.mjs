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
test('列表文字加购与详情分开点击，不再挂额外规格弹层', () => {
  const view = readFileSync(new URL('../pages/category/index.wxml', import.meta.url), 'utf8')
  const styles = readFileSync(new URL('../pages/category/index.wxss', import.meta.url), 'utf8')
  assert.match(view, /catchtap="quickAdd"/); assert.doesNotMatch(view, /quick-cart.wxml/)
  assert.match(view, /立即加购/); assert.doesNotMatch(view, /近期销量|回购|好评率|榜第/)
  assert.match(styles, /quick-cart-button, \.sort-tabs \.sort-tab \{ min-height: 44px/)
  assert.doesNotMatch(styles, /\[disabled\]/)
})
