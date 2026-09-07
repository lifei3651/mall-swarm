const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const format = require('../../utils/format')
const session = require('../../utils/session')
const foreground = require('../../utils/foreground-refresh')

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
    subscriptionAvailable: false, reading: false,
    smsPreference: { available: false, enabled: false, maskedPhone: '', statusText: '正在确认服务状态' }, smsBusy: false, smsError: ''
  },
  onLoad() { theme.apply(this) },
  onShow() { this.inactive = false; foreground.start(this, () => this.refreshQuietly(), () => this.fetching || this.data.reading || this.data.smsBusy); theme.apply(this); this.loadSmsPreference(); if (!this.fetching) return this.load(true) },
  onHide() { this.inactive = true; foreground.stop(this); this.sequence = (this.sequence || 0) + 1; this.smsSequence = (this.smsSequence || 0) + 1; this.fetching = false; this.setData({ loading: false }) },
  onUnload() { this.onHide(); this.disposed = true },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()) },
  async load(reset) {
    if (!auth.requireLogin('/pages/messages/index')) return
    if (this.data.loading && !reset) return
    const sequence = this.sequence = (this.sequence || 0) + 1
    const token = session.getToken()
    this.fetching = true
    feedback.update(this, { loading: true, error: '', ...(reset ? { rows: [], pageNum: 0, totalPage: 1 } : {}) })
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
      if (sequence !== this.sequence || this.inactive || token !== session.getToken()) return
      const incoming = (page.list || []).map((item) => ({
        ...item,
        displayTime: formatTime(item.occurredTime || item.createTime)
      }))
      this.loadedOnce = true
      const normalizedUnread = unread || { total: 0, categories: {} }
      feedback.update(this, {
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
      if (sequence === this.sequence) feedback.update(this, { error: error.message || '消息加载失败' })
    } finally {
      if (sequence === this.sequence) { this.fetching = false; feedback.update(this, { loading: false }) }
    }
  },
  retry() { this.load(true) },
  async refreshQuietly() {
    if (this.inactive || this.fetching || this.quietRefreshing || this.data.reading || this.data.smsBusy) return
    const token = session.getToken(), sequence = this.sequence = (this.sequence || 0) + 1, category = this.data.category
    if (!token) return
    const current = () => !this.inactive && !this.disposed && !this.data.reading && !this.data.smsBusy && token === session.getToken() && sequence === this.sequence && category === this.data.category
    this.quietRefreshing = true
    try {
      const result = await foreground.visiblePages(request, { url: '/shop/messages', params: { category: category || undefined }, pageCount: this.data.pageNum, pageSize: 20 }, current)
      if (!result || !current()) return
      const unread = await request({ url: '/shop/messages/unread' })
      if (!current()) return
      if (!unread || typeof unread !== 'object') throw new Error('未读消息状态暂不可用')
      const seen = new Set()
      this.setData({ error: '', rows: result.list.filter(item => { const id = String(item.id); if (seen.has(id)) return false; seen.add(id); return true }).map(item => ({ ...item, displayTime: formatTime(item.occurredTime || item.createTime) })),
        unread, categories: CATEGORIES.map(item => ({ ...item, count: Number(item.key ? unread.categories?.[item.key] || 0 : unread.total || 0) })),
        pageNum: Number(result.pageNum || Math.max(1, Math.ceil(result.list.length / 20))), totalPage: Number(result.totalPage || 1) })
      this.quietErrorShown = false
    } catch (error) {
      if (current() && !this.quietErrorShown) { this.quietErrorShown = true; feedback.notice('消息暂未刷新，当前显示上次记录。请下拉刷新后核对最新提醒。', '更新未完成') }
    } finally { this.quietRefreshing = false }
  },
  selectCategory(event) {
    feedback.update(this, { category: String(event.currentTarget.dataset.key || '') }, () => this.load(true))
  },
  openMessage(event) {
    const id = format.identifier(event.currentTarget.dataset.id)
    if (id) wx.navigateTo({ url: `/pages/message-detail/index?id=${id}` })
  },
  subscriptions() { wx.navigateTo({ url: '/pages/subscriptions/index' }) },
  support() { wx.navigateTo({ url: '/pages/support/index' }) },
  async loadSmsPreference() {
    if (!session.getToken()) return
    const token = session.getToken(), sequence = this.smsSequence = (this.smsSequence || 0) + 1
    const current = () => !this.inactive && token === session.getToken() && sequence === this.smsSequence
    this.setData({ smsError: '' })
    try {
      const result = await request({ url: '/shop/messages/preferences/sms' })
      if (!result || typeof result.enabled !== 'boolean') throw new Error('短信设置暂不可用')
      if (current()) this.setData({ smsPreference: result })
    } catch (_) { if (current()) this.setData({ smsPreference: { available: false, enabled: false, statusText: '暂时无法读取短信设置，站内消息不受影响' }, smsError: '短信设置加载失败' }) }
  },
  async changeSmsPreference() {
    if (this.data.smsBusy || this.inactive || (!this.data.smsPreference.available && !this.data.smsPreference.enabled) || !auth.requireLogin('/pages/messages/index')) return
    const token = session.getToken(), enabled = !this.data.smsPreference.enabled, current = () => !this.inactive && token === session.getToken()
    this.setData({ smsBusy: true })
    try {
      if (enabled) {
        const confirmed = await new Promise(resolve => wx.showModal({ title: '开启重要进度短信？', content: '开启后，商城可向当前绑定手机号发送订单发货、售后退款和账号安全变化提醒，不会用于营销。你可以随时在消息中心关闭。', confirmText: '同意并开启', success: result => resolve(result.confirm), fail: () => resolve(false) }))
        if (!confirmed || !current()) return
      }
      const result = await request({ url: '/shop/messages/preferences/sms', method: 'PUT', data: { enabled, consent: enabled } })
      if (!current()) return
      if (!result || typeof result.enabled !== 'boolean') throw new Error('设置结果待确认，请重新读取短信设置')
      this.setData({ smsPreference: result, smsError: '' })
      await feedback.notice(result.enabled ? '已开启重要进度短信' : '已关闭重要进度短信', '设置完成')
    } catch (error) { if (current()) await feedback.notice(error.message || '短信设置保存失败，请重试') }
    finally { this.setData({ smsBusy: false }) }
  },
  readAll() { return this.markRead(false) },
  readCategory() { return this.markRead(true) },
  async markRead(categoryOnly) {
    if (this.data.reading || this.inactive || !auth.requireLogin('/pages/messages/index')) return
    const category = this.data.category, token = session.getToken()
    if (categoryOnly && !CATEGORIES.some(item => item.key && item.key === category)) return
    this.setData({ reading: true })
    try {
      await request({ url: categoryOnly ? '/shop/messages/read-category' : '/shop/messages/read-all', method: 'PUT', ...(categoryOnly ? { params: { category } } : {}) })
      if (!this.inactive && token === session.getToken()) await this.load(true)
    } catch (error) { if (!this.inactive && token === session.getToken()) feedback.toast({ title: error.message || '操作失败', icon: 'none' }) }
    finally { this.setData({ reading: false }) }
  },
  loadMore() { if (this.data.pageNum < this.data.totalPage) this.load(false) }
})
