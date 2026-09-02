const request = require('../../utils/request')
const auth = require('../../utils/auth')
const format = require('../../utils/format')

function requestMerchantTransfer(parameters) {
  return new Promise((resolve, reject) => {
    wx.requestMerchantTransfer({ ...parameters, success: resolve, fail: reject })
  })
}

function isUserCancel(error) {
  return /cancel/i.test(String(error && (error.errMsg || error.message || error)))
}

Page({
  data: { loading: true, error: '', rows: [], actingId: null },
  onLoad() { if (auth.requireLogin('/pages/payout/index')) this.load() },
  onShow() { if (this.loadedOnce && auth.requireLogin('/pages/payout/index')) this.load() },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const records = await request({ url: '/shop/wallet/withdrawals' })
      const rows = (records || [])
        .filter((item) => Number(item.withdrawType) === 2 && Number(item.status) === 2)
        .map((item) => ({ ...item, amountText: format.money(item.withdrawAmount) }))
      this.loadedOnce = true
      this.setData({ rows })
    } catch (error) {
      this.setData({ error: error.message || '收款记录加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },
  async confirm(event) {
    const withdrawId = Number(event.currentTarget.dataset.id)
    if (!withdrawId || this.data.actingId) return
    if (!wx.canIUse || !wx.canIUse('requestMerchantTransfer')) {
      wx.showModal({
        title: '当前微信版本暂不支持',
        content: '请升级微信后再确认收款。',
        showCancel: false
      })
      return
    }
    this.setData({ actingId: withdrawId })
    wx.showLoading({ title: '正在核对', mask: true })
    try {
      const detail = await this.prepare(withdrawId)
      if (detail.state === 'SUCCESS') {
        wx.hideLoading()
        wx.showToast({ title: '收款成功', icon: 'success' })
        await this.load()
        return
      }
      if (detail.state !== 'WAIT_USER_CONFIRM' || !detail.mchId || !detail.appId || !detail.packageInfo) {
        wx.hideLoading()
        wx.showToast({ title: '渠道仍在处理中，请稍后重试', icon: 'none', duration: 2600 })
        await this.load()
        return
      }
      wx.hideLoading()
      await requestMerchantTransfer({ mchId: detail.mchId, appId: detail.appId, package: detail.packageInfo })
      wx.showLoading({ title: '正在确认结果', mask: true })
      const verified = await this.prepare(withdrawId)
      wx.hideLoading()
      wx.showToast({
        title: verified.state === 'SUCCESS' ? '收款成功' : '结果确认中，请稍后查看',
        icon: verified.state === 'SUCCESS' ? 'success' : 'none',
        duration: 2600
      })
      await this.load()
    } catch (error) {
      wx.hideLoading()
      wx.showToast({
        title: isUserCancel(error) ? '已取消确认，可稍后继续' : (error.message || '确认收款失败'),
        icon: 'none',
        duration: 2600
      })
    } finally {
      this.setData({ actingId: null })
    }
  },
  prepare(withdrawId) {
    return request({
      url: `/shop/wallet/withdrawals/${withdrawId}/wechat-confirmation`,
      method: 'POST'
    })
  }
})
