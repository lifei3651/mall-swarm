const flow = require('../../utils/login-flow')

// Direct links from checkout/orders keep working. Profile uses this same form
// in-place, without pushing another page onto the navigation stack.
Page({
  onLoad(options = {}) { this.options = options },
  onReady() {
    let redirect = ''
    try { redirect = decodeURIComponent(this.options.redirect || '') } catch (_) {}
    this.selectComponent('#login-sheet').open(redirect)
  },
  authorized(event) { this.redirect = event.detail.redirect; flow.finish.call(this) },
  close() { wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/index' }) }) }
})
