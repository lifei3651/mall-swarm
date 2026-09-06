const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const cart = require('../../utils/cart')
const format = require('../../utils/format')
const auth = require('../../utils/auth')
const payment = require('../../utils/payment')
const theme = require('../../utils/theme')
const catalog = require('../../utils/catalog')

function idempotencyKey() {
  return `MINI-CHECKOUT-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
}

Page({
  data: {
    ...theme.pageData(),
    loading: true,
    loadError: '',
    submitting: false,
    quoteLoading: false,
    quoteReady: false,
    quoteError: '',
    activityName: '',
    rows: [],
    address: null,
    count: 0,
    total: '0.00',
    freight: '--',
    payTotal: '--',
    wechatPayEnabled: false,
    needSmsVerify: false,
    smsCode: '',
    smsCooldown: 0,
    smsSending: false,
    remark: ''
  },
  onLoad(options = {}) {
    theme.apply(this)
    this.flashSaleMode = Object.prototype.hasOwnProperty.call(options, 'activityId')
    this.directMode = options.direct === '1'
    this.activityId = this.flashSaleMode ? format.identifier(options.activityId) : ''
    this.activityQuantity = options.quantity === undefined ? 1 : Number(options.quantity)
    this.route = this.flashSaleMode
      ? `/pages/checkout/index?activityId=${encodeURIComponent(options.activityId || '')}&quantity=${encodeURIComponent(options.quantity === undefined ? '1' : options.quantity)}`
      : this.directMode ? '/pages/checkout/index?direct=1' : '/pages/checkout/index'
    this.submitKey = idempotencyKey()
  },
  onShow() {
    theme.apply(this)
    if (this.data.submitting || this.createdPaymentId) return
    if (auth.requireLogin(this.route || '/pages/checkout/index')) {
      if (!this.submitKey) this.submitKey = idempotencyKey()
      return this.load()
    }
    this.invalidateQuote()
    feedback.update(this, { loading: false, address: null, wechatPayEnabled: false })
  },
  invalidateQuote() {
    this.quoteGeneration = (this.quoteGeneration || 0) + 1
    this.quotedPayload = ''
    feedback.update(this, { quoteReady: false, quoteLoading: false, quoteError: '', freight: '--', payTotal: '--', needSmsVerify: false })
  },
  async load() {
    if (this.data.submitting || this.createdPaymentId) return
    const generation = this.loadGeneration = (this.loadGeneration || 0) + 1
    this.invalidateQuote()
    feedback.update(this, { loading: true, loadError: '', wechatPayEnabled: false })
    try {
      if (this.flashSaleMode && this.directMode) throw new Error('结算入口无效，请返回重新选择')
      const source = this.flashSaleMode ? await this.loadActivity() : { rows: this.directMode ? cart.directItems() : cart.selected(), activityName: '' }
      if (!this.flashSaleMode) {
        source.rows = await catalog.refresh(source.rows)
        const invalid = source.rows.find((row) => row.unavailable)
        if (invalid) throw new Error(invalid.unavailable)
      }
      if (generation !== this.loadGeneration) return
      const rows = source.rows.map((row) => {
        const productId = format.identifier(row.productId)
        const skuId = row.skuId === null || row.skuId === undefined || row.skuId === '' ? '' : format.identifier(row.skuId)
        if (!productId || (row.skuId && !skuId) || !Number.isInteger(row.quantity) || row.quantity < 1 || row.quantity > 99) {
          throw new Error('待结算商品信息无效，请返回重新选择')
        }
        return { ...row, productId, skuId, coverUrl: format.mediaUrl(row.coverUrl), priceText: format.money(row.salePrice) }
      })
      feedback.update(this, { rows, activityName: source.activityName,
        count: rows.reduce((sum, row) => sum + row.quantity, 0),
        total: format.money(rows.reduce((sum, row) => sum + Number(row.salePrice) * row.quantity, 0)) })
      if (!rows.length) throw new Error('没有待结算商品，请返回购物车选择')
      const [rawAddresses, config] = await Promise.all([
        request({ url: '/shop/addresses' }), request({ url: '/shop/pay/config' })
      ])
      if (generation !== this.loadGeneration) return
      const addresses = (rawAddresses || []).filter((item) => format.identifier(item.id))
      const currentId = this.selectedAddressId || (this.data.address && String(this.data.address.id))
      const address = (addresses || []).find((item) => String(item.id) === currentId)
        || (addresses || []).find((item) => Number(item.isDefault) === 1) || (addresses || [])[0] || null
      this.selectedAddressId = address ? String(address.id) : ''
      feedback.update(this, { address, wechatPayEnabled: Boolean(config && config.wechatPayEnabled === true) })
      if (address) await this.quoteFreight(address)
    } catch (error) {
      if (generation !== this.loadGeneration) return
      feedback.update(this, { loadError: error.message || '结算信息加载失败' })
      feedback.toast({ title: error.message || '结算信息加载失败', icon: 'none' })
    } finally { if (generation === this.loadGeneration) feedback.update(this, { loading: false }) }
  },
  async loadActivity() {
    if (!this.activityId || !Number.isInteger(this.activityQuantity) || this.activityQuantity < 1 || this.activityQuantity > 99) {
      throw new Error('活动结算参数无效，请返回活动页重新选择')
    }
    const activities = await request({ url: '/shop/flash-sales' })
    const source = (activities || []).find((item) => format.identifier(item.activity && item.activity.id) === this.activityId)
    if (!source || source.activityState !== 'ACTIVE') throw new Error('该活动当前不可购买，请返回活动页查看')
    const activity = source.activity, product = source.product || {}, sku = source.sku
    const productId = format.identifier(product.id)
    const skuId = sku ? format.identifier(sku.id) : ''
    const activitySkuId = activity.skuId === null || activity.skuId === undefined ? '' : format.identifier(activity.skuId)
    if (!productId || productId !== format.identifier(activity.productId)
      || skuId !== activitySkuId || (activity.skuId !== null && activity.skuId !== undefined && !activitySkuId)) {
      throw new Error('活动商品信息不完整，请返回活动页重试')
    }
    const limit = Number(activity.perUserLimit), stock = Number(activity.availableStock)
    if (!Number.isInteger(limit) || !Number.isInteger(stock) || this.activityQuantity > Math.min(limit, stock)) {
      throw new Error('购买数量超过活动限购或剩余库存，请返回活动页调整')
    }
    if (activity.flashPrice === null || activity.flashPrice === undefined || !Number.isFinite(Number(activity.flashPrice)) || Number(activity.flashPrice) <= 0) {
      throw new Error('活动价格暂不可用，请稍后重试')
    }
    return { activityName: activity.activityName || '限时活动', rows: [{
      key: `flash-${this.activityId}`, productId, skuId, productName: product.productName,
      skuName: sku ? sku.skuName : '', coverUrl: (sku && sku.coverUrl) || product.coverUrl,
      salePrice: activity.flashPrice, quantity: this.activityQuantity
    }] }
  },
  acceptSelectedAddress(address) {
    const id = address && format.identifier(address.id)
    if (!id) return
    this.selectedAddressId = id
    this.invalidateQuote()
  },
  chooseAddress() {
    if (this.data.submitting || this.createdPaymentId) return
    wx.navigateTo({
      url: '/pages/address/index?select=1',
      events: { addressSelected: (address) => this.acceptSelectedAddress(address) }
    })
  },
  async quoteFreight(address) {
    this.invalidateQuote()
    const generation = this.quoteGeneration
    const payload = this.orderPayload(address, false)
    feedback.update(this, { quoteLoading: true })
    try {
      const quote = await request({
        url: '/shop/orders/freight-quote', method: 'POST',
        data: payload
      })
      if (generation !== this.quoteGeneration) return
      if (!quote || ['productAmount', 'freightAmount', 'payAmount'].some((key) =>
        quote[key] === null || quote[key] === undefined || String(quote[key]).trim() === ''
        || !Number.isFinite(Number(quote[key])) || Number(quote[key]) < 0)) {
        throw new Error('结算金额返回异常，请重新计算')
      }
      const needSmsVerify = await this.checkPaymentVerify(quote.payAmount)
      if (generation !== this.quoteGeneration) return
      this.quotedPayload = JSON.stringify(payload)
      feedback.update(this, {
        total: format.money(quote.productAmount),
        freight: format.money(quote.freightAmount),
        payTotal: format.money(quote.payAmount),
        needSmsVerify, quoteReady: true, quoteError: ''
      })
    } catch (error) {
      if (generation !== this.quoteGeneration) return
      feedback.update(this, { quoteReady: false, freight: '--', payTotal: '--', quoteError: error.message || '结算金额计算失败，请重试' })
    } finally { if (generation === this.quoteGeneration) feedback.update(this, { quoteLoading: false }) }
  },
  retryQuote() {
    if (this.data.submitting || this.data.quoteLoading) return
    if (this.data.loadError) return this.load()
    if (this.data.address) return this.quoteFreight(this.data.address)
  },
  async checkPaymentVerify(amount) {
    const config = await request({ url: '/payment/checkVerify', params: { amount } })
    if (!config || typeof config.needVerify !== 'boolean') throw new Error('支付验证配置加载失败，请重新计算')
    return config.needVerify
  },
  smsCodeInput(event) {
    feedback.update(this, { smsCode: String(event.detail.value || '').replace(/\D/g, '').slice(0, 6) })
  },
  async sendPaymentSms() {
    if (this.data.smsSending || this.data.smsCooldown > 0) return
    feedback.update(this, { smsSending: true })
    try {
      await request({ url: '/sms/send/payment', method: 'POST' })
      feedback.update(this, { smsCooldown: 60 })
      clearInterval(this.smsTimer)
      this.smsTimer = setInterval(() => {
        const next = Math.max(0, this.data.smsCooldown - 1)
        feedback.update(this, { smsCooldown: next })
        if (!next) clearInterval(this.smsTimer)
      }, 1000)
      feedback.toast({ title: '验证码已发送', icon: 'success' })
    } catch (error) {
      feedback.toast({ title: error.message || '验证码发送失败', icon: 'none' })
    } finally { feedback.update(this, { smsSending: false }) }
  },
  onHide() {
    this.loadGeneration = (this.loadGeneration || 0) + 1
    this.invalidateQuote()
  },
  onUnload() { this.onHide(); clearInterval(this.smsTimer) },
  orderPayload(address, includeRemark = true) {
    return {
      addressId: format.identifier(address.id),
      payType: 'WECHAT',
      businessType: this.flashSaleMode ? 'FLASH_SALE' : 'NORMAL',
      ...(this.flashSaleMode ? { businessSourceId: this.activityId } : {}),
      remark: includeRemark && this.data.remark ? this.data.remark : undefined,
      ...(includeRemark ? { smsCode: this.data.needSmsVerify ? this.data.smsCode : undefined } : {}),
      items: this.data.rows.map((row) => ({ productId: row.productId, skuId: row.skuId || undefined, quantity: row.quantity }))
    }
  },
  remarkInput(event) { feedback.update(this, { remark: String(event.detail.value || '').slice(0, 500) }) },
  async submit() {
    if (this.data.submitting || this.createdPaymentId) return
    if (!auth.requireLogin(this.route || '/pages/checkout/index')) return
    if (!this.data.address) { feedback.toast({ title: '请先添加收货地址', icon: 'none' }); return }
    if (this.data.loading || this.data.quoteLoading || !this.data.quoteReady
        || this.quotedPayload !== JSON.stringify(this.orderPayload(this.data.address, false))) {
      feedback.toast({ title: this.data.quoteLoading ? '结算金额正在计算，请稍候' : '请先完成结算金额计算', icon: 'none' })
      return
    }
    if (!this.data.wechatPayEnabled) {
      wx.showModal({ title: '微信支付暂未开放', content: '当前客户尚未完成微信支付商户资料配置与真实联调，因此不会创建无法支付的新订单。', showCancel: false })
      return
    }
    if (this.data.needSmsVerify && !/^\d{6}$/.test(this.data.smsCode)) {
      feedback.toast({ title: '请输入6位支付验证码', icon: 'none' })
      return
    }
    feedback.update(this, { submitting: true })
    wx.showLoading({ title: '正在提交订单', mask: true })
    let paymentId = null
    try {
      const order = await request({
        url: this.flashSaleMode ? `/shop/flash-sales/${this.activityId}/orders` : '/shop/orders', method: 'POST', idempotencyKey: this.submitKey,
        data: this.orderPayload(this.data.address)
      })
      paymentId = format.identifier(order && (order.checkoutId || (order.order && order.order.id)))
      this.createdPaymentId = paymentId || 'CREATED_WITH_UNKNOWN_ID'
      if (!paymentId) throw new Error('订单已提交，但订单标识异常，请到“我的订单”核对状态后再操作')
      if (this.directMode) cart.clearDirectCheckout()
      else if (!this.flashSaleMode) cart.clearSelected()
      wx.hideLoading()
      const confirmed = await payment.payOrder(paymentId)
      await feedback.toast({ title: confirmed ? '支付成功' : '支付结果确认中', icon: confirmed ? 'success' : 'none' })
      wx.redirectTo({ url: '/pages/orders/index' })
    } catch (error) {
      wx.hideLoading()
      if (this.createdPaymentId) {
        wx.showModal({
          title: '订单已保留',
          content: !paymentId ? error.message : payment.isUserCancel(error)
            ? '你已取消微信支付，可在“我的订单 → 待支付”继续付款。'
            : `微信支付暂未完成：${error.message || '请稍后重试'}。订单已保留在待支付。`,
          showCancel: false,
          success: () => wx.redirectTo({ url: '/pages/orders/index' })
        })
      } else {
        feedback.toast({ title: error.message || '订单提交失败', icon: 'none', duration: 2600 })
      }
    } finally { feedback.update(this, { submitting: false }) }
  }
})
