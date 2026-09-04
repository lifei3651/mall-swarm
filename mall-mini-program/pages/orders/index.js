const request = require('../../utils/request')
const auth = require('../../utils/auth')
const format = require('../../utils/format')
const payment = require('../../utils/payment')

const STATUS = { 0: '待支付', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已取消' }

Page({
  data: { loading: true, error: '', rows: [], wechatPayEnabled: false, payingId: null },
  onLoad() { if (auth.requireLogin('/pages/orders/index')) this.loadConfig().then(() => this.load()) },
  onShow() { if (this.loadedOnce && auth.requireLogin('/pages/orders/index')) this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const result = await request({ url: '/shop/orders', params: { pageNum: 1, pageSize: 30 } })
      const rows = (result.list || []).map((row) => ({
        ...row,
        items: (row.items || []).map((item) => ({ ...item, productCover: format.mediaUrl(item.productCover) })),
        key: row.order.id,
        statusText: STATUS[row.order.status] || '处理中',
        amountText: format.money(row.order.payAmount || row.order.totalAmount)
      }))
      this.loadedOnce = true
      this.setData({ rows })
    } catch (error) { this.setData({ error: error.message }) }
    finally { this.setData({ loading: false }) }
  },
  async loadConfig() {
    try {
      const config = await request({ url: '/shop/pay/config' })
      this.setData({ wechatPayEnabled: Boolean(config.wechatPayEnabled) })
    } catch (_) { this.setData({ wechatPayEnabled: false }) }
  },
  async pay(event) {
    const orderId = Number(event.currentTarget.dataset.id)
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
      await this.load()
    } catch (error) {
      wx.hideLoading()
      wx.showToast({ title: payment.isUserCancel(error) ? '已取消支付，订单保留在待支付' : (error.message || '支付失败'), icon: 'none', duration: 2600 })
      await this.load()
    } finally {
      this.setData({ payingId: null })
    }
  }
})
