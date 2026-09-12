const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const format = require('../../utils/format')
const theme = require('../../utils/theme')
const session = require('../../utils/session')
const { identifier, amountLabel } = require('../order-detail/policy')
const orderList = require('../../utils/order-list')
const foreground = require('../../utils/foreground-refresh')

const STATUS = { 0: '待支付', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已取消' }
const AFTER_SALE_STATUS = { 0: '待审核', 1: '退款完成', 2: '已拒绝', 3: '已取消', 4: '待寄回', 5: '待商家收货', 6: '退款处理中', 7: '待换货发出', 8: '换货已发出' }
const TABS = [
  { key: 'all', label: '全部', state: '' },
  { key: 'pending-payment', label: '待支付', state: 'PENDING_PAYMENT', countKey: 'pendingPayment' },
  { key: 'pending-shipment', label: '待发货', state: 'PENDING_SHIPMENT', countKey: 'pendingShipment' },
  { key: 'pending-receipt', label: '待收货', state: 'PENDING_RECEIPT', countKey: 'pendingReceipt' },
  { key: 'pending-review', label: '待评价', state: 'PENDING_REVIEW', countKey: 'pendingReview' },
  { key: 'after-sale', label: '退款/售后', state: 'AFTER_SALE', countKey: 'afterSale' }
]

function displayRows(source) {
  return source.map(row => ({
    ...row,
    order: { ...row.order, id: identifier(row.order.id), status: Number(row.order.status) },
    items: (row.items || []).map(item => ({ ...item, productCover: format.mediaUrl(item.productCover) })),
    key: identifier(row.order.id),
    canReceive: Number(row.order.status) === 2 && !(row.afterSales || []).some(sale => [0,4,5,6,7,8].includes(Number(sale.status))),
    statusText: (row.afterSales || []).some(sale => [0,4,5,6,7,8].includes(Number(sale.status))) ? '售后处理中' : STATUS[row.order.status] || '处理中',
    afterSaleText: row.afterSales?.length ? AFTER_SALE_STATUS[Number(row.afterSales[0].status)] || '处理中' : '',
    amountText: format.money(row.order.payAmount == null ? row.order.totalAmount : row.order.payAmount),
    amountLabel: amountLabel(row.order),
    quantity: (row.items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0)
  }))
}

Page({
  data: {
    ...theme.pageData(),
    loading: true, loadingMore: false, error: '', rows: [], total: 0, pageNum: 0, pageSize: 10,
    tabs: TABS, activeTab: 'all', actingId: ''
  },
  onLoad(options = {}) {
    theme.apply(this)
    const activeTab = TABS.some((tab) => tab.key === options.tab) ? options.tab : 'all'
    feedback.update(this, { activeTab })
  },
  onShow() {
    this.inactive = false
    foreground.start(this, () => this.refreshQuietly(), () => this.data.loading || this.data.loadingMore || this.data.actingId || this.openingPayment)
    theme.apply(this)
    const redirect = `/pages/orders/index${this.data.activeTab === 'all' ? '' : `?tab=${this.data.activeTab}`}`
    if (auth.requireLogin(redirect)) return Promise.all([this.loadSummary(), this.load(true)])
    this.requestVersion = (this.requestVersion || 0) + 1
    this.summaryVersion = (this.summaryVersion || 0) + 1
    feedback.update(this, { loading: false, loadingMore: false, rows: [], total: 0, pageNum: 0, tabs: TABS })
  },
  onHide() { this.inactive = true; foreground.stop(this); this.requestVersion = (this.requestVersion || 0) + 1; this.summaryVersion = (this.summaryVersion || 0) + 1; this.openingPayment = false; this.openingAfterSale = false },
  onUnload() { this.onHide(); this.disposed = true },
  onPullDownRefresh() {
    Promise.all([this.loadSummary(), this.load(true)]).finally(() => wx.stopPullDownRefresh())
  },
  async loadSummary() {
    const token = session.getToken()
    const version = this.summaryVersion = (this.summaryVersion || 0) + 1
    try {
      const summary = await request({ url: '/shop/profile/order-summary' })
      if (this.disposed || this.inactive || token !== session.getToken() || version !== this.summaryVersion) return
      feedback.update(this, { tabs: TABS.map((tab) => ({ ...tab, count: Number(tab.countKey ? summary[tab.countKey] || 0 : 0) })) })
    } catch (_) {}
  },
  async load(reset = false) {
    const token = session.getToken()
    reset = reset === true
    if (!reset && (this.data.loadingMore || this.data.loading)) return
    const version = this.requestVersion = (this.requestVersion || 0) + 1
    const activeTab = this.data.activeTab
    const nextPage = reset ? 1 : this.data.pageNum + 1
    feedback.update(this, reset ? { loading: true, loadingMore: false, error: '' } : { loadingMore: true, error: '' })
    try {
      const tab = TABS.find((item) => item.key === activeTab) || TABS[0]
      const result = await request({ url: '/shop/orders', params: {
        pageNum: nextPage,
        pageSize: this.data.pageSize,
        orderState: tab.state || undefined
      } })
      if (this.disposed || this.inactive || token !== session.getToken() || version !== this.requestVersion || activeTab !== this.data.activeTab) return
      const rows = displayRows(result.list || [])
      this.loadedOnce = true
      feedback.update(this, {
        rows: orderList.decorate(reset ? rows : this.data.rows.concat(rows)),
        total: Number(result.total || 0),
        pageNum: Number(result.pageNum || nextPage)
      })
    } catch (error) {
      if (!this.disposed && version === this.requestVersion) feedback.update(this, { error: error.message || '订单加载失败' })
    } finally {
      if (!this.disposed && version === this.requestVersion) feedback.update(this, { loading: false, loadingMore: false })
    }
  },
  pay(event) {
    const orderId = identifier(event.currentTarget.dataset.id)
    const row = this.data.rows.find((item) => item.order.id === orderId)
    if (!row || !orderList.decorate(this.data.rows).find(item => item.order.id === orderId)?.canPay || this.openingPayment || this.inactive) return
    if (!auth.requireLogin(`/pages/order-detail/index?id=${orderId}`)) return
    // One recovery path: review the latest full trade amount before native payment.
    this.openingPayment = true
    wx.navigateTo({ url: `/pages/order-detail/index?id=${orderId}`, fail: () => feedback.notice('暂时无法打开付款详情，请重试'), complete: () => { this.openingPayment = false } })
  },
  applyAfterSale(event) {
    const id = identifier(event.currentTarget.dataset.id)
    if (this.inactive || this.openingAfterSale || !orderList.decorate(this.data.rows).find(item => item.order.id === id)?.canApplyAfterSale) return
    this.openingAfterSale = true
    wx.navigateTo({ url: `/pages/after-sale/index?orderId=${id}`, fail: () => feedback.notice('暂时无法打开售后申请，请重试'), complete: () => { this.openingAfterSale = false } })
  },
  async refreshQuietly() {
    if (this.inactive || this.data.loading || this.data.loadingMore || this.data.actingId || this.quietRefreshing) return
    const token = session.getToken(), version = this.requestVersion = (this.requestVersion || 0) + 1
    const activeTab = this.data.activeTab, current = () => !this.inactive && !this.disposed && !this.data.actingId && !this.openingPayment && !this.openingAfterSale && token === session.getToken() && version === this.requestVersion && activeTab === this.data.activeTab
    if (!token) return
    this.quietRefreshing = true
    try {
      const tab = TABS.find(item => item.key === activeTab) || TABS[0]
      const result = await foreground.visiblePages(request, { url: '/shop/orders', params: { orderState: tab.state || undefined }, pageCount: this.data.pageNum, pageSize: this.data.pageSize }, current)
      if (!result || !current()) return
      const seen = new Set()
      const rows = displayRows(result.list.filter(row => { const id = identifier(row.order?.id); if (!id || seen.has(id)) return false; seen.add(id); return true }))
      this.setData({ error: '', rows: orderList.decorate(rows), total: Number(result.total || 0), pageNum: Number(result.pageNum || Math.max(1, Math.ceil(rows.length / this.data.pageSize))) })
      this.quietErrorShown = false
      await this.loadSummary()
    } catch (error) {
      if (current() && !this.quietErrorShown) { this.quietErrorShown = true; feedback.notice('订单暂未刷新，当前显示上次记录。请下拉刷新后再核对最新进度。', '更新未完成') }
    } finally { this.quietRefreshing = false }
  },
  selectTab(event) {
    const activeTab = String(event.currentTarget.dataset.key || 'all')
    if (!TABS.some((tab) => tab.key === activeTab) || activeTab === this.data.activeTab) return
    feedback.update(this, { activeTab, rows: [], total: 0, pageNum: 0 }, () => this.load(true))
  },
  openDetail(event) {
    const id = identifier(event.currentTarget.dataset.id)
    if (id) wx.navigateTo({ url: `/pages/order-detail/index?id=${id}` })
  },
  review(event) {
    const row = this.data.rows.find(item => item.order.id === identifier(event.currentTarget.dataset.id))
    const productId = row && identifier(row.pendingReviewProductId)
    const orderItemId = row && identifier(row.pendingReviewOrderItemId)
    if (!productId || !orderItemId) { feedback.notice('评价入口已变化，请刷新订单后重试'); return }
    wx.navigateTo({ url: `/pages/order-review/index?id=${productId}&orderItemId=${orderItemId}` })
  },
  cancelOrder(event) { return this.orderAction(event, 'cancel') },
  receive(event) { return this.orderAction(event, 'receive') },
  async orderAction(event, action) {
    if (this.disposed || this.data.actingId || !auth.requireLogin('/pages/orders/index')) return
    const id = identifier(event.currentTarget.dataset.id), row = this.data.rows.find(item => item.order.id === id)
    if (!row || this.inactive || (action === 'cancel' ? !orderList.decorate(this.data.rows).find(item => item.order.id === id)?.canCancel : !row.canReceive)) return
    const token = session.getToken(), current = () => !this.disposed && !this.inactive && token === session.getToken()
    this.setData({ actingId: id })
    try {
      const confirmed = await new Promise(resolve => wx.showModal({ title: action === 'cancel' ? '取消订单' : '确认收到商品', content: action === 'cancel' ? (row.order.tradeId ? '这是合并支付订单，取消将同时关闭该交易下所有待付款子订单并释放库存，无法恢复。' : '取消后将释放库存，这笔订单无法恢复。') : '确认后订单将完成；如商品未收到或存在问题，请暂时不要确认。', confirmText: action === 'cancel' ? '确认取消' : '确认收货', success: result => resolve(result.confirm), fail: () => resolve(false) }))
      if (!confirmed || !current()) return
      const latest = await request({ url: `/shop/orders/${id}` })
      if (!current()) return
      if (identifier(latest?.order?.id) !== id || (action === 'cancel' ? Number(latest.order.status) !== 0 : Number(latest.order.status) !== 2 || (latest.afterSales || []).some(sale => [0,4,5,6,7,8].includes(Number(sale.status))))) throw new Error('订单状态已变化，请刷新后操作')
      await request({ url: `/shop/orders/${id}/${action}`, method: 'PUT' })
      if (!current()) return
      await feedback.success(action === 'cancel' ? '订单已取消' : '已确认收货')
      if (current()) await Promise.all([this.load(true), this.loadSummary()])
    } catch (error) { if (current()) await feedback.notice(error.message || '操作结果待确认，请刷新订单核对后再试') }
    finally { if (!this.disposed) this.setData({ actingId: '' }) }
  },
  retry() { return this.load(true) },
  loadMore() {
    if (this.data.rows.length < this.data.total) this.load(false)
  },
  goShopping() { wx.switchTab({ url: '/pages/home/index' }) }
})
