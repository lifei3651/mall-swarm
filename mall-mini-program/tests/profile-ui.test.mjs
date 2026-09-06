import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const sourceUrl = new URL('../pages/profile/index.js', import.meta.url)
const zeroSummary = { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, afterSale: 0 }
const plain = (value) => JSON.parse(JSON.stringify(value))

function loadProfile({ token = '', member = null, respond } = {}) {
  const calls = [], navigations = [], modals = [], stored = []
  let definition, cleared = 0, themeApplied = 0
  const session = {
    getToken: () => token,
    getMember: () => member,
    clearSession() { token = ''; member = null; cleared++ }
  }
  const request = async (options) => {
    calls.push(plain(options))
    if (!respond) throw new Error(`Unexpected request: ${options.url}`)
    return respond(options)
  }
  const theme = {
    pageData: () => ({ themeStyle: '--brand: #7c3aed;' }),
    apply: () => { themeApplied++; return Promise.resolve({}) }
  }
  vm.runInNewContext(readFileSync(sourceUrl, 'utf8'), {
    Page: (value) => { definition = value },
    require: (id) => {
      const mocks = { '../../utils/session': session, '../../utils/request': request, '../../utils/theme': theme,
        '../../utils/member-avatar': { fallback: '/assets/profile/user-round.png', load: async () => '/assets/profile/user-round.png', release() {} } }
      assert.ok(Object.hasOwn(mocks, id), `Profile dependency must have an explicit mock: ${id}`)
      return mocks[id]
    },
    wx: {
      navigateTo: ({ url }) => navigations.push(url),
      showModal: (options) => modals.push(options),
      setStorageSync: (key, value) => stored.push([key, plain(value)])
    }
  }, { filename: sourceUrl.pathname })
  const page = { ...definition, data: plain(definition.data), setData(patch) { Object.assign(this.data, plain(patch)) } }
  return { page, calls, navigations, modals, stored, cleared: () => cleared, themeApplied: () => themeApplied }
}

test('个人中心游客刷新不请求账户接口，并清空上一个账号资料和角标', async () => {
  const { page, calls, navigations } = loadProfile({ member: { nickname: '旧账号' } })
  page.setData({ loggedIn: true, member: { nickname: '旧账号' }, unreadCount: 12, unreadText: '12', payoutCount: 2,
    orderSummary: { pendingPayment: 9, pendingShipment: 8, pendingReceipt: 7, afterSale: 6 } })
  await page.refresh()
  assert.equal(page.data.loggedIn, false)
  assert.equal(page.data.member, null)
  assert.equal(page.data.unreadCount, 0)
  assert.equal(page.data.unreadText, '')
  assert.equal(page.data.payoutCount, 0)
  assert.deepEqual(page.data.orderSummary, zeroSummary)
  assert.deepEqual(calls, [])
  assert.deepEqual(navigations, [], '展示游客页面本身不强制跳登录')
})

test('个人中心每次显示都会刷新账户状态并应用商城主题', () => {
  const harness = loadProfile()
  let refreshed = 0
  harness.page.refresh = () => { refreshed++ }
  harness.page.onShow()
  harness.page.onShow()
  assert.equal(refreshed, 2)
  assert.equal(harness.themeApplied(), 2)
})

test('个人中心游客点击受保护入口只跳登录，并保留原始目标及订单筛选', () => {
  const { page, navigations, calls } = loadProfile()
  for (const handler of ['security', 'messages', 'orders', 'addresses', 'payout', 'wallet', 'service']) page[handler]()
  const tabs = ['pending-payment', 'pending-shipment', 'pending-receipt', 'after-sale']
  for (const tab of tabs) {
    page.orderTab({ currentTarget: { dataset: { tab } } })
  }
  const targets = ['/pages/account-security/index', '/pages/messages/index', '/pages/orders/index',
    '/pages/address/index', '/pages/payout/index', '/pages/wallet/index', '/pages/orders/index?tab=after-sale',
    ...tabs.map((tab) => `/pages/orders/index?tab=${tab}`)]
  assert.deepEqual(navigations, targets.map((url) => `/pages/login/index?redirect=${encodeURIComponent(url)}`))
  assert.deepEqual(calls, [])
})

test('个人中心过期登录显示不能绕过门禁，非法订单筛选回落全部订单', () => {
  const { page, navigations } = loadProfile()
  page.setData({ loggedIn: true })
  page.addresses()
  page.orderTab({ currentTarget: { dataset: { tab: 'all&orderId=other' } } })
  assert.deepEqual(navigations, ['/pages/address/index', '/pages/orders/index?tab=all']
    .map((url) => `/pages/login/index?redirect=${encodeURIComponent(url)}`))
})

test('个人中心登录后保留消息、订单、地址、收款及售后正确入口', () => {
  const { page, navigations } = loadProfile({ token: 'test-token' })
  page.setData({ loggedIn: true })
  for (const handler of ['messages', 'orders', 'addresses', 'payout', 'service']) page[handler]()
  assert.deepEqual(navigations, [
    '/pages/messages/index', '/pages/orders/index', '/pages/address/index',
    '/pages/payout/index', '/pages/orders/index?tab=after-sale'
  ])
})

test('个人中心四个订单状态快捷入口不因视觉改版丢失筛选参数', () => {
  const { page, navigations } = loadProfile({ token: 'test-token' })
  page.setData({ loggedIn: true })
  const tabs = ['pending-payment', 'pending-shipment', 'pending-receipt', 'after-sale']
  for (const tab of tabs) page.orderTab({ currentTarget: { dataset: { tab } } })
  assert.deepEqual(navigations, tabs.map((tab) => `/pages/orders/index?tab=${tab}`))
})

test('个人中心加载本人信息、真实订单角标、未读消息和待确认微信收款', async () => {
  const member = { nickname: '测试会员', phone: '13800000000' }
  const summary = { pendingPayment: 2, pendingShipment: 3, pendingReceipt: 4, afterSale: 5 }
  const results = {
    '/shop/auth/me': member,
    '/shop/messages/unread': { total: 108 },
    '/shop/profile/order-summary': summary,
    '/shop/wallet/withdrawals': [
      { withdrawType: 2, status: 2 }, { withdrawType: '2', status: '2' },
      { withdrawType: 1, status: 2 }, { withdrawType: 2, status: 3 }
    ]
  }
  const { page, calls, stored } = loadProfile({ token: 'test-token', respond: ({ url }) => {
    assert.ok(Object.hasOwn(results, url))
    return results[url]
  } })
  await page.refresh()
  assert.equal(page.data.loggedIn, true)
  assert.deepEqual(page.data.member, member)
  assert.deepEqual(page.data.orderSummary, summary)
  assert.equal(page.data.unreadCount, 108)
  assert.equal(page.data.unreadText, '99+')
  assert.equal(page.data.payoutCount, 2)
  assert.deepEqual(stored, [['mall_mini_member', member]])
  assert.equal(calls[0].url, '/shop/auth/me')
  assert.deepEqual(calls.map(({ url }) => url).sort(), Object.keys(results).sort())
})

test('个人中心账户读取失败时不继续显示旧会员或交易计数', async () => {
  const { page, calls } = loadProfile({ token: 'expired-test-token', member: { nickname: '旧会员' }, respond: () => { throw new Error('Unauthorized') } })
  page.setData({ loggedIn: true, unreadCount: 8, unreadText: '8', payoutCount: 3,
    orderSummary: { pendingPayment: 1, pendingShipment: 2, pendingReceipt: 3, afterSale: 4 } })
  await page.refresh()
  assert.equal(page.data.loggedIn, false)
  assert.equal(page.data.member, null)
  assert.equal(page.data.unreadCount, 0)
  assert.equal(page.data.unreadText, '')
  assert.equal(page.data.payoutCount, 0)
  assert.deepEqual(page.data.orderSummary, zeroSummary)
  assert.deepEqual(calls, [{ url: '/shop/auth/me' }])
})

test('个人中心取消退出确认时不调用退出接口、不清理会话', async () => {
  const harness = loadProfile({ token: 'test-token', member: { nickname: '测试会员' } })
  harness.page.setData({ loggedIn: true, member: { nickname: '测试会员' }, unreadCount: 3 })
  harness.page.logout()
  assert.equal(harness.modals.length, 1)
  assert.equal(harness.modals[0].title, '退出登录')
  assert.equal(harness.cleared(), 0)
  await harness.modals[0].success({ confirm: false, cancel: true })
  assert.deepEqual(harness.calls, [])
  assert.equal(harness.cleared(), 0)
  assert.equal(harness.page.data.loggedIn, true)
  assert.equal(harness.page.data.unreadCount, 3)
})

test('个人中心确认退出后清理会话和全部账户角标', async () => {
  const harness = loadProfile({ token: 'test-token', member: { nickname: '测试会员' }, respond: () => ({}) })
  harness.page.setData({ loggedIn: true, unreadCount: 3, unreadText: '3', payoutCount: 2,
    orderSummary: { pendingPayment: 1, pendingShipment: 2, pendingReceipt: 3, afterSale: 4 } })
  harness.page.logout()
  await harness.modals[0].success({ confirm: true })
  assert.deepEqual(harness.calls, [{ url: '/shop/auth/logout', method: 'POST' }])
  assert.equal(harness.cleared(), 1)
  assert.equal(harness.page.data.loggedIn, false)
  assert.equal(harness.page.data.member, null)
  assert.equal(harness.page.data.unreadCount, 0)
  assert.equal(harness.page.data.unreadText, '')
  assert.equal(harness.page.data.payoutCount, 0)
  assert.deepEqual(harness.page.data.orderSummary, zeroSummary)
})

test('个人中心视觉布局保留四个可读订单入口、登录、常用服务及原生在线客服', () => {
  const view = readFileSync(new URL('../pages/profile/index.wxml', import.meta.url), 'utf8')
  const tags = view.match(/<[^>]+>/g) || []
  const attribute = (tag, name) => tag.match(new RegExp(`(?:^|\\s)${name}="([^"]*)"`))?.[1]
  const shortcuts = tags.filter((tag) => attribute(tag, 'bindtap') === 'orderTab')
  assert.equal(shortcuts.length, 4)
  assert.deepEqual(shortcuts.map((tag) => attribute(tag, 'data-tab')).sort(),
    ['pending-payment', 'pending-shipment', 'pending-receipt', 'after-sale'].sort())
  for (const tag of shortcuts) assert.ok(attribute(tag, 'aria-label'), '订单入口保留可读操作名称')
  for (const handler of ['login', 'orders', 'messages', 'addresses', 'payout', 'logout']) {
    assert.ok(tags.some((tag) => attribute(tag, 'bindtap') === handler), `保留 ${handler} 点击绑定`)
  }
  const contact = tags.find((tag) => attribute(tag, 'open-type') === 'contact')
  assert.ok(contact, '在线客服继续调用微信原生客服，不替换成无响应的装饰项')
  assert.ok(attribute(contact, 'aria-label'))
})

test('个人中心全部本地图标资源真实存在且具有有效PNG尺寸', () => {
  const view = readFileSync(new URL('../pages/profile/index.wxml', import.meta.url), 'utf8')
  const imageTags = view.match(/<image\b[^>]*>/g) || []
  const paths = [...new Set(imageTags.map((tag) => tag.match(/\bsrc="([^"]+)"/)?.[1]).filter((path) => path?.startsWith('/')))]
  assert.ok(paths.length > 0, '个人中心使用可打包的本地图标')
  for (const path of paths) {
    assert.match(path, /^\/assets\/[a-zA-Z0-9/_-]+\.png$/, `安全且固定的本地资源路径：${path}`)
    const content = readFileSync(new URL(`..${path}`, import.meta.url))
    assert.ok(content.length > 33, `图标内容非空：${path}`)
    assert.deepEqual(content.subarray(0, 8), Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), `PNG签名：${path}`)
    assert.equal(content.toString('ascii', 12, 16), 'IHDR', `PNG尺寸头：${path}`)
    assert.ok(content.readUInt32BE(16) > 0, `图标宽度有效：${path}`)
    assert.ok(content.readUInt32BE(20) > 0, `图标高度有效：${path}`)
  }
})

test('个人中心订单、消息和收款角标均受登录态显示保护', () => {
  const view = readFileSync(new URL('../pages/profile/index.wxml', import.meta.url), 'utf8')
  const attribute = (tag, name) => tag.match(new RegExp(`(?:^|\\s)${name}="([^"]*)"`))?.[1]
  const stack = []
  let badges = 0
  for (const tag of view.match(/<[^>]+>/g) || []) {
    if (tag.startsWith('</')) { stack.pop(); continue }
    const condition = attribute(tag, 'wx:if') || ''
    if (/\b(shortcut-count|unread-badge)\b/.test(attribute(tag, 'class') || '')) {
      badges++
      const conditions = [...stack, condition]
      assert.ok(conditions.some((value) => /\bloggedIn\s*&&/.test(value)), `角标本身或父级需要登录门禁：${tag}`)
    }
    if (!tag.endsWith('/>')) stack.push(condition)
  }
  assert.ok(badges >= 6, '四个订单状态、未读消息及待确认收款角标均已检查')
})
