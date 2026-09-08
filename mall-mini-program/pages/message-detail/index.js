const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const session = require('../../utils/session')
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
  onShow() {
    this.hidden = false
    if (this.owner !== session.getToken()) this.setData({ message: null, categoryName: '', displayTime: '', error: '' })
    if (auth.requireLogin(`/pages/message-detail/index?id=${this.messageId}`)) return this.load(this.messageId)
  },
  onHide() { this.hidden = true; this.loadVersion = (this.loadVersion || 0) + 1; this.fetching = false; this.setData({ message: null, categoryName: '', displayTime: '' }) },
  onUnload() { this.onHide(); this.disposed = true },
  async load(id) {
    if (!id) { feedback.update(this, { loading: false, error: '消息编号不正确' }); return }
    if (this.fetching) return
    this.fetching = true
    const token = this.owner = session.getToken(), version = this.loadVersion = (this.loadVersion || 0) + 1
    const current = () => !this.hidden && !this.disposed && version === this.loadVersion && Boolean(token) && session.getToken() === token
    feedback.update(this, { loading: true, error: '' })
    try {
      const message = await request({ url: `/shop/messages/${id}` })
      if (!current()) return
      feedback.update(this, {
        message,
        categoryName: CATEGORY_NAMES[message.category] || '个人消息',
        displayTime: String(message.occurredTime || message.createTime || '').replace('T', ' ').slice(0, 16)
      })
    } catch (error) { if (current()) feedback.update(this, { message: null, error: error.message || '消息不存在或无权查看' }) }
    finally { if (current()) { this.fetching = false; feedback.update(this, { loading: false }) } }
  },
  openTarget() {
    if (this.hidden || this.disposed || !session.getToken() || this.owner !== session.getToken()) return
    const message = this.data.message || {}
    if (message.targetType === 'ORDER' || message.targetType === 'AFTER_SALE') {
      const id = format.identifier(message.targetType === 'ORDER' ? message.targetId : message.targetParentId)
      wx.navigateTo({ url: id ? `/pages/order-detail/index?id=${id}` : '/pages/orders/index' })
      return
    }
    if (message.targetType === 'WALLET') {
      wx.navigateTo({ url: '/pages/wallet/index' })
      return
    }
    if (message.targetType === 'WITHDRAWAL') {
      const id = format.identifier(message.targetId)
      wx.navigateTo({ url: id ? `/pages/payout/index?history=1&id=${id}` : '/pages/payout/index?history=1' })
      return
    }
    if (message.targetType === 'SERVICE_TICKET') { const id = format.identifier(message.targetId); wx.navigateTo({ url: id ? `/pages/support-detail/index?id=${id}` : '/pages/support/index' }); return }
    if (message.targetType === 'ACCOUNT_SECURITY') { wx.navigateTo({ url: '/pages/account-security/index' }); return }
    feedback.toast({ title: '请从对应功能入口查看', icon: 'none' })
  }
})
