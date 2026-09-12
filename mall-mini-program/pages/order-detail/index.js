const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const session = require('../../utils/session')
const payment = require('../../utils/payment')
const balancePayment = require('../../utils/balance-order')
const format = require('../../utils/format')
const orderCenter = require('../../utils/order-center')
const theme = require('../../utils/theme')
const foreground = require('../../utils/foreground-refresh')
const { identifier, afterSaleEligibility, amountLabel, paymentSummary } = require('./policy')

const STATUS = { 0: '待付款', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已关闭', 5: '售后中' }
const AFTER_SALE_STATUS = { 0: '待审核', 1: '退款完成', 2: '已拒绝', 3: '已取消', 4: '待寄回', 5: '待商家收货', 6: '退款处理中', 7: '待商家换货发出', 8: '换货已发出' }
const AFTER_SALE_TYPE = { 1: '仅退款', 2: '退货退款', 3: '同规格换货' }
const CARRIERS = ['顺丰速运', '京东物流', '中通快递', '圆通速递', '申通快递', '韵达快递', '极兔速递', '中国邮政', 'EMS', '德邦快递', '跨越速运', '安能物流', '壹米滴答', 'DHL', 'FedEx', 'UPS']

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function addressText(order) {
  return [order.receiverProvince, order.receiverCity, order.receiverDistrict, order.receiverDetailAddress]
    .filter(Boolean).join('') || order.receiverAddress || ''
}

Page({
  ...balancePayment.methods,
  data: { ...theme.pageData(), ...paymentSummary(), loading: true, error: '', rows: [], paymentNo: '', actingId: null, paying: false, cancellingAfterSaleId: null,
    editingSaleId: '', deliveryCompany: '', deliveryNo: '', shipmentError: '', submittingShipment: false,
    carriers: CARRIERS, expandedOrders: {}, trackingOrderId: '', trackingLoading: false, trackingError: '', trackingRows: [], ...balancePayment.data },
  onLoad(options = {}) {
    theme.apply(this)
    const orderId = identifier(options.id)
    const paymentNo = orderCenter.normalizePaymentNo(options.orderNo || options.paymentNo)
    if (!orderId && !paymentNo) {
      feedback.update(this, { loading: false, error: '订单编号不正确' })
      return
    }
    this.orderId = orderId || null
    this.autoPay = options.autoPay === '1'
    this.paymentNo = paymentNo
    this.redirect = this.orderId ? `/pages/order-detail/index?id=${this.orderId}` : orderCenter.detailPath(paymentNo)
    feedback.update(this, { paymentNo })
  },
  onShow() {
    this.hidden = false
    foreground.start(this, () => this.load(true), () => !this.redirect || this.data.loading || this.data.paying || this.data.actingId || this.data.balanceDialog || this.data.balanceBusy || this.data.balanceLoading || this.data.editingSaleId || this.data.submittingShipment || this.data.cancellingAfterSaleId || this.data.trackingLoading)
    theme.apply(this)
    if (this.data.paying) return
    if (this.redirect && auth.requireLogin(this.redirect)) return this.load().then(() => { if (this.autoPay && this.data.paymentChannel === 'BALANCE') { this.autoPay = false; return this.openBalancePayment() } })
    this.requestVersion = (this.requestVersion || 0) + 1
    if (this.redirect) feedback.update(this, { loading: false, rows: [], ...paymentSummary() })
  },
  onHide() { this.hidden = true; foreground.stop(this); this.requestVersion = (this.requestVersion || 0) + 1; this.setData({ balancePassword: '', balanceDialog: false, ...(this.data.actingId === 'balance' && !this.data.balanceBusy ? { actingId: null } : {}) }) },
  onUnload() { this.onHide(); this.disposed = true; this.requestVersion = (this.requestVersion || 0) + 1 },
  onPullDownRefresh() {
    if (this.data.paying || (!this.orderId && !this.paymentNo)) {
      wx.stopPullDownRefresh()
      return
    }
    this.load().finally(() => wx.stopPullDownRefresh())
  },
  async load(quiet = false) {
    quiet = quiet === true
    if (quiet && (this.hidden || this.data.loading || this.data.paying || this.data.actingId || this.data.balanceDialog || this.data.balanceBusy || this.data.editingSaleId || this.data.submittingShipment)) return false
    const version = this.requestVersion = (this.requestVersion || 0) + 1
    const token = session.getToken()
    const current = () => !this.disposed && !this.hidden && version === this.requestVersion && token === session.getToken()
      && !(quiet && (this.data.paying || this.data.actingId || this.data.balanceDialog || this.data.balanceBusy || this.data.editingSaleId || this.data.submittingShipment))
    if (!quiet) feedback.update(this, { loading: true, error: '' })
    try {
      const result = this.orderId
        ? await request({ url: `/shop/orders/${this.orderId}` })
        : await request({ url: '/shop/orders/payment-detail', params: { paymentNo: this.paymentNo } })
      if (!current()) return false
      let source = Array.isArray(result) ? result : (result ? [result] : [])
      const first = source[0] && source[0].order
      if (this.orderId && first && first.tradeId && Number(first.status) === 0) {
        const groupNo = orderCenter.normalizePaymentNo(first.paymentOrderNo || first.tradeNo)
        if (!groupNo) throw new Error('合并订单信息不完整，请从全部订单重新进入')
        source = await request({ url: '/shop/orders/payment-detail', params: { paymentNo: groupNo } })
        if (!current()) return false
        if (!Array.isArray(source) || !source.length || !source.some((row) => identifier(row.order && row.order.id) === this.orderId)
          || source.some((row) => !row.order || String(row.order.tradeId) !== String(first.tradeId))) {
          throw new Error('合并订单信息发生变化，请刷新后重试')
        }
      }
      const rows = source.map((row) => {
        const order = row.order || {}
        return {
          ...row,
          key: identifier(order.id),
          canApplyAfterSale: afterSaleEligibility(row).allowed,
          canReceive: Number(order.status) === 2 && !(row.afterSales || []).some((sale) => [0, 4, 5, 6, 7, 8].includes(Number(sale.status))),
          order: {
            ...order,
            id: identifier(order.id),
            status: Number(order.status),
            statusText: STATUS[Number(order.status)] || '处理中',
            amountText: format.money(order.payAmount == null ? order.totalAmount : order.payAmount),
            amountLabel: amountLabel(order),
            freightText: format.money(order.freightAmount),
            createTimeText: formatTime(order.createTime),
            payTimeText: formatTime(order.payTime),
            addressText: addressText(order)
          },
          items: (row.items || []).map((item) => ({
            ...item,
            productCover: format.mediaUrl(item.productCover),
            priceText: format.money(item.price)
          })),
          shipments: (row.shipments?.length ? row.shipments : order.deliveryNo ? [{ deliveryCompany: order.deliveryCompany, deliveryNo: order.deliveryNo,
            deliveryTime: order.deliveryTime, shipmentQuantity: (row.items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0) }] : []).map((shipment, index) => ({
            ...shipment,
            key: `${shipment.id || shipment.deliveryNo || 'package'}:${index}`,
            packageLabel: `包裹 ${index + 1}${shipment.shipmentQuantity ? ' · ' + shipment.shipmentQuantity + '件商品' : ''}`,
            deliveryTimeText: formatTime(shipment.deliveryTime)
          })),
          afterSales: (row.afterSales || []).map((sale) => ({
            ...sale,
            id: identifier(sale.id),
            status: Number(sale.status),
            applyType: Number(sale.applyType),
            statusText: Number(sale.applyType) === 3 && Number(sale.status) === 1 ? '换货完成' : AFTER_SALE_STATUS[Number(sale.status)] || '处理中',
            typeText: AFTER_SALE_TYPE[Number(sale.applyType)] || '售后申请',
            amountText: format.money(sale.refundAmount),
            createTimeText: formatTime(sale.createTime),
            cancellable: [0, 4].includes(Number(sale.status)),
            canReturn: [2, 3].includes(Number(sale.applyType)) && [4, 5].includes(Number(sale.status)),
            canReceiveExchange: Number(sale.applyType) === 3 && Number(sale.status) === 8,
            canReapply: Number(sale.status) === 2 && afterSaleEligibility(row).allowed,
            orderId: identifier(order.id)
          }))
        }
      })
      this.loadedOnce = true
      feedback.update(this, {
        rows,
        error: '',
        paymentNo: this.paymentNo || (rows[0] && (rows[0].order.paymentOrderNo || rows[0].order.orderNo)) || '',
        ...paymentSummary(rows)
      })
      this.quietErrorShown = false
      return true
    } catch (error) {
      if (current()) {
        if (!quiet) feedback.update(this, { error: error.message || '订单不存在或无权查看', rows: [], ...paymentSummary() })
        else if (!this.quietErrorShown) { this.quietErrorShown = true; feedback.notice('订单暂未刷新，当前显示上次记录。请下拉刷新核对最新进度。', '更新未完成') }
      }
      return false
    } finally {
      if (!this.disposed && version === this.requestVersion) {
        feedback.update(this, { loading: false, ...(token !== session.getToken() ? { rows: [], ...paymentSummary() } : {}) })
      }
    }
  },
  async pay() {
    if (this.data.paymentChannel === 'BALANCE') return this.openBalancePayment()
    if (this.data.paying || this.data.actingId || this.data.loading || !this.data.payOrderId) return
    if (!auth.requireLogin(this.redirect)) return
    const token = session.getToken()
    const current = () => !this.disposed && token === session.getToken()
    const previous = `${this.data.payOrderId}:${this.data.totalText}:${this.data.rows.map((row) => row.order.id).join(',')}`
    feedback.update(this, { paying: true, actingId: 'payment' })
    wx.showLoading({ title: '正在核对订单', mask: true })
    try {
      const config = await request({ url: '/shop/pay/config' })
      if (!current()) return
      if (!config || config.wechatPayEnabled !== true) {
        feedback.notice('商城暂未开放微信支付，请稍后重试或联系商城客服。', '暂不能支付')
        return
      }
      if (!await this.load() || !current()) return
      const next = `${this.data.payOrderId}:${this.data.totalText}:${this.data.rows.map((row) => row.order.id).join(',')}`
      if (!this.data.payOrderId || previous !== next) {
        feedback.notice('订单状态或付款金额已更新，请核对页面后再操作。', '订单已更新')
        return
      }
      let result, failure
      try { result = await payment.payOrder(this.data.payOrderId, current) } catch (error) { failure = error }
      if (!current()) return
      wx.hideLoading()
      const refreshed = await this.load()
      if (!current()) return
      if (refreshed && this.data.rows.length && this.data.rows.every((row) => [1, 2, 3, 5].includes(row.order.status))) {
        feedback.success('支付已确认，可在订单中查看发货进度')
      } else if (failure && payment.isUserCancel(failure)) {
        feedback.success('已取消本次支付，请在订单中核对状态')
      } else if (failure) {
        feedback.notice(`${failure.message || '微信支付未完成，请检查网络后重试'}。如已扣款，请先刷新订单确认结果，勿重复付款。`, '支付未完成')
      } else {
        feedback.notice(result && refreshed && this.data.rows.some((row) => row.order.status === 4)
          ? '付款结果已返回，但订单已关闭。请查看退款进度或联系商城客服，勿重复付款。'
          : '支付结果还在确认中，请稍后下拉刷新订单；如已扣款，勿重复付款。', '请核对订单状态')
      }
    } catch (error) {
      if (current()) feedback.notice(error.message || '暂时无法核对支付信息，请检查网络后重试', '支付未发起')
    } finally {
      wx.hideLoading()
      if (!this.disposed) feedback.update(this, { paying: false, actingId: null,
        ...(!current() ? { rows: [], ...paymentSummary() } : {}) })
    }
  },
  selectCarrier(event) {
    const company = CARRIERS[Number(event.detail.value)]
    if (company && !this.data.submittingShipment) feedback.update(this, { deliveryCompany: company, shipmentError: '' })
  },
  async loadTracking(event) {
    const id = identifier(event.currentTarget.dataset.id)
    const current = this.operationCurrent()
    if (!current() || !id || this.data.trackingLoading || !this.data.rows.some((row) => row.order.id === id)) return
    const version = this.requestVersion
    feedback.update(this, { trackingOrderId: id, trackingLoading: true, trackingError: '', trackingRows: [] })
    try {
      const records = await request({ url: `/shop/orders/${id}/tracking` })
      if (!current() || version !== this.requestVersion) return
      feedback.update(this, { trackingRows: (Array.isArray(records) ? records : []).map((record) => ({
        deliveryNo: String(record.deliveryNo || ''), deliveryCompany: record.deliveryCompany || '',
        statusText: record.statusText || (record.configured ? '暂无新物流轨迹' : '商城尚未配置物流轨迹服务，可复制单号向承运商查询'),
        events: (record.events || []).map((item) => ({ description: item.description || '', location: item.location || '', time: formatTime(item.eventTime) }))
      })) })
    } catch (error) { if (current() && version === this.requestVersion) feedback.update(this, { trackingError: error.message || '物流查询失败，请重试' }) }
    finally { if (!this.disposed) feedback.update(this, { trackingLoading: false }) }
  },
  copyDeliveryNo(event) {
    const number = String(event.currentTarget.dataset.number || '')
    if (number && number.length <= 64) wx.setClipboardData({ data: number })
  },
  operationCurrent() { const token = session.getToken(); return () => !!token && token === session.getToken() && !this.disposed && !this.hidden },
  receive(event) {
    const orderId = identifier(event.currentTarget.dataset.id)
    const current = this.operationCurrent()
    if (!current() || !orderId || this.data.actingId) return
    wx.showModal({
      title: '确认收到商品',
      content: '确认后订单将完成；如商品未收到或存在问题，请暂时不要确认。',
      confirmText: '确认收货',
      success: async ({ confirm }) => {
        if (!confirm || !current() || this.data.actingId) return
        feedback.update(this, { actingId: orderId })
        try {
          await request({ url: `/shop/orders/${orderId}/receive`, method: 'PUT' })
          if (!current()) return
          feedback.toast({ title: '已确认收货', icon: 'success' })
          await this.load()
        } catch (error) {
          if (current()) feedback.toast({ title: error.message || '确认失败', icon: 'none', duration: 2600 })
        } finally {
          feedback.update(this, { actingId: null })
        }
      }
    })
  },
  copyOrderNo(event) { const id = identifier(event.currentTarget.dataset.id), row = this.data.rows.find(item => item.order.id === id); if (row?.order?.orderNo) wx.setClipboardData({ data: String(row.order.orderNo) }) },
  review(event) {
    const row = this.data.rows.find(item => item.order.id === identifier(event.currentTarget.dataset.id))
    const productId = row && identifier(row.pendingReviewProductId), orderItemId = row && identifier(row.pendingReviewOrderItemId)
    if (productId && orderItemId) wx.navigateTo({ url: `/pages/order-review/index?id=${productId}&orderItemId=${orderItemId}` })
    else feedback.notice('评价入口已变化，请刷新订单后重试')
  },
  support(event) {
    const id = identifier(event.currentTarget.dataset.id)
    if (this.data.rows.some(item => item.order.id === id)) wx.navigateTo({ url: `/pages/support/index?create=1&orderId=${id}` })
  },
  cancelOrder(event) {
    const orderId = identifier(event.currentTarget.dataset.id)
    const current = this.operationCurrent()
    if (!current() || !orderId || this.data.actingId) return
    wx.showModal({
      title: '取消订单',
      content: this.data.rows.some((row) => row.order.id === orderId && row.order.tradeId)
        ? '这是合并支付订单，取消将同时关闭该交易下所有待付款子订单并释放库存，无法恢复。'
        : '取消后将释放库存，这笔订单无法恢复。',
      confirmText: '确认取消',
      confirmColor: this.data.themeColor,
      success: async ({ confirm }) => {
        if (!confirm || !current() || this.data.actingId) return
        feedback.update(this, { actingId: orderId })
        try {
          await request({ url: `/shop/orders/${orderId}/cancel`, method: 'PUT' })
          if (!current()) return
          feedback.toast({ title: '订单已取消', icon: 'success' })
          await this.load()
        } catch (error) { if (current()) feedback.toast({ title: error.message || '取消失败', icon: 'none' }) }
        finally { feedback.update(this, { actingId: null }) }
      }
    })
  },
  cancelAfterSale(event) {
    const id = identifier(event.currentTarget.dataset.id)
    const current = this.operationCurrent()
    if (!current() || !id || this.data.cancellingAfterSaleId) return
    wx.showModal({
      title: '取消售后申请',
      content: '取消后不会产生退款；如仍在售后期限内，可以重新申请。',
      confirmText: '确认取消',
      confirmColor: this.data.themeColor,
      success: async ({ confirm }) => {
        if (!confirm || !current() || this.data.cancellingAfterSaleId) return
        feedback.update(this, { cancellingAfterSaleId: id })
        try {
          await request({ url: `/shop/after-sales/${id}/cancel`, method: 'PUT' })
          if (!current()) return
          feedback.toast({ title: '售后申请已取消', icon: 'success' })
          await this.load()
        } catch (error) { if (current()) feedback.toast({ title: error.message || '取消失败', icon: 'none' }) }
        finally { feedback.update(this, { cancellingAfterSaleId: null }) }
      }
    })
  },
  applyAfterSale(event) {
    if (!this.operationCurrent()() || this.data.paying || this.data.actingId || this.data.submittingShipment) return
    const id = identifier(event.currentTarget.dataset.id)
    const row = this.data.rows.find((item) => item.order.id === id)
    if (row && row.canApplyAfterSale) wx.navigateTo({ url: `/pages/after-sale/index?orderId=${id}` })
  },
  toggleOrderInfo(event) {
    const id = identifier(event.currentTarget.dataset.id)
    if (id && this.data.rows.some(row => row.order.id === id)) this.setData({ [`expandedOrders.${id}`]: !this.data.expandedOrders[id] })
  },
  findSale(id) {
    for (const row of this.data.rows) {
      const sale = row.afterSales.find((item) => item.id === id)
      if (sale) return sale
    }
    return null
  },
  editShipment(event) {
    const id = identifier(event.currentTarget.dataset.id)
    const sale = this.findSale(id)
    if (!sale || !sale.canReturn || this.data.submittingShipment) return
    feedback.update(this, { editingSaleId: id, deliveryCompany: sale.returnDeliveryCompany || '', deliveryNo: sale.returnDeliveryNo || '', shipmentError: '' })
  },
  shipmentInput(event) {
    const field = event.currentTarget.dataset.field
    if (!this.data.submittingShipment && ['deliveryCompany', 'deliveryNo'].includes(field)) feedback.update(this, { [field]: event.detail.value, shipmentError: '' })
  },
  closeShipment() { if (!this.data.submittingShipment) feedback.update(this, { editingSaleId: '', shipmentError: '' }) },
  async submitShipment() {
    const id = this.data.editingSaleId
    const sale = this.findSale(id)
    const current = this.operationCurrent()
    if (!current() || !sale || !sale.canReturn || this.data.submittingShipment) return
    const deliveryCompany = this.data.deliveryCompany.trim()
    const deliveryNo = this.data.deliveryNo.trim()
    if (!deliveryCompany || deliveryCompany.length > 50) { feedback.update(this, { shipmentError: '请填写1至50字的快递公司名称' }); return }
    if (!/^[A-Za-z0-9_-]{4,64}$/.test(deliveryNo)) { feedback.update(this, { shipmentError: '快递单号需为4至64位字母、数字、下划线或短横线' }); return }
    feedback.update(this, { submittingShipment: true, shipmentError: '' })
    try {
      await request({ url: `/shop/after-sales/${id}/return-shipment`, method: 'PUT', data: { deliveryCompany, deliveryNo } })
      if (!current()) return
      feedback.update(this, { editingSaleId: '', deliveryCompany: '', deliveryNo: '' })
      feedback.toast({ title: '退货物流已提交', icon: 'success' })
      await this.load()
    } catch (error) { if (current()) feedback.update(this, { shipmentError: error.message || '物流提交失败，请重试' }) }
    finally { feedback.update(this, { submittingShipment: false }) }
  },
  receiveExchange(event) {
    const id = identifier(event.currentTarget.dataset.id)
    const sale = this.findSale(id)
    const current = this.operationCurrent()
    if (!current() || !sale || !sale.canReceiveExchange || this.data.cancellingAfterSaleId) return
    wx.showModal({ title: '确认收到换货商品', content: '请确认换货商品已收到且无误，确认后本次换货将完成。', confirmText: '确认收货',
      success: async ({ confirm }) => {
        if (!confirm || !current() || this.data.cancellingAfterSaleId) return
        feedback.update(this, { cancellingAfterSaleId: id })
        try {
          await request({ url: `/shop/after-sales/${id}/exchange-received`, method: 'PUT' })
          if (!current()) return
          feedback.toast({ title: '已确认换货收货', icon: 'success' })
          await this.load()
        } catch (error) { if (current()) feedback.toast({ title: error.message || '确认失败', icon: 'none' }) }
        finally { feedback.update(this, { cancellingAfterSaleId: null }) }
      }
    })
  },
  openOrders() {
    wx.navigateTo({ url: '/pages/orders/index' })
  }
})
