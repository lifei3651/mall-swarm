import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/views/HomeView.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .+$/gm, '')
const deferred = () => { let resolve, reject; const promise = new Promise((ok, no) => { resolve = ok; reject = no }); return { promise, resolve, reject } }

function setup(respond = () => ({ data: { list: [] } })) {
  const store = new Map(), calls = [], routes = [], scrolls = [], timers = new Map(); let cleanup, timerId = 0
  const storage = { getItem: key => store.get(key) ?? null, setItem: (key, value) => store.set(key, value), removeItem: key => store.delete(key) }
  const context = {
    ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }), watch() {}, nextTick: async () => {},
    onMounted() {}, onUnmounted: fn => { cleanup = fn }, useRoute: () => ({ fullPath: '/' }), useRouter: () => ({ push: value => routes.push(value) }),
    useCart: () => ({}), localStorage: storage,
    window: { setTimeout: fn => { timers.set(++timerId, fn); return timerId }, clearTimeout: id => timers.delete(id), clearInterval() {} },
    listProducts: async query => { calls.push(query); return respond(query) }, getHome: async () => ({ data: {} }), applyBrandConfig() {},
  }
  const page = new Function(...Object.keys(context), `${script}\nreturn { query, products, searchFocused, recentSearches, searchInput, productSection, loading, submitSearch, applySearch, clearKeyword, requestClearHistory, cancelClearHistory, confirmClearHistory, historyConfirmVisible, searchNotice, productError, searchedKeyword, retryProducts, focusSearch, scheduleHideSuggestions, clearFilter }`)(...Object.values(context))
  page.searchInput.value = { blur() {}, focus: () => page.focusSearch() }
  page.productSection.value = { scrollIntoView: value => scrolls.push(value) }
  return { page, store, calls, routes, scrolls, storage, timers, cleanup: () => cleanup() }
}

test('H5首页原页搜索/历史热词/输入清除与原生约定一致', async () => {
  const { page, calls, routes, scrolls } = setup(() => ({ data: { list: [{ id: 7, productName: '礼盒' }] } }))
  page.query.value = { keyword: '  礼盒  ', categoryName: '护理套装' }
  await page.submitSearch()
  assert.equal(calls[0].keyword, '礼盒'); assert.equal(calls[0].categoryName, '护理套装'); assert.equal(calls[0].pageSize, 60)
  assert.equal(page.query.value.keyword, '礼盒'); assert.equal(page.searchedKeyword.value, '礼盒')
  assert.equal(page.products.value[0].id, 7); assert.equal(scrolls.length, 1); assert.deepEqual(routes, [])
  await page.applySearch('健康生活'); assert.equal(calls[1].categoryName, '')
  page.clearKeyword(); assert.equal(page.query.value.keyword, ''); assert.equal(calls.length, 2)
  assert.equal(page.searchedKeyword.value, '健康生活'); assert.equal(page.searchFocused.value, true)
})

test('H5清空历史需确认，取消保留，确认只删历史键且重开仍为空', async () => {
  const { page, store, calls } = setup()
  store.set('shop_recent_searches', JSON.stringify(['礼盒', '健康生活'])); store.set('shop_cart', 'keep-cart'); store.set('shop_session', 'keep-session')
  page.focusSearch(); page.requestClearHistory(); assert.equal(page.historyConfirmVisible.value, true)
  page.cancelClearHistory(); assert.equal(page.historyConfirmVisible.value, false)
  assert.deepEqual(JSON.parse(store.get('shop_recent_searches')), ['礼盒', '健康生活'])
  page.requestClearHistory(); page.confirmClearHistory()
  assert.equal(page.historyConfirmVisible.value, false); assert.deepEqual(page.recentSearches.value, [])
  page.focusSearch(); assert.deepEqual(page.recentSearches.value, [])
  assert.equal(store.get('shop_cart'), 'keep-cart'); assert.equal(store.get('shop_session'), 'keep-session'); assert.equal(calls.length, 0)
})

test('H5循环模板的数组引用可滚动到结果，损坏历史不会阻断聚焦', async () => {
  const { page, store, scrolls, calls } = setup()
  page.productSection.value = [page.productSection.value]
  store.set('shop_recent_searches', 'invalid-json'); page.focusSearch()
  assert.equal(page.searchFocused.value, true); assert.equal(calls.length, 0)
  store.set('shop_recent_searches', '[" 礼盒 ",null,7,"", "礼盒", "健康生活"]'); page.focusSearch()
  assert.deepEqual(page.recentSearches.value, ['礼盒', '健康生活'])
  page.query.value.keyword = '礼盒'; await page.submitSearch()
  assert.equal(scrolls.length, 1); assert.equal(page.productError.value, '')
})

test('H5清空存储失败显示明确错误，保留记录，不误报成功', () => {
  const { page, store, storage } = setup(); store.set('shop_recent_searches', '["礼盒"]'); page.focusSearch()
  storage.removeItem = () => { throw Error('blocked') }
  page.requestClearHistory(); page.confirmClearHistory()
  assert.match(page.searchNotice.value, /搜索历史清空失败/); assert.deepEqual(page.recentSearches.value, ['礼盒'])
  assert.equal(store.get('shop_recent_searches'), '["礼盒"]')
})

test('H5历史保存失败不中断商品搜索，最多五条去重且空词不记录', async () => {
  const { page, storage, calls } = setup(); storage.setItem = () => { throw Error('quota') }
  for (const word of ['1','2','3','4','5','6','3',' ']) { page.query.value.keyword = word; await page.submitSearch() }
  assert.equal(calls.length, 8); assert.deepEqual(page.recentSearches.value, ['3','6','5','4','2'])
})

test('H5重复同词提交合并；新查询/离页阻断旧搜索结果和错误回写', async () => {
  const old = deferred(), fresh = deferred(); let count = 0
  const { page, calls, cleanup } = setup(() => ++count === 1 ? old.promise : fresh.promise)
  page.query.value.keyword = '旧词'; const first = page.submitSearch(); page.submitSearch(); assert.equal(calls.length, 1)
  page.query.value.keyword = '新词'; const second = page.submitSearch()
  fresh.resolve({ data: { list: [{ id: 2 }] } }); await second; old.reject(Error('旧错误')); await first
  assert.equal(page.products.value[0].id, 2); assert.equal(page.searchNotice.value, '')
  const late = deferred(), e = setup(() => late.promise); e.page.query.value.keyword = '礼盒'
  const pending = e.page.submitSearch(); e.cleanup(); late.reject(Error('离页错误')); await pending
  assert.equal(e.page.searchNotice.value, ''); cleanup()
})

test('H5搜索失败有弹窗与原位重试，不当作无商品', async () => {
  let fail = true
  const { page, routes } = setup(() => { if (fail) throw Error('搜索服务暂不可用'); return { data: { list: [{ id: 2 }] } } })
  page.query.value.keyword = '礼盒'; await page.submitSearch()
  assert.match(page.productError.value, /搜索服务暂不可用/); assert.match(page.searchNotice.value, /搜索服务暂不可用/)
  assert.equal(page.loading.value, false); assert.equal(page.query.value.keyword, '礼盒')
  fail = false; await page.retryProducts(); assert.equal(page.products.value[0].id, 2); assert.equal(page.productError.value, ''); assert.deepEqual(routes, [])
})

test('H5确认弹窗出现前清理失焦计时；卸载后旧确认不清记录', () => {
  const { page, store, timers, cleanup } = setup(); store.set('shop_recent_searches', '["礼盒"]'); page.focusSearch()
  page.scheduleHideSuggestions({ currentTarget: { contains: () => false } }); assert.equal(timers.size, 1)
  page.requestClearHistory(); assert.equal(timers.size, 0)
  cleanup(); page.confirmClearHistory(); assert.equal(store.get('shop_recent_searches'), '["礼盒"]')
})

test('H5提供独立非提交清除按钮、清空历史确认和持久可见的错误弹窗', () => {
  assert.match(source, /type="button"[^>]*aria-label="清除搜索内容"[^>]*@click="clearKeyword"/)
  assert.match(source, /type="button"[^>]*@click="requestClearHistory"[^>]*>清空历史/)
  assert.match(source, /<ConfirmDialog[\s\S]*?:visible="historyConfirmVisible"/)
  assert.match(source, /@confirm="confirmClearHistory"/); assert.match(source, /@cancel="cancelClearHistory"/)
  assert.match(source, /:visible="!!searchNotice"/); assert.match(source, /:show-cancel="false"/)
  assert.match(source, /v-else-if="productError"/)
})

test('H5词条按内容收宽换行，手机不能把标签文字当搜索图标文案隐藏', async () => {
  assert.equal((source.match(/class="search-tags"/g) || []).length, 2)
  assert.equal((source.match(/class="search-tag" type="button"/g) || []).length, 2)
  assert.match(source, /\.search-tags[^}]+flex-wrap:wrap/)
  assert.match(source, /\.search-tags \.search-tag[^}]+width:auto; min-width:0; max-width:100%/)
  assert.match(source, /\.search-tag-label[^}]+text-overflow:ellipsis; white-space:nowrap/)
  assert.doesNotMatch(source, /\.home-search button > span\s*\{\s*display:\s*none/)
  assert.doesNotMatch(source, /\.home-search button span\s*\{\s*display:\s*none/)
  assert.match(source, /\.home-search > button > span\s*\{\s*display:\s*none/)
  const { page, calls } = setup()
  const keyword = '护理礼盒'.repeat(20)
  await page.applySearch(keyword)
  assert.equal(calls[0].keyword, keyword)
  assert.equal(page.query.value.keyword, keyword)
  assert.equal(page.recentSearches.value[0], keyword)
})
