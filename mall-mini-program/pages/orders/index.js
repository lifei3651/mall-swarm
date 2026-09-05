const request = require('../../utils/request')
const auth = require('../../utils/auth')
const format = require('../../utils/format')
const payment = require('../../utils/payment')
const theme = require('../../utils/theme')
const { identifier } = require('../order-detail/policy')

const STATUS = { 0: '待支付', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已取消' }
const AFTER_SALE_STATUS = { 0: '待审核', 1: '退款完成', 2: '已拒绝', 3: '已取消', 4: '待寄回', 5: '待商家收货', 6: '退款处理中', 7: '待换货发出', 8: '换货已发出' }
const TABS = [
  { key: 'all', label: '全部', state: '' },
  { key: 'pending-payment', label: '待支付', state: 'PENDING_PAYMENT', countKey: 'pendingPayment' },
  { key: 'pending-shipment', label: '待发货', state: 'PENDING_SHIPMENT', countKey: 'pendingShipment' },
  { key: 'pending-receipt', label: '待收货', state: 'PENDING_RECEIPT', countKey: 'pendingReceipt' },
  { key: 'after-sale', label: '退款/售后', state: 'AFTER_SALE', countKey: 'afterSale' }
]

Page({
  data: {
    ...theme.pageData(),
    loading: true, loadingMore: false, error: '', rows: [], total: 0, pageNum: 0, pageSize: 10,
    tabs: TABS, activeTab: 'all', wechatPayEnabled: false, payingId: null
  },
  onLoad(options = {}) {
    theme.apply(this)
    const activeTab = TABS.some((tab) => tab.key === options.tab) ? options.tab : 'all'
    this.setData({ activeTab })
  },
  onShow() {
    theme.sync(this)
    const redirect = `/pages/orders/index${this.data.activeTab === 'all' ? '' : `?tab=${this.data.activeTab}`}`
    if (auth.requireLogin(redirect)) return Promise.all([this.loadConfig(), this.loadSummary(), this.load(true)])
    this.requestVersion = (this.requestVersion || 0) + 1
    this.summaryVersion = (this.summaryVersion || 0) + 1
    this.setData({ loading: false, loadingMore: false, rows: [], total: 0, pageNum: 0, tabs: TABS, wechatPayEnabled: false })
  },
  onUnload() { this.disposed = true; this.requestVersion = (this.requestVersion || 0) + 1 },
  onPullDownRefresh() {
    Promise.all([this.loadSummary(), this.load(true)]).finally(() => wx.stopPullDownRefresh())
  },
  async loadSummary() {
    const version = this.summaryVersion = (this.summaryVersion || 0) + 1
    try {
      const summary = await request({ url: '/shop/profile/order-summary' })
      if (this.disposed || version !== this.summaryVersion) return
      this.setData({ tabs: TABS.map((tab) => ({ ...tab, count: Number(tab.countKey ? summary[tab.countKey] || 0 : 0) })) })
    } catch (_) {}
  },
  async load(reset = false) {
    reset = reset === true
    if (!reset && (this.data.loadingMore || this.data.loading)) return
    const version = this.requestVersion = (this.requestVersion || 0) + 1
    const activeTab = this.data.activeTab
    const nextPage = reset ? 1 : this.data.pageNum + 1
    this.setData(reset ? { loading: true, loadingMore: false, error: '' } : { loadingMore: true, error: '' })
    try {
      const tab = TABS.find((item) => item.key === activeTab) || TABS[0]
      const result = await request({ url: '/shop/orders', params: {
        pageNum: nextPage,
        pageSize: this.data.pageSize,
        orderState: tab.state || undefined
      } })
      if (this.disposed || version !== this.requestVersion || activeTab !== this.data.activeTab) return
      const rows = (result.list || []).map((row) => ({
        ...row,
        order: { ...row.order, id: identifier(row.order.id), status: Number(row.order.status) },
        items: (row.items || []).map((item) => ({ ...item, productCover: format.mediaUrl(item.productCover) })),
        key: identifier(row.order.id),
        statusText: (row.afterSales || []).some((sale) => [0, 4, 5, 6, 7, 8].includes(Number(sale.status)))
          ? '售后处理中' : (STATUS[row.order.status] || '处理中'),
        afterSaleText: row.afterSales && row.afterSales.length
          ? (AFTER_SALE_STATUS[Number(row.afterSales[0].status)] || '处理中') : '',
        amountText: format.money(row.order.payAmount == null ? row.order.totalAmount : row.order.payAmount),
        quantity: (row.items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0)
      }))
      this.loadedOnce = true
      this.setData({
        rows: reset ? rows : this.data.rows.concat(rows),
        total: Number(result.total || 0),
        pageNum: Number(result.pageNum || nextPage)
      })
    } catch (error) {
      if (!this.disposed && version === this.requestVersion) this.setData({ error: error.message || '订单加载失败' })
    } finally {
      if (!this.disposed && version === this.requestVersion) this.setData({ loading: false, loadingMore: false })
    }
  },
  async loadConfig() {
    try {
      const config = await request({ url: '/shop/pay/config' })
      this.setData({ wechatPayEnabled: Boolean(config.wechatPayEnabled) })
    } catch (_) { this.setData({ wechatPayEnabled: false }) }
  },
  async pay(event) {
    const orderId = identifier(event.currentTarget.dataset.id)
    if (!orderId) return
    if (!this.data.wechatPayEnabled) {
      wx.showModal({ title: '微信支付暂未开放', content: '当前客户尚未完成微信支付商户资料配置与真实联调。', showCancel: false })
      return
    }
    if (this.data.payingId) return
    this.setData({ payingId: orderId })
    wx.showLoading({ title: '正在调起支付', mask: true })
    try {
      const confirmed = await payment.payOrder(orderId)
      wx.hideLoading()
      wx.showToast({ title: confirmed ? '支付成功' : '支付结果确认中', icon: confirmed ? 'success' : 'none' })
      await Promise.all([this.load(true), this.loadSummary()])
    } catch (error) {
      wx.hideLoading()
      wx.showToast({ title: payment.isUserCancel(error) ? '已取消支付，订单保留在待支付' : (error.message || '支付失败'), icon: 'none', duration: 2600 })
      await Promise.all([this.load(true), this.loadSummary()])
    } finally {
      this.setData({ payingId: null })
    }
  },
  selectTab(event) {
    const activeTab = String(event.currentTarget.dataset.key || 'all')
    if (!TABS.some((tab) => tab.key === activeTab) || activeTab === this.data.activeTab) return
    this.setData({ activeTab, rows: [], total: 0, pageNum: 0 }, () => this.load(true))
  },
  openDetail(event) {
    const id = identifier(event.currentTarget.dataset.id)
    if (id) wx.navigateTo({ url: `/pages/order-detail/index?id=${id}` })
  },
  retry() { return this.load(true) },
  loadMore() {
    if (this.data.rows.length < this.data.total) this.load(false)
  },
  goShopping() { wx.switchTab({ url: '/pages/home/index' }) }
})
