const request = require('../../utils/request')
const auth = require('../../utils/auth')

const CATEGORY_NAMES = {
  ORDER_LOGISTICS: '订单物流',
  AFTER_SALE_REFUND: '售后退款',
  WALLET_FUNDS: '钱包资金',
  ACCOUNT_SECURITY: '账户安全',
  SERVICE: '服务通知'
}

Page({
  data: { loading: true, error: '', message: null, categoryName: '', displayTime: '' },
  onLoad(options) {
    if (!auth.requireLogin(`/pages/message-detail/index?id=${options.id || ''}`)) return
    this.load(Number(options.id))
  },
  async load(id) {
    if (!id) { this.setData({ loading: false, error: '消息编号不正确' }); return }
    try {
      const message = await request({ url: `/shop/messages/${id}` })
      this.setData({
        message,
        categoryName: CATEGORY_NAMES[message.category] || '个人消息',
        displayTime: String(message.occurredTime || message.createTime || '').replace('T', ' ').slice(0, 16)
      })
    } catch (error) { this.setData({ error: error.message || '消息不存在或无权查看' }) }
    finally { this.setData({ loading: false }) }
  },
  openTarget() {
    const message = this.data.message || {}
    if (message.targetType === 'ORDER' || message.targetType === 'AFTER_SALE') {
      wx.navigateTo({ url: '/pages/orders/index' })
      return
    }
    if (message.targetType === 'WALLET' || message.targetType === 'WITHDRAWAL') {
      wx.navigateTo({ url: '/pages/payout/index' })
      return
    }
    wx.showToast({ title: '请从对应功能入口查看', icon: 'none' })
  }
})
