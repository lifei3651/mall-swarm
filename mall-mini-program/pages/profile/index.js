const session = require('../../utils/session')
const request = require('../../utils/request')
const theme = require('../../utils/theme')
const avatar = require('../../utils/member-avatar')

Page({
  data: {
    ...theme.pageData(),
    loggedIn: false, member: null, loginVisible: false, avatarSrc: avatar.fallback, unreadCount: 0, unreadText: '', payoutCount: 0,
    orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, afterSale: 0 }
  },
  onShow() { theme.apply(this); this.setLoginVisible(this.data.loginVisible); this.refresh() },
  onHide() { this.refreshVersion = (this.refreshVersion || 0) + 1; avatar.release(this.data.avatarSrc); this.setData({ avatarSrc: avatar.fallback }) },
  onUnload() { this.onHide() },
  async refresh() {
    const version = this.refreshVersion = (this.refreshVersion || 0) + 1
    const token = session.getToken()
    this.setData({ loggedIn: Boolean(token), member: session.getMember() })
    if (!token) {
      avatar.release(this.data.avatarSrc)
      this.setData({
        member: null, avatarSrc: avatar.fallback,
        unreadCount: 0,
        unreadText: '',
        payoutCount: 0,
        orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, afterSale: 0 }
      })
      return
    }
    try {
      const member = await request({ url: '/shop/auth/me' })
      if (version !== this.refreshVersion || session.getToken() !== token) return
      wx.setStorageSync('mall_mini_member', member)
      this.setData({ member })
      const avatarSrc = await avatar.load(member.avatarUrl)
      if (version !== this.refreshVersion || session.getToken() !== token) { avatar.release(avatarSrc); return }
      avatar.release(this.data.avatarSrc)
      this.setData({ avatarSrc })
      await Promise.all([this.loadUnread(), this.loadPayoutCount(), this.loadOrderSummary()])
    } catch (_) {
      if (version !== this.refreshVersion || session.getToken() !== token) return
      avatar.release(this.data.avatarSrc)
      this.setData({
        loggedIn: false, member: null, avatarSrc: avatar.fallback, unreadCount: 0, unreadText: '', payoutCount: 0,
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
  accountEntry() {
    if (this.data.loggedIn && session.getToken()) this.security()
    else this.login()
  },
  login(redirect = '') {
    const panel = this.selectComponent('#login-sheet')
    if (!panel) { wx.showToast({ title: '登录入口加载中，请稍后重试', icon: 'none' }); return }
    this.setLoginVisible(true)
    panel.open(redirect)
  },
  setLoginVisible(visible) {
    this.setData({ loginVisible: visible })
    const tab = typeof this.getTabBar === 'function' && this.getTabBar()
    if (tab) tab.setData({ hidden: visible })
  },
  loginClosed() { this.setLoginVisible(false) },
  authorized(event) {
    this.loginClosed()
    if (!session.getToken()) return
    this.refresh()
    const redirect = event && event.detail && event.detail.redirect
    const allowed = new Set(['/pages/account-security/index', '/pages/messages/index', '/pages/orders/index',
      '/pages/address/index', '/pages/payout/index', '/pages/wallet/index'])
    if (typeof redirect === 'string' && allowed.has(redirect.split('?')[0])) wx.navigateTo({ url: redirect })
  },
  legal() { wx.navigateTo({ url: '/pages/legal/index' }) },
  openMemberPage(url) { if (this.requireLogin(url)) wx.navigateTo({ url }) },
  security() { this.openMemberPage('/pages/account-security/index') },
  messages() { this.openMemberPage('/pages/messages/index') },
  orders() { this.openMemberPage('/pages/orders/index') },
  orderTab(event) {
    const value = event && event.currentTarget && event.currentTarget.dataset && event.currentTarget.dataset.tab
    const tab = ['pending-payment', 'pending-shipment', 'pending-receipt', 'after-sale'].includes(value) ? value : 'all'
    this.openMemberPage(`/pages/orders/index?tab=${tab}`)
  },
  addresses() { this.openMemberPage('/pages/address/index') },
  payout() { this.openMemberPage('/pages/payout/index') },
  wallet() { this.openMemberPage('/pages/wallet/index') },
  service() { this.openMemberPage('/pages/orders/index?tab=after-sale') },
  requireLogin(redirect = '/pages/profile/index') {
    if (this.data.loggedIn && session.getToken()) return true
    this.login(redirect)
    return false
  },
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
