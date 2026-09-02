const session = require('../../utils/session')
const request = require('../../utils/request')

Page({
  data: { loggedIn: false, member: null },
  onShow() { this.refresh() },
  async refresh() {
    const token = session.getToken()
    this.setData({ loggedIn: Boolean(token), member: session.getMember() })
    if (!token) return
    try {
      const member = await request({ url: '/shop/auth/me' })
      wx.setStorageSync('mall_mini_member', member)
      this.setData({ member })
    } catch (_) { this.setData({ loggedIn: false, member: null }) }
  },
  login() { wx.navigateTo({ url: '/pages/login/index' }) },
  orders() { if (this.requireLogin()) wx.navigateTo({ url: '/pages/orders/index' }) },
  addresses() { if (this.requireLogin()) wx.navigateTo({ url: '/pages/address/index' }) },
  payout() { if (this.requireLogin()) wx.navigateTo({ url: '/pages/payout/index' }) },
  service() { wx.showToast({ title: '售后入口随订单履约阶段开放', icon: 'none' }) },
  requireLogin() { if (this.data.loggedIn) return true; this.login(); return false },
  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前商城账号吗？',
      success: async (result) => {
        if (!result.confirm) return
        try { await request({ url: '/shop/auth/logout', method: 'POST' }) } catch (_) {}
        session.clearSession()
        this.refresh()
      }
    })
  }
})
