import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'

const tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => { let resolve; const promise = new Promise(ok => { resolve = ok }); return { promise, resolve } }
function setup(respond = () => ({ list: [], total: 0 })) {
  const env = commerceEnv(respond, ''), home = env.page('home'), category = env.page('category')
  env.pages([home])
  env.wx.switchTab = options => {
    env.routes.push(options.url)
    home.onHide()
    env.pages([category])
    options.success()
    options.complete()
  }
  return { env, home, category }
}

test('首页搜索跳到原生分类标签，带中文/特殊字符，清掉旧分类排序并从第一页查询', async () => {
  const { env, home, category } = setup(() => ({ list: [{ id: '7', productName: '护理 & 礼盒' }], total: 1 }))
  category.setData({ active: '旧分类', keyword: '旧词', searchedKeyword: '旧词', sortMode: 'priceDesc', pageNum: 4, guideEnabled: true, guideTemplate: 'scenario', guideHasContent: true })
  home.setData({ keyword: '  护理 & 礼盒  ', activeCategory: '首页原分类' })
  home.search(); await tick()
  assert.deepEqual(env.routes, ['/pages/category/index'])
  assert.deepEqual(env.calls.map(c => c.params), [{ categoryName: '', keyword: '护理 & 礼盒', sortMode: 'default', status: 1, pageNum: 1, pageSize: 20 }])
  assert.equal(category.data.keyword, '护理 & 礼盒'); assert.equal(category.data.searchedKeyword, '护理 & 礼盒')
  assert.equal(category.data.browsingAll, true); assert.equal(category.data.products[0].id, '7')
  assert.equal(home.data.searchedKeyword, ''); assert.equal(home.data.activeCategory, '首页原分类')
  assert.deepEqual(Array.from(home.data.recentSearches), ['护理 & 礼盒'])
})

test('首次进入分类页时，迟到的分类数据不能冲掉首页带来的搜索词', async () => {
  const categories = deferred()
  const { env, home, category } = setup(({ url }) => url === '/shop/categories' ? categories.promise : { list: [{ id: '7', productName: '礼盒' }], total: 1 })
  category.onLoad()
  home.setData({ keyword: '礼盒' }); home.search(); await tick()
  categories.resolve([{ id: '1', categoryName: '护理' }]); await tick()
  assert.equal(category.data.keyword, '礼盒'); assert.equal(category.data.searchedKeyword, '礼盒')
  assert.equal(category.data.products[0].id, '7')
  assert.ok(env.calls.filter(c => c.url === '/shop/products').every(c => c.params.keyword === '礼盒' && c.params.categoryName === ''))
})

test('分类旧搜索的晚响应不会覆盖新搜索；空词进入全部商品而非旧分类', async () => {
  const first = deferred()
  let count = 0
  const { env, home, category } = setup(() => ++count === 1 ? first.promise : { list: [{ id: '2', productName: '全部新结果' }], total: 1 })
  category.setData({ keyword: '旧搜索', active: '旧分类', sortMode: 'sales' })
  const old = category.loadProducts()
  home.setData({ keyword: '   ' }); home.search(); await tick()
  first.resolve({ list: [{ id: '1', productName: '旧结果' }], total: 1 }); await old
  assert.equal(category.data.keyword, ''); assert.equal(category.data.active, ''); assert.equal(category.data.sortMode, 'default')
  assert.equal(category.data.browsingAll, true); assert.equal(category.data.products[0].id, '2')
  assert.deepEqual(Array.from(env.load('utils/search-history').list()), [])
})

test('跳分类失败有显眼弹窗、保留输入并可重试，跳转中重复点击只发起一次', () => {
  const env = commerceEnv(), home = env.page('home')
  let navigation, count = 0
  env.wx.switchTab = options => { navigation = options; count++ }
  home.setData({ keyword: '礼盒' }); home.search(); home.search()
  assert.equal(count, 1); assert.equal(home.searchNavigating, true)
  navigation.fail(); navigation.complete()
  assert.match(env.notices[0], /未能打开商品分类/); assert.equal(home.data.keyword, '礼盒'); assert.equal(home.searchNavigating, false)
  home.search(); assert.equal(count, 2); assert.equal(env.calls.length, 0)
  env.pages([]); navigation.success(); navigation.complete()
  assert.match(env.notices.at(-1), /分类页暂未就绪/)
})

test('历史/热门词、键盘确认与首页搜索按钮共用跳分类逻辑', async () => {
  const { env, home, category } = setup()
  home.applySearch({ currentTarget: { dataset: { keyword: '健康生活' } } }); await tick()
  assert.equal(env.routes[0], '/pages/category/index'); assert.equal(category.data.searchedKeyword, '健康生活')
  const template = readFileSync(new URL('../pages/home/index.wxml', import.meta.url), 'utf8')
  assert.match(template, /bindconfirm="search"/); assert.match(template, /bindtap="search"/)
  assert.match(template, /data-keyword="\{\{item\}\}" bindtap="applySearch"/)
})
