const session = require('../../utils/session')
const request = require('../../utils/request')

Page({
  data: {
    loggedIn: false, member: null, unreadCount: 0, unreadText: '', payoutCount: 0,
    orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, afterSale: 0 }
  },
  onShow() { this.refresh() },
  async refresh() {
    const token = session.getToken()
    this.setData({ loggedIn: Boolean(token), member: session.getMember() })
    if (!token) {
      this.setData({
        member: null,
        unreadCount: 0,
        unreadText: '',
        payoutCount: 0,
        orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, afterSale: 0 }
      })
      return
    }
    try {
      const member = await request({ url: '/shop/auth/me' })
      wx.setStorageSync('mall_mini_member', member)
      this.setData({ member })
      await Promise.all([this.loadUnread(), this.loadPayoutCount(), this.loadOrderSummary()])
    } catch (_) {
      this.setData({
        loggedIn: false, member: null, unreadCount: 0, unreadText: '', payoutCount: 0,
        orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, afterSale: 0 }
      })
    }
  },
  async loadUnread() {
    try {
      const unread = await request({ url: '/shop/messages/unread' })
      const count = Number(unread && unread.total ? unread.total : 0)
      this.setData({ unreadCount: count, unreadText: count > 99 ? '99+' : String(count || '') })
    } catch (_) { this.setData({ unreadCount: 0, unreadText: '' }) }
  },
  async loadPayoutCount() {
    try {
      const records = await request({ url: '/shop/wallet/withdrawals' })
      const payoutCount = (records || []).filter((item) => Number(item.withdrawType) === 2 && Number(item.status) === 2).length
      this.setData({ payoutCount })
    } catch (_) { this.setData({ payoutCount: 0 }) }
  },
  async loadOrderSummary() {
    try {
      const summary = await request({ url: '/shop/profile/order-summary' })
      this.setData({ orderSummary: {
        pendingPayment: Number(summary && summary.pendingPayment || 0),
        pendingShipment: Number(summary && summary.pendingShipment || 0),
        pendingReceipt: Number(summary && summary.pendingReceipt || 0),
        afterSale: Number(summary && summary.afterSale || 0)
      } })
    } catch (_) {
      this.setData({ orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, afterSale: 0 } })
    }
  },
  login() { wx.navigateTo({ url: '/pages/login/index' }) },
  messages() { if (this.requireLogin()) wx.navigateTo({ url: '/pages/messages/index' }) },
  orders() { if (this.requireLogin()) wx.navigateTo({ url: '/pages/orders/index' }) },
  orderTab(event) {
    if (!this.requireLogin()) return
    const tab = String(event.currentTarget.dataset.tab || 'all')
    wx.navigateTo({ url: `/pages/orders/index?tab=${tab}` })
  },
  addresses() { if (this.requireLogin()) wx.navigateTo({ url: '/pages/address/index' }) },
  payout() { if (this.requireLogin()) wx.navigateTo({ url: '/pages/payout/index' }) },
  service() {
    if (!this.requireLogin()) return
    wx.navigateTo({ url: '/pages/orders/index?tab=after-sale' })
  },
  requireLogin() { if (this.data.loggedIn) return true; this.login(); return false },
  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前商城账号吗？',
      success: async (result) => {
        if (!result.confirm) return
        try { await request({ url: '/shop/auth/logout', method: 'POST' }) } catch (_) {}
        session.clearSession()
        this.setData({
          unreadCount: 0, unreadText: '', payoutCount: 0,
          orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, afterSale: 0 }
        })
        this.refresh()
      }
    })
  }
})
