const request = require('../../utils/request')
const cart = require('../../utils/cart')
const format = require('../../utils/format')
const auth = require('../../utils/auth')
const payment = require('../../utils/payment')
const theme = require('../../utils/theme')

function idempotencyKey() {
  return `MINI-CHECKOUT-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
}

Page({
  data: {
    ...theme.pageData(),
    loading: true,
    submitting: false,
    rows: [],
    address: null,
    count: 0,
    total: '0.00',
    freight: '0.00',
    payTotal: '0.00',
    wechatPayEnabled: false,
    needSmsVerify: false,
    smsCode: '',
    smsCooldown: 0,
    smsSending: false,
    remark: ''
  },
  onLoad() {
    theme.apply(this)
    if (!auth.requireLogin('/pages/checkout/index')) return
    this.submitKey = idempotencyKey()
  },
  onShow() { theme.sync(this); if (auth.requireLogin('/pages/checkout/index')) this.load() },
  async load() {
    const rows = cart.selected().map((row) => ({
      ...row,
      coverUrl: format.mediaUrl(row.coverUrl),
      priceText: format.money(row.salePrice)
    }))
    if (!rows.length) {
      wx.showToast({ title: '没有待结算商品', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 600)
      return
    }
    this.setData({ loading: true, rows,
      count: rows.reduce((sum, row) => sum + row.quantity, 0),
      total: format.money(rows.reduce((sum, row) => sum + Number(row.salePrice) * row.quantity, 0)) })
    try {
      const [addresses, config] = await Promise.all([
        request({ url: '/shop/addresses' }), request({ url: '/shop/pay/config' })
      ])
      const address = (addresses || []).find((item) => item.isDefault === 1) || (addresses || [])[0] || null
      this.setData({ address, wechatPayEnabled: Boolean(config.wechatPayEnabled) })
      if (address) await this.quoteFreight(address)
    } catch (error) {
      wx.showToast({ title: error.message || '结算信息加载失败', icon: 'none' })
    } finally { this.setData({ loading: false }) }
  },
  chooseAddress() { wx.navigateTo({ url: '/pages/address/index?select=1' }) },
  async quoteFreight(address) {
    try {
      const quote = await request({
        url: '/shop/orders/freight-quote', method: 'POST',
        data: this.orderPayload(address, false)
      })
      this.setData({
        total: format.money(quote.productAmount),
        freight: format.money(quote.freightAmount),
        payTotal: format.money(quote.payAmount)
      })
      await this.checkPaymentVerify(quote.payAmount)
    } catch (error) {
      this.setData({ freight: '--', payTotal: this.data.total })
      throw error
    }
  },
  async checkPaymentVerify(amount) {
    const config = await request({ url: '/payment/checkVerify', params: { amount } })
    this.setData({ needSmsVerify: Boolean(config && config.needVerify) })
  },
  smsCodeInput(event) {
    this.setData({ smsCode: String(event.detail.value || '').replace(/\D/g, '').slice(0, 6) })
  },
  async sendPaymentSms() {
    if (this.data.smsSending || this.data.smsCooldown > 0) return
    this.setData({ smsSending: true })
    try {
      await request({ url: '/sms/send/payment', method: 'POST' })
      this.setData({ smsCooldown: 60 })
      clearInterval(this.smsTimer)
      this.smsTimer = setInterval(() => {
        const next = Math.max(0, this.data.smsCooldown - 1)
        this.setData({ smsCooldown: next })
        if (!next) clearInterval(this.smsTimer)
      }, 1000)
      wx.showToast({ title: '验证码已发送', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message || '验证码发送失败', icon: 'none' })
    } finally { this.setData({ smsSending: false }) }
  },
  onUnload() { clearInterval(this.smsTimer) },
  orderPayload(address, includeRemark = true) {
    return {
      addressId: address.id,
      payType: 'WECHAT',
      businessType: 'NORMAL',
      remark: includeRemark && this.data.remark ? this.data.remark : undefined,
      smsCode: this.data.needSmsVerify ? this.data.smsCode : undefined,
      items: this.data.rows.map((row) => ({ productId: row.productId, skuId: row.skuId || undefined, quantity: row.quantity }))
    }
  },
  remarkInput(event) { this.setData({ remark: String(event.detail.value || '').slice(0, 500) }) },
  async submit() {
    if (this.data.submitting) return
    if (!this.data.address) { wx.showToast({ title: '请先添加收货地址', icon: 'none' }); return }
    if (!this.data.wechatPayEnabled) {
      wx.showModal({ title: '微信支付暂未开放', content: '当前客户尚未完成微信支付商户资料配置与真实联调，因此不会创建无法支付的新订单。', showCancel: false })
      return
    }
    if (this.data.needSmsVerify && !/^\d{6}$/.test(this.data.smsCode)) {
      wx.showToast({ title: '请输入6位支付验证码', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    wx.showLoading({ title: '正在提交订单', mask: true })
    let paymentId = null
    try {
      const order = await request({
        url: '/shop/orders', method: 'POST', idempotencyKey: this.submitKey,
        data: this.orderPayload(this.data.address)
      })
      paymentId = order.checkoutId || order.order.id
      cart.clearSelected()
      wx.hideLoading()
      const confirmed = await payment.payOrder(paymentId)
      wx.showToast({ title: confirmed ? '支付成功' : '支付结果确认中', icon: confirmed ? 'success' : 'none' })
      setTimeout(() => wx.redirectTo({ url: '/pages/orders/index' }), 700)
    } catch (error) {
      wx.hideLoading()
      if (paymentId) {
        wx.showModal({
          title: '订单已保留',
          content: payment.isUserCancel(error)
            ? '你已取消微信支付，可在“我的订单 → 待支付”继续付款。'
            : `微信支付暂未完成：${error.message || '请稍后重试'}。订单已保留在待支付。`,
          showCancel: false,
          success: () => wx.redirectTo({ url: '/pages/orders/index' })
        })
      } else {
        wx.showToast({ title: error.message || '订单提交失败', icon: 'none', duration: 2600 })
      }
    } finally { this.setData({ submitting: false }) }
  }
})
