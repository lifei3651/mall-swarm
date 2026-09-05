const request = require('../../utils/request')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const format = require('../../utils/format')

const CATEGORIES = [
  { key: '', label: '全部' },
  { key: 'ORDER_LOGISTICS', label: '订单物流' },
  { key: 'AFTER_SALE_REFUND', label: '售后退款' },
  { key: 'WALLET_FUNDS', label: '钱包资金' },
  { key: 'ACCOUNT_SECURITY', label: '账户安全' },
  { key: 'SERVICE', label: '服务通知' }
]

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

Page({
  data: {
    ...theme.pageData(),
    categories: CATEGORIES.map((item) => ({ ...item, count: 0 })),
    category: '',
    rows: [],
    unread: { total: 0, categories: {} },
    pageNum: 0,
    totalPage: 1,
    loading: true,
    error: '',
    subscriptionAvailable: false
  },
  onLoad() { theme.apply(this) },
  onShow() { theme.sync(this); if (!this.fetching) return this.load(true) },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()) },
  async load(reset) {
    if (!auth.requireLogin('/pages/messages/index')) return
    if (this.data.loading && !reset) return
    const sequence = this.sequence = (this.sequence || 0) + 1
    this.fetching = true
    this.setData({ loading: true, error: '', ...(reset ? { rows: [], pageNum: 0, totalPage: 1 } : {}) })
    try {
      const next = reset ? 1 : this.data.pageNum + 1
      const [page, unread, templates] = await Promise.all([
        request({
          url: '/shop/messages',
          params: { category: this.data.category || undefined, pageNum: next, pageSize: 20 }
        }),
        request({ url: '/shop/messages/unread' }),
        request({ url: '/shop/wechat-mini-program/subscriptions' }).catch(() => [])
      ])
      if (sequence !== this.sequence) return
      const incoming = (page.list || []).map((item) => ({
        ...item,
        displayTime: formatTime(item.occurredTime || item.createTime)
      }))
      this.loadedOnce = true
      const normalizedUnread = unread || { total: 0, categories: {} }
      this.setData({
        rows: reset ? incoming : this.data.rows.concat(incoming),
        unread: normalizedUnread,
        categories: CATEGORIES.map((item) => ({
          ...item,
          count: Number(item.key ? normalizedUnread.categories && normalizedUnread.categories[item.key] : normalizedUnread.total || 0)
        })),
        pageNum: Number(page.pageNum || next),
        totalPage: Number(page.totalPage || 1),
        subscriptionAvailable: Boolean(templates && templates.length)
      })
    } catch (error) {
      if (sequence === this.sequence) this.setData({ error: error.message || '消息加载失败' })
    } finally {
      if (sequence === this.sequence) { this.fetching = false; this.setData({ loading: false }) }
    }
  },
  retry() { this.load(true) },
  selectCategory(event) {
    this.setData({ category: String(event.currentTarget.dataset.key || '') }, () => this.load(true))
  },
  openMessage(event) {
    const id = format.identifier(event.currentTarget.dataset.id)
    if (id) wx.navigateTo({ url: `/pages/message-detail/index?id=${id}` })
  },
  subscriptions() { wx.navigateTo({ url: '/pages/subscriptions/index' }) },
  async readAll() {
    try {
      await request({ url: '/shop/messages/read-all', method: 'PUT' })
      await this.load(true)
    } catch (error) { wx.showToast({ title: error.message || '操作失败', icon: 'none' }) }
  },
  loadMore() { if (this.data.pageNum < this.data.totalPage) this.load(false) }
})
