const feedback = require('../../utils/feedback')
const session = require('../../utils/session')
const request = require('../../utils/request')
const theme = require('../../utils/theme')
const avatar = require('../../utils/member-avatar')
const share = require('../../utils/share')
const capabilities = require('../../utils/member-capabilities')

Page({
  data: {
    ...theme.pageData(),
    capabilities: capabilities.empty(), shareReady: false, shareError: '',
    loggedIn: false, member: null, loginVisible: false, canOpenStudio: false, avatarSrc: avatar.fallback, unreadCount: 0, unreadText: '', payoutCount: 0,
    orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, pendingReview: 0, afterSale: 0 }
  },
  onShow() { this.hidden = false; if (typeof wx.setNavigationBarTitle === 'function') wx.setNavigationBarTitle({ title: '我的' }); theme.apply(this); this.setLoginVisible(this.data.loginVisible); return this.refresh() },
  onHide() { this.hidden = true; share.hide(this); this.refreshVersion = (this.refreshVersion || 0) + 1; this.setData({ shareReady: false }) },
  onUnload() { this.onHide(); avatar.release(this.data.avatarSrc) },
  currentRefresh(version, token) { return !this.hidden && version === this.refreshVersion && token === session.getToken() },
  async refresh() {
    const version = this.refreshVersion = (this.refreshVersion || 0) + 1
    const token = session.getToken()
    const sameOwner = Boolean(token) && token === this.displayToken
    this.displayToken = token
    if (!sameOwner) {
      avatar.release(this.data.avatarSrc)
      feedback.update(this, { capabilities: capabilities.empty(), canOpenStudio: false, shareReady: false, avatarSrc: avatar.fallback, unreadCount: 0, unreadText: '', payoutCount: 0,
        orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, pendingReview: 0, afterSale: 0 } })
    }
    const rights = this.loadCapabilities(version, token)
    feedback.update(this, { loggedIn: Boolean(token), member: session.getMember() })
    if (!token) {
      avatar.release(this.data.avatarSrc)
      feedback.update(this, {
        member: null, avatarSrc: avatar.fallback,
        unreadCount: 0,
        unreadText: '',
        payoutCount: 0,
        orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, pendingReview: 0, afterSale: 0 }
      })
      return
    }
    try {
      const member = await request({ url: '/shop/auth/me' })
      if (!this.currentRefresh(version, token)) return
      wx.setStorageSync('mall_mini_member', member)
      feedback.update(this, { member })
      const avatarSrc = await avatar.load(member.avatarUrl)
      if (!this.currentRefresh(version, token)) { avatar.release(avatarSrc); return }
      avatar.release(this.data.avatarSrc)
      feedback.update(this, { avatarSrc })
      await Promise.all([this.loadUnread(version, token), this.loadPayoutCount(version, token), this.loadOrderSummary(version, token), this.loadStudio(version, token)])
      await rights
    } catch (_) {
      if (!this.currentRefresh(version, token)) return
      share.hide(this)
      avatar.release(this.data.avatarSrc)
      feedback.update(this, {
        capabilities: capabilities.empty(), canOpenStudio: false, shareReady: false,
        loggedIn: false, member: null, avatarSrc: avatar.fallback, unreadCount: 0, unreadText: '', payoutCount: 0,
        orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, pendingReview: 0, afterSale: 0 }
      })
    }
  },
  async loadCapabilities(version = this.refreshVersion, token = session.getToken()) {
    const result = await share.prepare(this)
    if (this.currentRefresh(version, token)) {
      // Preserve same-owner presentation while checking, but revoke stale rights on failure.
      feedback.update(this, { capabilities: result || capabilities.empty() })
    }
  },
  retryShare() { return this.loadCapabilities() },
  checkUpdate() { return require('../../utils/app-update').check() },
  async loadStudio(version, token) {
    try { const studio = await request({ url: '/shop/live-studio/me' }); if (this.currentRefresh(version, token)) this.setData({ canOpenStudio: !!studio?.anchor?.anchor }) }
    catch (_) { if (this.currentRefresh(version, token)) this.setData({ canOpenStudio: false }) }
  },
  studio() { if (this.data.loggedIn && this.data.canOpenStudio && session.getToken()) wx.navigateTo({ url: '/pages/live-studio/index' }) },
  onShareAppMessage() { return share.message(this, '/pages/home/index', this.data.brandName) },
  async loadUnread(version = this.refreshVersion, token = session.getToken()) {
    try {
      const unread = await request({ url: '/shop/messages/unread' })
      if (!this.currentRefresh(version, token)) return
      const count = Number(unread && unread.total ? unread.total : 0)
      feedback.update(this, { unreadCount: count, unreadText: count > 99 ? '99+' : String(count || '') })
    } catch (_) { if (this.currentRefresh(version, token)) feedback.update(this, { unreadCount: 0, unreadText: '' }) }
  },
  async loadPayoutCount(version = this.refreshVersion, token = session.getToken()) {
    try {
      const records = await request({ url: '/shop/wallet/withdrawals' })
      if (!this.currentRefresh(version, token)) return
      const payoutCount = (records || []).filter((item) => Number(item.withdrawType) === 2 && Number(item.status) === 2).length
      feedback.update(this, { payoutCount })
    } catch (_) { if (this.currentRefresh(version, token)) feedback.update(this, { payoutCount: 0 }) }
  },
  async loadOrderSummary(version = this.refreshVersion, token = session.getToken()) {
    try {
      const summary = await request({ url: '/shop/profile/order-summary' })
      if (!this.currentRefresh(version, token)) return
      feedback.update(this, { orderSummary: {
        pendingPayment: Number(summary && summary.pendingPayment || 0),
        pendingShipment: Number(summary && summary.pendingShipment || 0),
        pendingReceipt: Number(summary && summary.pendingReceipt || 0),
        pendingReview: Number(summary && summary.pendingReview || 0),
        afterSale: Number(summary && summary.afterSale || 0)
      } })
    } catch (_) {
      if (!this.currentRefresh(version, token)) return
      feedback.update(this, { orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, pendingReview: 0, afterSale: 0 } })
    }
  },
  accountEntry() {
    if (this.data.loggedIn && session.getToken()) this.openMemberPage('/pages/account-security/index')
    else this.login()
  },
  login(redirect = '') {
    const panel = this.selectComponent('#login-sheet')
    if (!panel) { feedback.toast({ title: '登录入口加载中，请稍后重试', icon: 'none' }); return }
    this.setLoginVisible(true)
    panel.open(redirect)
  },
  setLoginVisible(visible) {
    feedback.update(this, { loginVisible: visible })
    if (visible) share.hide(this)
    const tab = typeof this.getTabBar === 'function' && this.getTabBar()
    if (tab) tab.setData({ hidden: visible })
  },
  loginClosed() { this.setLoginVisible(false); this.loadCapabilities() },
  authorized(event) {
    this.setLoginVisible(false)
    if (!session.getToken()) return
    const token = session.getToken()
    this.refresh()
    const redirect = event && event.detail && event.detail.redirect
    const fail = () => { if (token === session.getToken()) feedback.notice('账号已登录，但目标页面未能打开，请重新点击入口。') }
    if (redirect === '/pages/home/index') { wx.switchTab({ url: redirect, fail }); return }
    const allowed = new Set(['/pages/account-security/index', '/pages/account-settings/index', '/pages/messages/index', '/pages/orders/index',
      '/pages/address/index', '/pages/payout/index', '/pages/wallet/index', '/pages/support/index'])
    if (typeof redirect === 'string' && allowed.has(redirect.split('?')[0])) wx.navigateTo({ url: redirect, fail })
  },
  legal() { wx.navigateTo({ url: '/pages/legal/index' }) },
  openMemberPage(url) { if (this.requireLogin(url)) wx.navigateTo({ url }) },
  security() { this.openMemberPage('/pages/account-settings/index?section=security') },
  messages() { this.openMemberPage('/pages/messages/index') },
  orders() { this.openMemberPage('/pages/orders/index') },
  orderTab(event) {
    const value = event && event.currentTarget && event.currentTarget.dataset && event.currentTarget.dataset.tab
    const tab = ['pending-payment', 'pending-shipment', 'pending-receipt', 'pending-review', 'after-sale'].includes(value) ? value : 'all'
    this.openMemberPage(`/pages/orders/index?tab=${tab}`)
  },
  addresses() { this.openMemberPage('/pages/address/index') },
  coupons() { this.openMemberPage('/pages/coupons/index') },
  payout() { this.openMemberPage('/pages/payout/index') },
  wallet() { this.openMemberPage('/pages/wallet/index') },
  service() { this.openMemberPage('/pages/orders/index?tab=after-sale') },
  support() { this.openMemberPage('/pages/support/index') },
  contact() { wx.navigateTo({ url: '/pages/legal/index?type=contact', fail: () => feedback.notice('客服页面暂时无法打开，请稍后重试') }) },
  requireLogin(redirect = '/pages/profile/index') {
    if (this.data.loggedIn && session.getToken()) return true
    this.login(redirect)
    return false
  },
  logout() {
    const token = session.getToken()
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前商城账号吗？',
      success: async (result) => {
        if (!result.confirm || token !== session.getToken()) return
        try { await request({ url: '/shop/auth/logout', method: 'POST' }) } catch (_) {}
        if (token !== session.getToken()) return
        session.clearSession({ clearCart: true })
        feedback.update(this, {
          unreadCount: 0, unreadText: '', payoutCount: 0,
          orderSummary: { pendingPayment: 0, pendingShipment: 0, pendingReceipt: 0, pendingReview: 0, afterSale: 0 }
        })
        this.refresh()
      }
    })
  }
})
