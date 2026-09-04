const request = require('../../utils/request')
const auth = require('../../utils/auth')

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
  onLoad() { if (auth.requireLogin('/pages/messages/index')) this.load(true) },
  onShow() { if (this.loadedOnce && auth.requireLogin('/pages/messages/index')) this.load(true) },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()) },
  async load(reset) {
    if (this.data.loading && !reset) return
    this.setData({ loading: true, error: '' })
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
      this.setData({ error: error.message || '消息加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },
  retry() { this.load(true) },
  selectCategory(event) {
    this.setData({ category: String(event.currentTarget.dataset.key || '') }, () => this.load(true))
  },
  openMessage(event) {
    wx.navigateTo({ url: `/pages/message-detail/index?id=${Number(event.currentTarget.dataset.id)}` })
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
