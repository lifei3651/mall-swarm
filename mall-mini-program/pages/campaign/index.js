const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const format = require('../../utils/format')
const theme = require('../../utils/theme')
const labels = { UPCOMING: '即将开始', ACTIVE: '立即抢购', SOLD_OUT: '已抢完', ENDED: '已结束', DISABLED: '暂不可用' }
Page({
  data: { ...theme.pageData(), loading: true, error: '', rows: [] },
  onLoad(options = {}) { theme.apply(this); this.activityId = format.identifier(options.id); this.load() },
  onShow() { if (this.loadedOnce && !this.fetching) this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  async load() {
    if (this.fetching) return
    this.fetching = true
    feedback.update(this, { loading: true, error: '' })
    try {
      const result = await request({ url: '/shop/flash-sales' })
      const rows = (result || []).filter((row) => row && row.activity && row.product && (!this.activityId || String(row.activity.id) === this.activityId)).map((row) => {
        const maximum = Math.max(0, Math.min(99, Number(row.activity.perUserLimit || 0), Number(row.activity.availableStock || 0)))
        return { ...row, product: format.product(row.product), priceText: format.money(row.activity.flashPrice), quantity: 1, quantities: Array.from({ length: maximum }, (_, index) => index + 1), canBuy: row.activityState === 'ACTIVE' && maximum > 0, label: labels[row.activityState] || '暂不可用' }
      })
      feedback.update(this, { rows }); this.loadedOnce = true
    } catch (error) { feedback.update(this, { error: error.message || '活动加载失败' }) }
    finally { this.fetching = false; feedback.update(this, { loading: false }) }
  },
  quantityChange(event) {
    const index = this.data.rows.findIndex((row) => String(row.activity.id) === String(event.currentTarget.dataset.id))
    const choice = Number(event.detail.value)
    if (index >= 0 && Number.isInteger(choice) && this.data.rows[index].quantities[choice]) feedback.update(this, { [`rows[${index}].quantity`]: this.data.rows[index].quantities[choice] })
  },
  imageError(event) { const index = Number(event.currentTarget.dataset.index); if (Number.isInteger(index) && this.data.rows[index]) feedback.update(this, { [`rows[${index}].product.imageFailed`]: true }) },
  buy(event) {
    const row = this.data.rows.find((item) => String(item.activity.id) === String(event.currentTarget.dataset.id))
    if (!row || !row.canBuy || this.data.loading || this.data.error) return
    const id = format.identifier(row.activity.id)
    if (!id || !row.quantities.includes(row.quantity)) return
    const url = `/pages/checkout/index?activityId=${id}&quantity=${row.quantity}`
    if (auth.requireLogin(url)) wx.navigateTo({ url })
  }
})
