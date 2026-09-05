import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'

const require = createRequire(import.meta.url)
const format = require('../utils/format')
const legal = require('../utils/legal')
const theme = require('../utils/theme')
const plain = (value) => JSON.parse(JSON.stringify(value))
const event = (id) => ({ currentTarget: { dataset: { id } } })
const tick = () => new Promise((resolve) => setImmediate(resolve))
const pending = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }

function pageHarness(name, response = () => ({})) {
  let definition, loggedIn = true
  const calls = [], nav = [], logins = [], cart = []
  const mocks = {
    '../../utils/request': async (options) => { calls.push(plain(options)); return response(options) },
    '../../utils/format': format, '../../utils/legal': legal,
    '../../utils/theme': { pageData: () => ({}), apply: async () => ({}), sync() {} },
    '../../utils/auth': { requireLogin: (url) => { logins.push(url); return loggedIn } },
    '../../utils/cart': { add: (row) => cart.push(row), selectOnly() {} }
  }
  vm.runInNewContext(readFileSync(new URL(`../pages/${name}/index.js`, import.meta.url), 'utf8'), {
    Page: (value) => { definition = value }, require: (id) => { assert.ok(Object.hasOwn(mocks, id), id); return mocks[id] },
    wx: { navigateTo: (options) => nav.push(options.url), setNavigationBarTitle() {}, showToast() {}, stopPullDownRefresh() {} }
  })
  const page = { ...definition, data: plain(definition.data), setData(patch, done) {
    for (const [key, value] of Object.entries(plain(patch))) {
      const parts = key.replace(/\[(\d+)\]/g, '.$1').split('.'); let target = this.data
      for (const part of parts.slice(0, -1)) target = target[part]
      target[parts.at(-1)] = value
    }
    if (done) done()
  } }
  return { page, calls, nav, logins, cart, login: (value) => { loggedIn = value } }
}

test('公共编号保留长整数字符串，拒绝精度已丢失的数字和路径注入', () => {
  assert.equal(format.identifier('9223372036854775807'), '9223372036854775807')
  for (const value of [9007199254740992, 0, -1, '1e3', '1/../../2', '1?admin=1', undefined, Infinity]) assert.equal(format.identifier(value), '')
  assert.equal(format.identifier(123), '123')
})

test('分类分页直到后续商品，去重并按原查询翻页，不混入未提交的新关键词', async () => {
  const h = pageHarness('category', ({ params }) => ({ list: params.pageNum === 1 ? [{ id: '1' }, { id: '2' }] : [{ id: '2' }, { id: '3' }], total: 3, totalPage: 2 }))
  h.page.setData({ active: '护理', keyword: '面霜' })
  await h.page.loadProducts()
  h.page.onKeywordInput({ detail: { value: '新输入未搜索' } })
  await h.page.loadProducts(false)
  assert.equal(h.calls[1].params.keyword, '面霜')
  assert.equal(h.calls[1].params.categoryName, '护理')
  assert.equal(h.calls[1].params.pageNum, 2)
  assert.deepEqual(plain(h.page.data.products.map((row) => row.id)), ['1', '2', '3'])
  assert.equal(h.page.data.hasMore, false)
})

test('分类追加失败保留已加载商品且重试原页，不跳页', async () => {
  let fail = true
  const h = pageHarness('category', ({ params }) => {
    if (params.pageNum === 2 && fail) throw new Error('网络中断')
    return { list: [{ id: String(params.pageNum) }], total: 2, totalPage: 2 }
  })
  await h.page.loadProducts(); await h.page.loadProducts(false)
  assert.equal(h.page.data.products.length, 1); assert.equal(h.page.data.pageNum, 1)
  assert.equal(h.page.data.moreError, '网络中断')
  fail = false; await h.page.loadProducts(false)
  assert.equal(h.calls.at(-1).params.pageNum, 2); assert.equal(h.page.data.products.length, 2)
})

test('分类筛选请求倒序返回时旧结果不能覆盖新筛选', async () => {
  const old = pending(), fresh = pending()
  const h = pageHarness('category', ({ params }) => params.keyword === '旧' ? old.promise : fresh.promise)
  h.page.setData({ keyword: '旧' }); const first = h.page.loadProducts()
  h.page.setData({ keyword: '新' }); const second = h.page.loadProducts()
  fresh.resolve({ list: [{ id: '2' }], total: 1 }); await second
  old.resolve({ list: [{ id: '1' }], total: 1 }); await first
  assert.equal(h.page.data.products[0].id, '2'); assert.equal(h.page.data.loading, false)
})

test('外部搜索清除旧分类，外部分类清除旧搜索，查看全部解除导购预览', async () => {
  const h = pageHarness('category', () => ({ list: [], total: 0 }))
  h.page.setData({ active: '旧分类', keyword: '旧关键词' })
  h.page.applyKeyword('新关键词'); await tick()
  assert.equal(h.calls.at(-1).params.categoryName, '')
  h.page.applyCategory('新分类'); await tick()
  assert.equal(h.calls.at(-1).params.keyword, '')
  h.page.showAll(); await tick()
  assert.equal(h.page.data.browsingAll, true); assert.equal(h.page.data.active, '')
})

test('商城政策使用后台完整内容并替换主体，异常FAQ不崩溃', () => {
  const config = { privacyPolicy: '第一段\n{{companyName}} 联系 {{servicePhone}}\n最后一段', companyName: '测试商城主体', servicePhone: '客服电话', faqs: '[{"question":"如何退款","answer":"联系 {{companyName}}"}]' }
  assert.equal(legal.content('privacy', config), '第一段\n测试商城主体 联系 客服电话\n最后一段')
  assert.equal(legal.faqs(config)[0].answer, '联系 测试商城主体')
  assert.deepEqual(legal.faqs({ faqs: '{坏数据' }), [])
  assert.equal(legal.content('privacy', {}), '')
})

test('隐私页读取真实政策，拒绝可执行营业执照链接和未知页面类型', async () => {
  const h = pageHarness('legal', () => ({ privacyPolicy: '完整隐私内容', businessLicenseUrl: 'javascript:alert(1)' }))
  h.page.onLoad({ type: 'privacy' }); await tick()
  assert.equal(h.calls[0].url, '/shop/legal-config')
  assert.equal(h.page.data.content, '完整隐私内容')
  assert.equal(h.page.data.config.businessLicenseUrl, '')
  h.page.open({ currentTarget: { dataset: { type: '__proto__' } } }); assert.equal(h.nav.length, 0)
})

test('公告列表和全文为真实接口，长ID不截断，无效编号不发请求', async () => {
  const id = '9223372036854775799'
  const h = pageHarness('notices', () => ({ id, content: '完整公告' }))
  h.page.onLoad({ id }); await tick()
  assert.equal(h.calls[0].url, `/shop/notices/${id}`); assert.equal(h.page.data.notice.content, '完整公告')
  const invalid = pageHarness('notices'); invalid.page.onLoad({ id: '../private' }); await tick()
  assert.equal(invalid.calls.length, 0); assert.equal(invalid.page.data.error, '公告编号不正确')
})

test('活动入口只传活动ID与受限数量，不放入普通购物车；结束活动不可下单', async () => {
  const id = '9223372036854775700'
  const h = pageHarness('campaign', () => [
    { activity: { id, availableStock: 10, perUserLimit: 2, flashPrice: '1.20' }, activityState: 'ACTIVE', product: { id: '12' } },
    { activity: { id: '15', availableStock: 10, perUserLimit: 2 }, activityState: 'ENDED', product: { id: '13' } }
  ])
  await h.page.load()
  h.page.quantityChange({ currentTarget: { dataset: { id } }, detail: { value: '1' } })
  h.page.buy(event(id)); h.page.buy(event('15'))
  assert.deepEqual(h.nav, [`/pages/checkout/index?activityId=${id}&quantity=2`])
  assert.equal(h.cart.length, 0)
  h.login(false); h.page.buy(event(id)); assert.equal(h.nav.length, 1)
})

test('商品加载未完成或已下架时不能加入购物车，免费SKU不被普通价格覆盖', async () => {
  const h = pageHarness('product')
  assert.equal(h.page.purchaseItem(), null)
  h.page.setData({ loading: false, product: { id: '10', status: 0 }, soldOut: true })
  assert.equal(h.page.purchaseItem(), null)
  h.page.setData({ product: { id: '10', status: 1, salePrice: 50 }, soldOut: false, skus: [{ id: '11', salePrice: 0, stock: 5 }] })
  h.page.addToCart(); assert.equal(h.cart[0].salePrice, 0)
})

for (const name of ['messages', 'payout', 'message-detail']) {
  test(`${name} onLoad/onShow不重复跳登录，登录返回能恢复加载`, async () => {
    const h = pageHarness(name, ({ url }) => url === '/shop/messages' ? { list: [], pageNum: 1 } : url.includes('unread') ? {} : url.includes('/messages/') ? { id: '123' } : [])
    h.login(false); h.page.onLoad({ id: '123' }); await h.page.onShow()
    assert.equal(h.logins.length, 1); assert.equal(h.calls.length, 0)
    h.login(true); await h.page.onShow()
    assert.ok(h.calls.length > 0); assert.equal(h.page.data.loading, false)
  })
}

test('消息按服务端受控类型直达原订单或账号页，不执行消息URL', () => {
  const h = pageHarness('message-detail'), id = '9223372036854775766'
  h.page.setData({ message: { targetType: 'ORDER', targetId: id } }); h.page.openTarget()
  h.page.setData({ message: { targetType: 'AFTER_SALE', targetId: '111', targetParentId: id } }); h.page.openTarget()
  h.page.setData({ message: { targetType: 'ORDER', targetId: '../other' } }); h.page.openTarget()
  h.page.setData({ message: { targetType: 'ACCOUNT_SECURITY' } }); h.page.openTarget()
  h.page.setData({ message: { targetType: 'javascript:bad', url: 'https://other.invalid' } }); h.page.openTarget()
  assert.deepEqual(h.nav, [`/pages/order-detail/index?id=${id}`, `/pages/order-detail/index?id=${id}`, '/pages/orders/index', '/pages/account-security/index'])
})

test('消息切分类失败清理旧列表与页码，不能带着旧分类第2页跳过新分类首页', async () => {
  let fail = true
  const h = pageHarness('messages', ({ url }) => {
    if (url === '/shop/messages' && fail) throw new Error('新分类失败')
    return url === '/shop/messages' ? { list: [{ id: '5' }], pageNum: 1, totalPage: 1 } : []
  })
  h.page.setData({ category: 'ORDER_LOGISTICS', rows: [{ id: '1' }], pageNum: 2, totalPage: 3, loading: false })
  h.page.selectCategory({ currentTarget: { dataset: { key: 'ACCOUNT_SECURITY' } } }); await tick()
  assert.equal(h.page.data.rows.length, 0); assert.equal(h.page.data.pageNum, 0)
  assert.equal(h.page.data.error, '新分类失败')
  fail = false; await h.page.load(true)
  assert.equal(h.calls.filter((call) => call.url === '/shop/messages').at(-1).params.pageNum, 1)
  assert.equal(h.page.data.rows[0].id, '5')
})

test('后台短HEX和RGB主题转换为微信原生合法颜色，透明色合成且拒绝CSS注入', () => {
  assert.equal(theme.normalizeColor('#f80'), '#ff8800')
  assert.equal(theme.normalizeColor('rgb(1, 2, 3)'), '#010203')
  assert.equal(theme.normalizeColor('rgba(0, 0, 0, 0.5)'), '#808080')
  assert.equal(theme.normalizeColor('#00000000'), '#ffffff')
  assert.equal(theme.normalizeColor('red; background:url(x)'), '#e7193f')
  const palette = theme.pageData({ themeColor: 'rgb(1, 2, 3)' })
  assert.equal(palette.themeColor, '#010203'); assert.match(palette.themeStyle, /--brand: #010203/)
})

test('小程序上传排除构建输入和本地互通测试，保留运行时加密库', () => {
  const config = JSON.parse(readFileSync(new URL('../project.config.json', import.meta.url), 'utf8'))
  const ignored = config.packOptions.ignore.filter((item) => item.type === 'folder').map((item) => item.value)
  assert.ok(ignored.includes('scripts')); assert.ok(ignored.includes('tests'))
  assert.equal(ignored.includes('vendor'), false)
  const css = readFileSync(new URL('../custom-tab-bar/index.wxss', import.meta.url), 'utf8')
  assert.doesNotMatch(css, /\.nav-item\s+image/)
})
