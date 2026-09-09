const request = require('./request')
const feedback = require('./feedback')
const auth = require('./auth')
const session = require('./session')
const format = require('./format')
const data = { reviews: [], reviewSummary: {}, reviewPage: 0, reviewTotal: 0, reviewLoading: false, reviewError: '', reviewFormVisible: false, reviewSubmitting: false, reviewContent: '', reviewRating: 5, ratingOptions: [1, 2, 3, 4, 5], ratingDistribution: [] }
function hide(page) { page.reviewGeneration = (page.reviewGeneration || 0) + 1; page.setData({ reviewLoading: false }) }
const methods = {
  reviewRoute() { return `/pages/order-review/index?id=${this.productId}${this.reviewOrderItemId ? '&orderItemId=' + this.reviewOrderItemId : ''}` },
  async loadReviews(reset = true) {
    reset = reset !== false
    if (!this.productId || this.purchaseInactive || (!reset && this.data.reviewLoading)) return false
    const token = session.getToken(), version = this.reviewGeneration = (this.reviewGeneration || 0) + 1
    const current = () => !this.purchaseInactive && token === session.getToken() && version === this.reviewGeneration
    const pageNum = reset ? 1 : this.data.reviewPage + 1
    this.setData({ reviewLoading: true, reviewError: '' })
    try {
      const result = await request({ url: `/shop/products/${this.productId}/reviews`, params: { pageNum, pageSize: 5, ...(this.reviewOrderItemId ? { orderItemId: this.reviewOrderItemId } : {}) } })
      if (!current()) return false
      if (!result || !result.page || !Array.isArray(result.page.list)) throw new Error('评价数据暂不可用，请重试')
      const maximum = Math.max(1, ...[1,2,3,4,5].map(star => Number(result[`star${star}Count`] || 0)))
      const rows = result.page.list.map(item => ({ ...item, reviewerAvatar: format.mediaUrl(item.reviewerAvatar), dateText: String(item.createTime || '').slice(0,10) }))
      this.setData({ reviewSummary: result, reviews: reset ? rows : this.data.reviews.concat(rows), reviewPage: pageNum, reviewTotal: Number(result.page.total || 0),
        ratingDistribution: [5,4,3,2,1].map(star => ({ star, count: Number(result[`star${star}Count`] || 0), percent: Math.round(Number(result[`star${star}Count`] || 0) / maximum * 100) })) })
      return true
    } catch (error) { if (current()) feedback.update(this, { reviewError: error.message || '评价加载失败，请重试' }); return false }
    finally { if (current()) this.setData({ reviewLoading: false }) }
  },
  loadMoreReviews() { if (this.data.reviews.length < this.data.reviewTotal) return this.loadReviews(false) },
  async openReviewForm() {
    if (!this.reviewOrderItemId) { await feedback.notice('请从我的订单中选择已完成的商品进行评价'); return }
    if (this.data.reviewSubmitting || !auth.requireLogin(this.reviewRoute())) return
    if (this.data.reviewFormVisible) { this.setData({ reviewFormVisible: false }); return }
    // Refresh eligibility for the exact order item; never infer it from paid status.
    if (!await this.loadReviews()) return
    if (this.data.reviewSummary.canReview !== true) { await feedback.notice(this.data.reviewSummary.reviewHint || '购买并确认收货后可以评价'); return }
    this.setData({ reviewFormVisible: true })
  },
  reviewInput(event) { if (!this.data.reviewSubmitting) this.setData({ reviewContent: String(event.detail.value || '').slice(0,1000) }) },
  chooseRating(event) { const rating = Number(event.currentTarget.dataset.rating); if (!this.data.reviewSubmitting && [1,2,3,4,5].includes(rating)) this.setData({ reviewRating: rating }) },
  async submitReview() {
    if (this.data.completed) return
    if (!this.reviewOrderItemId) { await feedback.notice('请从我的订单中选择已完成的商品进行评价'); return }
    if (this.data.reviewSubmitting || this.purchaseInactive || !auth.requireLogin(this.reviewRoute())) return
    const content = this.data.reviewContent.trim(), rating = this.data.reviewRating
    if (!content || ![1,2,3,4,5].includes(rating)) { await feedback.notice(!content ? '请填写评价内容' : '请选择1至5星评分'); return }
    const token = session.getToken(), version = this.reviewGeneration = (this.reviewGeneration || 0) + 1
    const current = () => !this.purchaseInactive && token === session.getToken() && version === this.reviewGeneration
    this.setData({ reviewSubmitting: true })
    try {
      await request({ url: `/shop/products/${this.productId}/reviews`, method: 'POST', data: { rating, content, ...(this.reviewOrderItemId ? { orderItemId: this.reviewOrderItemId } : {}) } })
      if (!current()) return
      this.setData({ reviewContent: '', reviewRating: 5, reviewFormVisible: false, reviewSubmitting: false })
      if (this.onReviewSubmitted) this.onReviewSubmitted()
      await feedback.notice('评价提交成功', '操作完成')
      if (current()) await this.loadReviews()
    } catch (error) { if (current()) await feedback.notice(error.message || '评价提交失败，请检查后重试', '未能提交评价') }
    finally { this.setData({ reviewSubmitting: false }) }
  }
}
module.exports = { data, methods, hide }
