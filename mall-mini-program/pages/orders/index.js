const request = require('../../utils/request')
const auth = require('../../utils/auth')
const format = require('../../utils/format')

const STATUS = { 0: '待支付', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已取消' }

Page({
  data: { loading: true, error: '', rows: [] },
  onLoad() { if (auth.requireLogin('/pages/orders/index')) this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const result = await request({ url: '/shop/orders', params: { pageNum: 1, pageSize: 30 } })
      const rows = (result.list || []).map((row) => ({
        ...row,
        key: row.order.id,
        statusText: STATUS[row.order.status] || '处理中',
        amountText: format.money(row.order.payAmount || row.order.totalAmount)
      }))
      this.setData({ rows })
    } catch (error) { this.setData({ error: error.message }) }
    finally { this.setData({ loading: false }) }
  },
  paymentNotice() {
    wx.showModal({
      title: '支付通道待客户配置',
      content: '微信支付必须由当前客户提供商户号、API v3 证书与回调域名，完成联调后才会显示支付按钮。',
      showCancel: false
    })
  }
})
