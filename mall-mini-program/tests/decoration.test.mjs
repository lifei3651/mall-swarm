import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
const require = createRequire(import.meta.url)
const display = require('../utils/display-config')
const wrap = (data) => ({ extraConfigJson: JSON.stringify(data) })
const types = (items) => items.map((item) => item.type)

test('装修扩展兼容对象、字符串及损坏配置，安全回退', () => {
  for (const raw of [null, '', '[1]', 'null', '{bad', 1]) assert.deepEqual(display.extra({ extraConfigJson: raw }), {})
  assert.deepEqual(display.extra(wrap({ showTrustStrip: 0 })), { showTrustStrip: 0 })
  assert.doesNotThrow(() => display.home(null))
  assert.equal(display.home({ layoutTemplate: '<style>' }).layoutTemplate, 'standard')
})
test('所有开关统一处理布尔、数字及字符串，不把字符串0视为开启', () => {
  for (const value of [false, 0, '0', 'false']) assert.equal(display.toggle(value), false)
  for (const value of [true, 1, '1', 'true']) assert.equal(display.toggle(value, false), true)
})
test('当前客户装修：分类总开关关闭，轮播公告关闭，只保留商品模块', () => {
  const config = { ...wrap({ homeModules: ['notice', 'category', 'banner', 'live', 'newArrivals', 'products', 'trust'].map((type, i) => ({ type, sort: i + 1, enabled: ['category', 'products'].includes(type) })), showTrustStrip: 0 }), showHomeCategories: 0 }
  assert.deepEqual(types(display.home(config).homeModules), ['products'])
})
test('首页模块按后台顺序渲染；缺失模块补默认，未知和重复类型不执行', () => {
  const config = wrap({ showTrustStrip: 1, homeModules: [{ type: 'products', sort: 1 }, { type: 'banner', sort: 10 }, { type: 'notice', sort: 2, enabled: '0' }, { type: 'products', sort: 100 }, { type: 'evil', enabled: true }] })
  const modules = display.home(config).homeModules
  assert.equal(modules[0].type, 'products')
  assert.equal(modules.at(-1).type, 'banner')
  assert.equal(modules.filter((m) => m.type === 'products').length, 1)
  assert.ok(!types(modules).includes('evil') && !types(modules).includes('notice'))
})
test('直播新品及信任条遵循独立总开关，旧discovery配置兼容', () => {
  const config = { ...wrap({ homeModules: [{ type: 'discovery', enabled: '0' }], showTrustStrip: 1 }), newArrivalsEnabled: 1 }
  assert.ok(!types(display.home(config).homeModules).includes('live'))
  assert.ok(!types(display.home(config).homeModules).includes('newArrivals'))
  assert.ok(types(display.home(config).homeModules).includes('trust'))
  assert.ok(!types(display.home({ liveSquareEnabled: 0 }).homeModules).includes('live'))
})
test('四种版型都能读取，版型本身不会重新开启隐藏模块', () => {
  for (const layoutTemplate of ['standard', 'product-focus', 'category-focus', 'campaign-feed']) {
    const result = display.home({ layoutTemplate, ...wrap({ homeModules: [{ type: 'banner', enabled: false }] }) })
    assert.equal(result.layoutTemplate, layoutTemplate)
    assert.ok(!types(result.homeModules).includes('banner'))
  }
})
test('九项详细配色和主题圆角全部映射，非法CSS不能注入页面', () => {
  const palette = display.palette({ productTemplate: 'soft-purple', themeColor: '#7c3aed', displayConfig: wrap({ colors: { priceColor: '#123456', pageBg: 'rgba(10, 20, 30, 0.5)', buttonBg: '#fff;display:none', textColor: 'url(https://bad.test)' } }) })
  assert.equal(palette.priceColor, '#123456')
  assert.equal(palette.pageBg, 'rgba(10, 20, 30, 0.5)')
  assert.equal(palette.buttonBg, '#7c3aed')
  assert.equal(palette.textColor, '#202735')
  assert.equal(palette.radius, '40rpx')
  for (const invalid of ['rgba(256,0,0,1)', 'rgb(0,0,0,1)', 'rgba(0,0,0)', '#fff;background:red', 'expression(foo)']) assert.equal(display.color(invalid, 'fallback'), 'fallback')
})
test('底部导航改名、分类隐藏和订单开启生效，必要交易入口和固定路径不可覆盖', () => {
  const nav = display.navigation(wrap({ bottomNavIndependent: 1, bottomNav: [{ type: 'home', label: '逛商城', enabled: false, path: 'https://bad.test' }, { type: 'category', enabled: '0' }, { type: 'orders', enabled: '1' }, { type: 'cart', enabled: false }, { type: 'profile', enabled: false }] }))
  assert.deepEqual(types(nav), ['home', 'cart', 'orders', 'profile'])
  assert.equal(nav[0].label, '逛商城')
  assert.equal(nav[0].path, '/pages/home/index')
})
test('旧版商品优先导航兼容，独立导航标识优先尊重分类关闭', () => {
  assert.ok(types(display.navigation({ layoutTemplate: 'product-focus', showBottomCategoryNav: 0 })).includes('category'))
  assert.ok(!types(display.navigation({ layoutTemplate: 'product-focus', showBottomCategoryNav: 0, ...wrap({ bottomNavIndependent: 1 }) })).includes('category'))
})
test('分类三种导购模板及九个子模块逐项读取，直写字段优先于扩展', () => {
  const names = ['primaryCategories', 'subcategories', 'hotProducts', 'heroCategories', 'shelves', 'recommendedProducts', 'scenarios', 'quickEntries', 'popularProducts']
  for (const name of names) {
    const field = `categoryGuide${name[0].toUpperCase()}${name.slice(1)}Enabled`
    const result = display.category({ layoutTemplate: 'category-focus', [field]: '0', ...wrap({ categoryGuideTemplate: 'scenario', categoryGuideModules: { [name]: true } }) })
    assert.equal(result.guide[name], false)
    assert.equal(result.guideTemplate, 'scenario')
    assert.equal(result.guideEnabled, true)
  }
  assert.equal(display.category({ ...wrap({ categoryGuideModules: { primaryCategories: 0, subcategories: 0, hotProducts: 0 } }) }).guideHasContent, false)
})

function load(file, mocks = {}) {
  let page
  const module = { exports: {} }
  const filename = new URL(file, import.meta.url)
  const localRequire = createRequire(filename)
  vm.runInNewContext(readFileSync(filename, 'utf8'), { module, require: (id) => mocks[id] || localRequire(id), Page: (value) => { page = value }, Component: (value) => { page = value }, ...mocks.globals }, { filename: filename.pathname })
  return page || module.exports
}
function instance(definition) {
  return { ...definition, data: structuredClone(definition.data), setData(patch) { Object.assign(this.data, patch) } }
}
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }

test('首页再次显示会重新取装修；失败保留旧数据，不清空正在浏览的商城', async () => {
  let home = { brandName: '商城', displayConfig: wrap({ homeModules: [{ type: 'banner', enabled: false }] }) }
  let fail = false, calls = 0, notice = ''
  const page = instance(load('../pages/home/index.js', {
    '../../utils/share': { prepare: async () => null, hide() {} },
    '../../utils/request': async ({ url }) => { calls++; if (fail) throw new Error('offline'); return url === '/shop/home' ? structuredClone(home) : { list: [{ id: 1, salePrice: 2 }] } },
    '../../utils/theme': { pageData: () => ({}), remember: () => ({}), sync: () => {} },
    globals: { wx: { setNavigationBarTitle() {}, showToast({ title }) { notice = title } } }
  }))
  await page.loadHome()
  assert.ok(!types(page.data.homeModules).includes('banner'))
  home.displayConfig = wrap({ homeModules: [{ type: 'banner', enabled: true }] })
  page.onShow(); await page.refreshing
  assert.ok(types(page.data.homeModules).includes('banner'))
  assert.equal(calls, 4)
  fail = true
  page.onShow(); await page.refreshing
  assert.equal(page.data.products.length, 1)
  assert.equal(page.data.error, '')
  assert.match(notice, /更新失败/)
})
test('首页并发刷新合并，不让旧请求覆盖新装修', async () => {
  const pending = deferred(); let calls = 0
  const page = instance(load('../pages/home/index.js', {
    '../../utils/request': ({ url }) => { calls++; return url === '/shop/home' ? pending.promise : Promise.resolve({ list: [] }) },
    '../../utils/theme': { pageData: () => ({}), remember: () => ({}), sync: () => {} }, globals: { wx: { setNavigationBarTitle() {} } }
  }))
  const first = page.loadHome(), second = page.loadHome()
  pending.resolve({ brandName: '最新商城' })
  await Promise.all([first, second])
  assert.equal(calls, 2)
  assert.equal(page.data.home.brandName, '最新商城')
})
test('主题缓存会过期重取，直接打开其他页面也获取完整装修', async () => {
  let now = 100000, calls = 0
  const app = { globalData: {} }
  const themes = load('../utils/theme.js', {
    './request': async () => { calls++; return { themeColor: '#123456', displayConfig: wrap({ colors: { pageBg: '#112233' }, bottomNav: [{ type: 'orders', enabled: true }] }) } },
    globals: { Date: { now: () => now }, getApp: () => app, getCurrentPages: () => [], wx: { getStorageSync() {}, setStorageSync() {}, setTabBarStyle() {} } }
  })
  const page = instance({ data: {} })
  await Promise.all([themes.apply(page), themes.apply(page)])
  assert.equal(calls, 1)
  assert.match(page.data.themeStyle, /--canvas: #112233/)
  assert.ok(types(page.data.bottomNav).includes('orders'))
  await themes.apply(page); assert.equal(calls, 1)
  now += 10001; await themes.apply(page); assert.equal(calls, 2)
})
test('导航按固定页面跳转，订单仍是普通页面，未知配置不可导航', () => {
  const calls = []
  const definition = load('../custom-tab-bar/index.js', { globals: { wx: { switchTab: (arg) => calls.push(['tab', arg.url]), navigateTo: (arg) => calls.push(['page', arg.url]) } } })
  const component = { ...definition.methods, data: { active: 'home', bottomNav: display.navigation(wrap({ bottomNav: [{ type: 'orders', enabled: true }] })) } }
  for (const type of ['category', 'orders', 'evil', 'home']) component.navigate({ currentTarget: { dataset: { type } } })
  assert.deepEqual(calls, [['tab', '/pages/category/index'], ['page', '/pages/orders/index']])
  component.data.hidden = true
  component.navigate({ currentTarget: { dataset: { type: 'category' } } })
  assert.equal(calls.length, 2, '登录弹窗打开时不得穿透触发底部导航')
})
test('分类快速切换时忽略旧响应，避免名称与商品列表错配', async () => {
  const requests = [deferred(), deferred()]; let i = 0
  const page = instance(load('../pages/category/index.js', { '../../utils/request': () => requests[i++].promise }))
  const a = page.loadProducts(), b = page.loadProducts()
  requests[1].resolve({ list: [{ id: 2, productName: '新分类' }] }); await b
  requests[0].resolve({ list: [{ id: 1, productName: '旧分类' }] }); await a
  assert.equal(page.data.products[0].productName, '新分类')
})

test('较早的主题请求不能覆盖首页刚刚加载的新装修', async () => {
  const pending = deferred(), app = { globalData: {} }
  const themes = load('../utils/theme.js', { './request': () => pending.promise, globals: { getApp: () => app, getCurrentPages: () => [], wx: { getStorageSync() {}, setStorageSync() {}, setTabBarStyle() {} } } })
  const page = instance({ data: {} })
  const request = themes.apply(page)
  themes.remember({ themeColor: '#123456' })
  pending.resolve({ themeColor: '#999999' })
  await request
  assert.equal(page.data.themeColor, '#123456')
  assert.equal(app.globalData.brand.themeColor, '#123456')
})
test('品牌文化空字段不会渲染null，关闭的独立页面不继续获取内容', async () => {
  const paths = []
  let enabled = true
  const page = instance(load('../pages/store-content/index.js', {
    '../../utils/request': async ({ url }) => { paths.push(url); return url === '/shop/home' ? { brandCultureEnabled: enabled } : { enabled: true, title: null, subtitle: null, content: null, detailImages: [] } },
    '../../utils/theme': { pageData: () => ({}), remember: () => ({}) }, globals: { wx: { setNavigationBarTitle() {} } }
  }))
  page.contentType = 'culture'
  await page.load()
  assert.equal(page.data.culture.subtitle, '')
  assert.equal(page.data.culture.content, '')
  enabled = false; paths.length = 0
  await page.load()
  assert.match(page.data.error, /暂未开放/)
  assert.deepEqual(paths, ['/shop/home'])
})
test('直播详情拒绝网页及不安全视频地址，不把后台链接当脚本执行', async () => {
  for (const url of ['javascript:alert(1)', 'http://example.com/live.mp4', 'https://example.com/page', 'https://example.com/live.m3u8?token=test']) {
    const page = instance(load('../pages/store-content/index.js', {
      '../../utils/request': async ({ url: path }) => path === '/shop/home' ? { displayConfig: { liveSquareEnabled: 1 } } : { roomState: 'LIVE', room: { watchUrl: url }, products: [] },
      '../../utils/theme': { pageData: () => ({}), remember: () => ({}) }, globals: { wx: { setNavigationBarTitle() {} } }
    }))
    page.contentType = 'live'; page.roomId = 1
    await page.load()
    assert.equal(Boolean(page.data.videoUrl), url.includes('.m3u8'))
  }
})
