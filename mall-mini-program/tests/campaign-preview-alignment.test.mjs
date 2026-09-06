import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'
import { runMiniScript } from './helpers/run-mini-script.mjs'
const require = createRequire(import.meta.url)
const { campaignIndex, decorateCampaignProducts } = require('../utils/campaign-display')
const now = Date.parse('2026-09-06T10:00:00Z')
const product = { id: '1', status: 1, productName: '活动商品', salePrice: 99 }
const activity = (patch = {}) => ({ activity: { id: '10', productId: '1', status: 1, availableStock: 3,
  flashPrice: 19, startTime: '2026-09-06T09:00:00Z', endTime: '2026-09-06T11:00:00Z', ...patch }, product, activityState: 'ACTIVE' })

test('没有活动时只显示普通商品，不保留活动价格、标签或独立空标题', () => {
  const cards = decorateCampaignProducts([product], [], 'campaign-feed', now)
  assert.equal(cards[0].campaign, null)
  assert.equal(cards[0].priceText, '99.00')
  const template = readFileSync(new URL('../pages/home/index.wxml', import.meta.url), 'utf8')
  assert.doesNotMatch(template, /<text>限时活动<\/text>/)
  assert.match(template, /item\.campaign/)
})

test('真实活动匹配对应商品，不给所有普通商品加活动示意；进行中优先', () => {
  const upcoming = { ...activity({ id: '11', startTime: '2026-09-06T10:30:00Z' }), activityState: 'UPCOMING' }
  const cards = decorateCampaignProducts([product, { ...product, id: '2' }], [upcoming, activity()], 'campaign-feed', now)
  assert.equal(cards[0].priceText, '19.00')
  assert.equal(cards[0].campaign.id, '10')
  assert.equal(cards[0].campaign.countdown, '距结束 01:00:00')
  assert.equal(cards[1].campaign, null)
  assert.equal(cards[1].priceText, '99.00')
  assert.equal(decorateCampaignProducts([product], [activity()], 'standard', now)[0].campaign, null)
})

test('未启用、售罄、结束、下架、不匹配、无效价格与日期都不制造活动', () => {
  const rows = [activity({ status: 0 }), activity({ availableStock: 0 }), activity({ endTime: '2026-09-06T10:00:00Z' }),
    activity({ productId: '2' }), activity({ flashPrice: null }), activity({ flashPrice: 'NaN' }),
    activity({ startTime: 'invalid' }), { ...activity(), product: { ...product, status: 0 } },
    { ...activity(), activityState: 'DISABLED' }, { ...activity(), activityState: 'SOLD_OUT' }]
  assert.equal(campaignIndex(rows, now).size, 0)
})

test('倒计时跨开始/结束点更新状态，活动失效恢复普通售价；长编号不丢精度', () => {
  const row = activity({ id: '9223372036854775807', startTime: '2026-09-06T10:30:00Z' })
  row.activityState = 'UPCOMING'
  assert.equal(campaignIndex([row], now).get('1').actionLabel, '去看看')
  assert.equal(campaignIndex([row], now + 1800000).get('1').id, '9223372036854775807')
  assert.equal(campaignIndex([row], now + 1800000).get('1').actionLabel, '去抢购')
  assert.equal(decorateCampaignProducts([product], [row], 'campaign-feed', now + 3600000)[0].priceText, '99.00')
})

test('后台与小程序活动展示规则保持完全同源逻辑', () => {
  const mini = readFileSync(new URL('../utils/campaign-display.js', import.meta.url), 'utf8')
  const admin = readFileSync(new URL('../../mall-distribution-admin/src/utils/campaignDisplay.js', import.meta.url), 'utf8')
  assert.equal(mini.replace('module.exports =', 'export'), admin)
})

test('活动页循环使用有效的顶层编号，不再使用原生不支持的嵌套key', async () => {
  const row = activity({ perUserLimit: 1 })
  const env = commerceEnv(() => [row])
  const page = env.page('campaign'); await page.load()
  assert.equal(page.data.rows[0].id, '10')
  const template = readFileSync(new URL('../pages/campaign/index.wxml', import.meta.url), 'utf8')
  assert.match(template, /wx:key="id"/)
  assert.doesNotMatch(template, /wx:key="activity.id"/)
})

test('倒计时只更新变化字段；结束恢复原价并停表，隐藏页面不继续刷新', () => {
  let definition, callback, clock = now, scheduled = 0
  const rows = [activity()], products = [product]
  runMiniScript(readFileSync(new URL('../pages/home/index.js', import.meta.url), 'utf8'), {
    Page: page => { definition = page }, wx: {},
    require: id => id.endsWith('/campaign-display') ? { decorateCampaignProducts: (items, activities, layout) => decorateCampaignProducts(items, activities, layout, clock) }
      : id.endsWith('/theme') ? { pageData: () => ({}) } : {},
    setTimeout: fn => { callback = fn; scheduled++; return 1 }, clearTimeout() { callback = null }
  })
  const patches = [], page = { ...definition, campaignClockActive: true, baseProducts: products,
    data: { layoutTemplate: 'campaign-feed', campaigns: rows, products: decorateCampaignProducts(products, rows, 'campaign-feed', clock) },
    setData(patch) { patches.push(patch); for (const [key, value] of Object.entries(patch)) this.data.products[0][key.split('.').at(-1)] = value }
  }
  page.startCampaignClock(); clock += 1000; callback()
  assert.deepEqual(Object.keys(patches[0]), ['products[0].campaign'])
  clock = now + 3600000; callback()
  assert.equal(page.data.products[0].campaign, null)
  assert.equal(page.data.products[0].priceText, '99.00')
  assert.equal(callback, null)
  page.campaignClockActive = false; page.startCampaignClock()
  assert.equal(scheduled, 2)
})

test('首页真实加载：活动卡进入对应活动页，普通商品仍进入详情，不产生购物车或订单写入', async () => {
  const row = activity({ startTime: new Date(Date.now() - 3600000).toISOString(), endTime: new Date(Date.now() + 3600000).toISOString() })
  const env = commerceEnv(({ url }) => url === '/shop/home' ? { displayConfig: { layoutTemplate: 'campaign-feed' } }
    : url === '/shop/products' ? { list: [product, { ...product, id: '2' }] } : [row])
  const home = env.page('home')
  await home.loadHome()
  assert.equal(home.data.products[0].priceText, '19.00')
  assert.equal(home.data.products[1].priceText, '99.00')
  home.openProduct({ currentTarget: { dataset: { id: '1' } } })
  home.openProduct({ currentTarget: { dataset: { id: '2' } } })
  assert.deepEqual(env.routes, ['/pages/campaign/index?id=10', '/pages/product/index?id=2'])
  assert.ok(env.calls.every(call => !call.method || call.method === 'GET'))
  assert.equal(env.notices.length, 0)
  home.onHide()
})

test('刷新无活动或读取失败时清除旧活动价，失败须弹窗并保留重试入口', async () => {
  let mode = 'active'
  const env = commerceEnv(({ url }) => {
    if (url === '/shop/home') return { displayConfig: { layoutTemplate: 'campaign-feed' } }
    if (url === '/shop/products') return { list: [product] }
    if (mode === 'error') throw Error('断网')
    return mode === 'empty' ? [] : [activity({ startTime: new Date(Date.now() - 3600000).toISOString(), endTime: new Date(Date.now() + 3600000).toISOString() })]
  })
  const home = env.page('home')
  await home.loadHome(); assert.equal(home.data.products[0].priceText, '19.00')
  mode = 'empty'; await home.retryCampaigns()
  assert.equal(home.data.products[0].campaign, null)
  assert.equal(home.data.products[0].priceText, '99.00')
  assert.equal(home.data.campaignError, '')
  mode = 'active'; await home.retryCampaigns()
  mode = 'error'; await home.retryCampaigns()
  assert.equal(home.data.products[0].priceText, '99.00')
  assert.equal(home.data.products[0].campaign, null)
  assert.match(home.data.campaignError, /点击重试/)
  assert.ok(env.notices.some(notice => notice.includes('活动信息暂不可用')))
  home.onHide()
})
