const reviews = require('../../utils/product-reviews')
const theme = require('../../utils/theme')
const session = require('../../utils/session')
const format = require('../../utils/format')
const auth = require('../../utils/auth')
Page({
  ...reviews.methods,
  data: { ...theme.pageData(), ...reviews.data, invalid: false, completed: false },
  onLoad(options = {}) {
    theme.apply(this)
    this.productId = format.identifier(options.id)
    this.reviewOrderItemId = format.identifier(options.orderItemId)
    this.setData({ invalid: !this.productId || !this.reviewOrderItemId })
  },
  async onShow() {
    this.purchaseInactive = false
    theme.sync(this)
    if (this.reviewOwner !== session.getToken()) {
      this.reviewOwner = session.getToken()
      this.setData({ ...reviews.data, completed: false })
    }
    if (!this.data.invalid && !this.data.completed && auth.requireLogin(this.reviewRoute())) await this.openReviewForm()
  },
  onHide() { this.purchaseInactive = true; reviews.hide(this); this.setData({ reviewFormVisible: false }) },
  onUnload() { this.onHide() },
  onReviewSubmitted() { this.setData({ completed: true, reviewFormVisible: false }) },
  backToOrders() { wx.redirectTo({ url: '/pages/orders/index?tab=pending-review' }) }
})
