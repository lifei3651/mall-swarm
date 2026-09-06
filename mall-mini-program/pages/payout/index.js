const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const format = require('../../utils/format')
const theme = require('../../utils/theme')

function requestMerchantTransfer(parameters) {
  return new Promise((resolve, reject) => {
    wx.requestMerchantTransfer({ ...parameters, success: resolve, fail: reject })
  })
}

function isUserCancel(error) {
  return /cancel/i.test(String(error && (error.errMsg || error.message || error)))
}

Page({
  data: { ...theme.pageData(), loading: true, error: '', rows: [], actingId: null, history: false },
  onLoad(options = {}) { theme.apply(this); this.withdrawId = format.identifier(options.id); feedback.update(this, { history: options.history === '1' || Boolean(this.withdrawId) }) },
  onShow() { theme.apply(this); if (!this.fetching) return this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  async load() {
    if (this.fetching || !auth.requireLogin('/pages/payout/index')) return
    this.fetching = true
    feedback.update(this, { loading: true, error: '' })
    try {
      const records = await request({ url: '/shop/wallet/withdrawals' })
      const rows = (records || [])
        .filter((item) => this.withdrawId ? format.identifier(item.id) === this.withdrawId : this.data.history || (Number(item.withdrawType) === 2 && Number(item.status) === 2))
        .map((item) => ({ ...item, id: format.identifier(item.id), amountText: format.money(item.withdrawAmount),
          canConfirm: Number(item.withdrawType) === 2 && Number(item.status) === 2,
          timeText: String(item.createTime || '').replace('T', ' ').slice(0, 16) }))
      this.loadedOnce = true
      feedback.update(this, { rows })
    } catch (error) {
      feedback.update(this, { error: error.message || '收款记录加载失败' })
    } finally {
      this.fetching = false
      feedback.update(this, { loading: false })
    }
  },
  async confirm(event) {
    const withdrawId = format.identifier(event.currentTarget.dataset.id)
    if (!withdrawId || this.data.actingId || !this.data.rows.some((row) => format.identifier(row.id) === withdrawId && row.canConfirm)) return
    if (!wx.canIUse || !wx.canIUse('requestMerchantTransfer')) {
      wx.showModal({
        title: '当前微信版本暂不支持',
        content: '请升级微信后再确认收款。',
        showCancel: false
      })
      return
    }
    feedback.update(this, { actingId: withdrawId })
    wx.showLoading({ title: '正在核对', mask: true })
    try {
      const detail = await this.prepare(withdrawId)
      if (detail.state === 'SUCCESS') {
        wx.hideLoading()
        feedback.toast({ title: '收款成功', icon: 'success' })
        await this.load()
        return
      }
      if (detail.state !== 'WAIT_USER_CONFIRM' || !detail.mchId || !detail.appId || !detail.packageInfo) {
        wx.hideLoading()
        feedback.toast({ title: '渠道仍在处理中，请稍后重试', icon: 'none', duration: 2600 })
        await this.load()
        return
      }
      wx.hideLoading()
      await requestMerchantTransfer({ mchId: detail.mchId, appId: detail.appId, package: detail.packageInfo })
      wx.showLoading({ title: '正在确认结果', mask: true })
      const verified = await this.prepare(withdrawId)
      wx.hideLoading()
      feedback.toast({
        title: verified.state === 'SUCCESS' ? '收款成功' : '结果确认中，请稍后查看',
        icon: verified.state === 'SUCCESS' ? 'success' : 'none',
        duration: 2600
      })
      await this.load()
    } catch (error) {
      wx.hideLoading()
      feedback.toast({
        title: isUserCancel(error) ? '已取消确认，可稍后继续' : (error.message || '确认收款失败'),
        icon: 'none',
        duration: 2600
      })
    } finally {
      feedback.update(this, { actingId: null })
    }
  },
  prepare(withdrawId) {
    return request({
      url: `/shop/wallet/withdrawals/${withdrawId}/wechat-confirmation`,
      method: 'POST'
    })
  }
})
