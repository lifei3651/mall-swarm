const request = require('../../utils/request')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const format = require('../../utils/format')

const CATEGORY_NAMES = {
  ORDER_LOGISTICS: '订单物流',
  AFTER_SALE_REFUND: '售后退款',
  WALLET_FUNDS: '钱包资金',
  ACCOUNT_SECURITY: '账户安全',
  SERVICE: '服务通知'
}

Page({
  data: { ...theme.pageData(), loading: true, error: '', message: null, categoryName: '', displayTime: '' },
  onLoad(options = {}) {
    theme.apply(this)
    this.messageId = format.identifier(options.id)
  },
  onShow() { if (!this.fetching && !this.data.message && auth.requireLogin(`/pages/message-detail/index?id=${this.messageId}`)) return this.load(this.messageId) },
  async load(id) {
    if (!id) { this.setData({ loading: false, error: '消息编号不正确' }); return }
    if (this.fetching) return
    this.fetching = true
    this.setData({ loading: true, error: '' })
    try {
      const message = await request({ url: `/shop/messages/${id}` })
      this.setData({
        message,
        categoryName: CATEGORY_NAMES[message.category] || '个人消息',
        displayTime: String(message.occurredTime || message.createTime || '').replace('T', ' ').slice(0, 16)
      })
    } catch (error) { this.setData({ error: error.message || '消息不存在或无权查看' }) }
    finally { this.fetching = false; this.setData({ loading: false }) }
  },
  openTarget() {
    const message = this.data.message || {}
    if (message.targetType === 'ORDER' || message.targetType === 'AFTER_SALE') {
      const id = format.identifier(message.targetType === 'ORDER' ? message.targetId : message.targetParentId)
      wx.navigateTo({ url: id ? `/pages/order-detail/index?id=${id}` : '/pages/orders/index' })
      return
    }
    if (message.targetType === 'WALLET' || message.targetType === 'WITHDRAWAL') {
      wx.navigateTo({ url: '/pages/payout/index' })
      return
    }
    if (message.targetType === 'ACCOUNT_SECURITY') { wx.navigateTo({ url: '/pages/account-security/index' }); return }
    wx.showToast({ title: '请从对应功能入口查看', icon: 'none' })
  }
})
