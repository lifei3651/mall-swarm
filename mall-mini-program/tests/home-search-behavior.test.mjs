import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'

const tick = () => new Promise(resolve => setImmediate(resolve))
const deferred = () => { let resolve; const promise = new Promise(ok => { resolve = ok }); return { promise, resolve } }
const event = keyword => ({ currentTarget: { dataset: { keyword } } })

test('首页搜索按H5原页展示，保留当前分类，不切换底部标签，并滚动到搜索结果', async () => {
  const env = commerceEnv(() => ({ list: [{ id: '7', productName: '护理 & 礼盒' }], total: 1 }), ''), home = env.page('home')
  const scrolls = []; env.wx.pageScrollTo = options => scrolls.push(options)
  home.setData({ keyword: '  护理 & 礼盒  ', activeCategory: '护理套装', searchFocused: true })
  await home.search()
  assert.deepEqual(env.routes, [])
  assert.deepEqual(env.calls.map(call => call.params), [{ status: 1, pageNum: 1, pageSize: 60, keyword: '护理 & 礼盒', categoryName: '护理套装' }])
  assert.equal(home.data.searchedKeyword, '护理 & 礼盒'); assert.equal(home.data.keyword, '护理 & 礼盒')
  assert.equal(home.data.activeCategory, '护理套装'); assert.equal(home.data.products[0].id, '7')
  assert.equal(home.data.searchFocused, false); assert.equal(scrolls[0].selector, '#home-product-section')
  assert.deepEqual(Array.from(home.data.recentSearches), ['护理 & 礼盒'])
})

test('只点输入框显示历史，不查询、不跳分类；输入清除不删历史或立即提交', () => {
  const env = commerceEnv(), home = env.page('home'), history = env.load('utils/search-history')
  history.remember('礼盒'); home.setData({ keyword: '礼盒', searchedKeyword: '已提交词' })
  home.focusSearch()
  assert.equal(home.data.searchFocused, true); assert.deepEqual(Array.from(home.data.recentSearches), ['礼盒'])
  home.clearKeyword()
  assert.equal(home.data.keyword, ''); assert.equal(home.data.searchedKeyword, '已提交词')
  assert.equal(home.data.searchFocused, true); assert.deepEqual(Array.from(history.list()), ['礼盒'])
  assert.equal(env.calls.length, 0); assert.equal(env.routes.length, 0)
})

test('历史/热门词按H5清掉分类后原页搜索，空搜索也不跳分类或保存空历史', async () => {
  const env = commerceEnv(() => ({ list: [] })), home = env.page('home')
  home.setData({ activeCategory: '原分类' }); await home.applySearch(event('健康生活'))
  assert.equal(home.data.activeCategory, ''); assert.equal(home.data.searchedKeyword, '健康生活')
  home.setData({ keyword: '   ' }); await home.search()
  assert.equal(home.data.keyword, ''); assert.equal(home.data.searchedKeyword, '')
  assert.deepEqual(Array.from(env.load('utils/search-history').list()), ['健康生活'])
  assert.equal(env.routes.length, 0)
})

test('重复点击同一搜索只请求一次，新关键词与离开页面阻断旧结果回写', async () => {
  const old = deferred(), fresh = deferred(); let count = 0
  const env = commerceEnv(() => ++count === 1 ? old.promise : fresh.promise), home = env.page('home')
  home.setData({ keyword: '旧词' }); const first = home.search(); home.search()
  assert.equal(env.calls.length, 1)
  home.setData({ keyword: '新词' }); const second = home.search()
  fresh.resolve({ list: [{ id: '2', productName: '新结果' }] }); await second
  old.resolve({ list: [{ id: '1', productName: '旧结果' }] }); await first
  assert.equal(home.data.products[0].id, '2'); assert.equal(home.data.searchedKeyword, '新词')
  const late = deferred(); const e = commerceEnv(() => late.promise), p = e.page('home')
  p.setData({ keyword: '礼盒' }); const pending = p.search(); p.onHide()
  late.resolve({ list: [{ id: '3' }] }); await pending
  assert.equal(p.data.products.length, 0); p.search(); assert.equal(e.calls.length, 1)
})

test('搜索失败在原页显眼弹窗，保留关键词并可重试', async () => {
  let fail = true
  const env = commerceEnv(() => { if (fail) throw Error('网络暂不可用，请重试'); return { list: [{ id: '9' }] } }), home = env.page('home')
  home.setData({ keyword: '礼盒' }); await home.search()
  assert.match(env.notices[0], /网络暂不可用/); assert.equal(home.data.keyword, '礼盒')
  assert.equal(home.data.productsLoading, false); assert.equal(env.routes.length, 0)
  fail = false; await home.retryProducts(); assert.equal(home.data.products[0].id, '9')
})

test('清空历史有确认和取消，防重复弹窗，只删除搜索记录且重新打开仍为空', async () => {
  const env = commerceEnv(), home = env.page('home'), history = env.load('utils/search-history')
  history.remember('礼盒'); history.remember('健康生活')
  env.storage.set('mall_mini_cart', [{ productId: '7' }]); home.focusSearch()
  let modal, count = 0; env.wx.showModal = options => { modal = options; count++ }
  home.blurSearch(); home.clearSearchHistory(); home.clearSearchHistory()
  assert.equal(count, 1); assert.match(modal.title, /清空搜索历史/); assert.equal(modal.showCancel, true)
  modal.success({ confirm: false }); modal.complete?.(); await tick()
  assert.deepEqual(Array.from(history.list()), ['健康生活', '礼盒'])
  home.clearSearchHistory(); modal.success({ confirm: true }); modal.complete?.(); await tick()
  assert.deepEqual(Array.from(history.list()), []); assert.deepEqual(Array.from(home.data.recentSearches), [])
  home.focusSearch(); assert.deepEqual(Array.from(home.data.recentSearches), [])
  assert.deepEqual(env.storage.get('mall_mini_cart'), [{ productId: '7' }])
  assert.equal(env.storage.get('mall_mini_access_token'), 'member')
  assert.equal(env.calls.length, 0); assert.equal(env.routes.length, 0)
})

test('清空失败准确提示并保留记录，不假报成功；离页后的旧确认不删除新历史', async () => {
  const env = commerceEnv(), home = env.page('home'), history = env.load('utils/search-history')
  history.remember('礼盒'); home.focusSearch()
  env.wx.removeStorageSync = () => { throw Error('storage failed') }
  home.clearSearchHistory(); await tick()
  assert.ok(env.notices.some(text => /搜索历史清空失败/.test(text)))
  assert.deepEqual(Array.from(history.list()), ['礼盒']); assert.deepEqual(Array.from(home.data.recentSearches), ['礼盒'])
  const e = commerceEnv(), p = e.page('home'), h = e.load('utils/search-history')
  let dialog; e.wx.showModal = options => { dialog = options }
  h.remember('旧词'); p.focusSearch(); p.clearSearchHistory(); p.onHide()
  h.remember('新词'); dialog.success({ confirm: true }); dialog.complete?.()
  assert.deepEqual(Array.from(h.list()), ['新词', '旧词'])
})

test('模板提供独立清空历史和清除输入按钮，搜索按钮/键盘确认/历史词统一原页搜索', () => {
  const template = readFileSync(new URL('../pages/home/index.wxml', import.meta.url), 'utf8')
  assert.match(template, /bindconfirm="search"/); assert.match(template, /bindtap="search"/)
  assert.match(template, /data-keyword="\{\{item\}\}" bindtap="applySearch"/)
  assert.match(template, /catchtap="clearSearchHistory"[^>]*>清空历史<\/button>/)
  assert.match(template, /catchtap="clearKeyword"/)
  assert.match(template, /aria-label="清除搜索内容"/)
  assert.match(template, /searchedKeyword \? '搜索结果'/)
})

test('查看全部商品仍是明确切换分类的入口，不随首页搜索修正被移除', () => {
  const env = commerceEnv(), home = env.page('home'), category = env.page('category')
  let shown = false; category.showAll = () => { shown = true }
  env.wx.switchTab = options => { env.routes.push(options.url); env.pages([category]); options.success() }
  home.allProducts()
  assert.deepEqual(env.routes, ['/pages/category/index']); assert.equal(shown, true)
})

test('搜索词标题与词条分层，原生按钮显式收宽，长词仅视觉省略', async () => {
  const template = readFileSync(new URL('../pages/home/index.wxml', import.meta.url), 'utf8')
  const css = readFileSync(new URL('../pages/home/index.wxss', import.meta.url), 'utf8')
  assert.equal((template.match(/class="search-tags"/g) || []).length, 2)
  assert.equal((template.match(/class="search-tag" size="mini"/g) || []).length, 2)
  assert.equal((template.match(/aria-label="搜索{{item}}"/g) || []).length, 2)
  const tags = css.match(/\.search-tags \{([^}]+)\}/)[1]
  const button = css.match(/\.search-tags \.search-tag \{([^}]+)\}/)[1]
  assert.match(tags, /flex-direction:row/); assert.match(tags, /flex-wrap:wrap/)
  assert.match(button, /width:auto/); assert.match(button, /min-width:0/)
  assert.match(button, /flex:0 1 auto/); assert.match(button, /margin:0/)
  assert.match(button, /height:64rpx/); assert.match(button, /font-weight:400/)
  assert.match(css, /\.search-tag-label[^}]+text-overflow:ellipsis; white-space:nowrap/)
  const keyword = '护理礼盒'.repeat(20)
  const env = commerceEnv(() => ({ list: [] })), page = env.page('home')
  await page.applySearch(event(keyword))
  assert.equal(env.calls[0].params.keyword, keyword)
  assert.equal(page.data.keyword, keyword)
  assert.equal(env.load('utils/search-history').list()[0], keyword)
})
