const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const format = require('../../utils/format')
const orderCenter = require('../../utils/order-center')
const theme = require('../../utils/theme')
const { identifier, afterSaleEligibility } = require('./policy')

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
  data: { ...theme.pageData(), loading: true, error: '', rows: [], paymentNo: '', totalText: '0.00', actingId: null, cancellingAfterSaleId: null,
    editingSaleId: '', deliveryCompany: '', deliveryNo: '', shipmentError: '', submittingShipment: false,
    carriers: CARRIERS, trackingOrderId: '', trackingLoading: false, trackingError: '', trackingRows: [] },
  onLoad(options = {}) {
    theme.apply(this)
    const orderId = identifier(options.id)
    const paymentNo = orderCenter.normalizePaymentNo(options.orderNo || options.paymentNo)
    if (!orderId && !paymentNo) {
      feedback.update(this, { loading: false, error: '订单编号不正确' })
      return
    }
    this.orderId = orderId || null
    this.paymentNo = paymentNo
    this.redirect = this.orderId ? `/pages/order-detail/index?id=${this.orderId}` : orderCenter.detailPath(paymentNo)
    feedback.update(this, { paymentNo })
  },
  onShow() {
    theme.apply(this)
    if (this.redirect && auth.requireLogin(this.redirect)) return this.load()
    this.requestVersion = (this.requestVersion || 0) + 1
    if (this.redirect) feedback.update(this, { loading: false, rows: [] })
  },
  onUnload() { this.disposed = true; this.requestVersion = (this.requestVersion || 0) + 1 },
  onPullDownRefresh() {
    if (!this.orderId && !this.paymentNo) {
      wx.stopPullDownRefresh()
      return
    }
    this.load().finally(() => wx.stopPullDownRefresh())
  },
  async load() {
    const version = this.requestVersion = (this.requestVersion || 0) + 1
    feedback.update(this, { loading: true, error: '' })
    try {
      const result = this.orderId
        ? await request({ url: `/shop/orders/${this.orderId}` })
        : await request({ url: '/shop/orders/payment-detail', params: { paymentNo: this.paymentNo } })
      if (this.disposed || version !== this.requestVersion) return
      const source = Array.isArray(result) ? result : (result ? [result] : [])
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
            createTimeText: formatTime(order.createTime),
            payTimeText: formatTime(order.payTime),
            addressText: addressText(order)
          },
          items: (row.items || []).map((item) => ({
            ...item,
            productCover: format.mediaUrl(item.productCover),
            priceText: format.money(item.price)
          })),
          shipments: (row.shipments || []).map((shipment) => ({
            ...shipment,
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
            canReceiveExchange: Number(sale.applyType) === 3 && Number(sale.status) === 8
          }))
        }
      })
      this.loadedOnce = true
      feedback.update(this, {
        rows,
        paymentNo: this.paymentNo || (rows[0] && (rows[0].order.paymentOrderNo || rows[0].order.orderNo)) || '',
        totalText: format.money(rows.reduce((sum, row) => sum + Number(row.order.payAmount || 0), 0))
      })
    } catch (error) {
      if (!this.disposed && version === this.requestVersion) feedback.update(this, { error: error.message || '订单不存在或无权查看', rows: [] })
    } finally {
      if (!this.disposed && version === this.requestVersion) feedback.update(this, { loading: false })
    }
  },
  selectCarrier(event) {
    const company = CARRIERS[Number(event.detail.value)]
    if (company && !this.data.submittingShipment) feedback.update(this, { deliveryCompany: company, shipmentError: '' })
  },
  async loadTracking(event) {
    const id = identifier(event.currentTarget.dataset.id)
    if (!id || this.data.trackingLoading || !this.data.rows.some((row) => row.order.id === id)) return
    const version = this.requestVersion
    feedback.update(this, { trackingOrderId: id, trackingLoading: true, trackingError: '', trackingRows: [] })
    try {
      const records = await request({ url: `/shop/orders/${id}/tracking` })
      if (this.disposed || version !== this.requestVersion) return
      feedback.update(this, { trackingRows: (Array.isArray(records) ? records : []).map((record) => ({
        deliveryNo: String(record.deliveryNo || ''), deliveryCompany: record.deliveryCompany || '',
        statusText: record.statusText || (record.configured ? '暂无新物流轨迹' : '商城尚未配置物流轨迹服务，可复制单号向承运商查询'),
        events: (record.events || []).map((item) => ({ description: item.description || '', location: item.location || '', time: formatTime(item.eventTime) }))
      })) })
    } catch (error) { if (!this.disposed && version === this.requestVersion) feedback.update(this, { trackingError: error.message || '物流查询失败，请重试' }) }
    finally { if (!this.disposed) feedback.update(this, { trackingLoading: false }) }
  },
  copyDeliveryNo(event) {
    const number = String(event.currentTarget.dataset.number || '')
    if (number && number.length <= 64) wx.setClipboardData({ data: number })
  },
  receive(event) {
    const orderId = identifier(event.currentTarget.dataset.id)
    if (!orderId || this.data.actingId) return
    wx.showModal({
      title: '确认收到商品',
      content: '确认后订单将完成；如商品未收到或存在问题，请暂时不要确认。',
      confirmText: '确认收货',
      success: async ({ confirm }) => {
        if (!confirm || this.data.actingId) return
        feedback.update(this, { actingId: orderId })
        try {
          await request({ url: `/shop/orders/${orderId}/receive`, method: 'PUT' })
          feedback.toast({ title: '已确认收货', icon: 'success' })
          await this.load()
        } catch (error) {
          feedback.toast({ title: error.message || '确认失败', icon: 'none', duration: 2600 })
        } finally {
          feedback.update(this, { actingId: null })
        }
      }
    })
  },
  cancelOrder(event) {
    const orderId = identifier(event.currentTarget.dataset.id)
    if (!orderId || this.data.actingId) return
    wx.showModal({
      title: '取消订单',
      content: '取消后将释放库存，这笔订单无法恢复。',
      confirmText: '确认取消',
      confirmColor: this.data.themeColor,
      success: async ({ confirm }) => {
        if (!confirm || this.data.actingId) return
        feedback.update(this, { actingId: orderId })
        try {
          await request({ url: `/shop/orders/${orderId}/cancel`, method: 'PUT' })
          feedback.toast({ title: '订单已取消', icon: 'success' })
          await this.load()
        } catch (error) { feedback.toast({ title: error.message || '取消失败', icon: 'none' }) }
        finally { feedback.update(this, { actingId: null }) }
      }
    })
  },
  cancelAfterSale(event) {
    const id = identifier(event.currentTarget.dataset.id)
    if (!id || this.data.cancellingAfterSaleId) return
    wx.showModal({
      title: '取消售后申请',
      content: '取消后不会产生退款；如仍在售后期限内，可以重新申请。',
      confirmText: '确认取消',
      confirmColor: this.data.themeColor,
      success: async ({ confirm }) => {
        if (!confirm || this.data.cancellingAfterSaleId) return
        feedback.update(this, { cancellingAfterSaleId: id })
        try {
          await request({ url: `/shop/after-sales/${id}/cancel`, method: 'PUT' })
          feedback.toast({ title: '售后申请已取消', icon: 'success' })
          await this.load()
        } catch (error) { feedback.toast({ title: error.message || '取消失败', icon: 'none' }) }
        finally { feedback.update(this, { cancellingAfterSaleId: null }) }
      }
    })
  },
  applyAfterSale(event) {
    const id = identifier(event.currentTarget.dataset.id)
    const row = this.data.rows.find((item) => item.order.id === id)
    if (row && row.canApplyAfterSale) wx.navigateTo({ url: `/pages/after-sale/index?orderId=${id}` })
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
    if (!sale || !sale.canReturn || this.data.submittingShipment) return
    const deliveryCompany = this.data.deliveryCompany.trim()
    const deliveryNo = this.data.deliveryNo.trim()
    if (!deliveryCompany || deliveryCompany.length > 50) { feedback.update(this, { shipmentError: '请填写1至50字的快递公司名称' }); return }
    if (!/^[A-Za-z0-9_-]{4,64}$/.test(deliveryNo)) { feedback.update(this, { shipmentError: '快递单号需为4至64位字母、数字、下划线或短横线' }); return }
    feedback.update(this, { submittingShipment: true, shipmentError: '' })
    try {
      await request({ url: `/shop/after-sales/${id}/return-shipment`, method: 'PUT', data: { deliveryCompany, deliveryNo } })
      feedback.update(this, { editingSaleId: '', deliveryCompany: '', deliveryNo: '' })
      feedback.toast({ title: '退货物流已提交', icon: 'success' })
      await this.load()
    } catch (error) { feedback.update(this, { shipmentError: error.message || '物流提交失败，请重试' }) }
    finally { feedback.update(this, { submittingShipment: false }) }
  },
  receiveExchange(event) {
    const id = identifier(event.currentTarget.dataset.id)
    const sale = this.findSale(id)
    if (!sale || !sale.canReceiveExchange || this.data.cancellingAfterSaleId) return
    wx.showModal({ title: '确认收到换货商品', content: '请确认换货商品已收到且无误，确认后本次换货将完成。', confirmText: '确认收货',
      success: async ({ confirm }) => {
        if (!confirm || this.data.cancellingAfterSaleId) return
        feedback.update(this, { cancellingAfterSaleId: id })
        try {
          await request({ url: `/shop/after-sales/${id}/exchange-received`, method: 'PUT' })
          feedback.toast({ title: '已确认换货收货', icon: 'success' })
          await this.load()
        } catch (error) { feedback.toast({ title: error.message || '确认失败', icon: 'none' }) }
        finally { feedback.update(this, { cancellingAfterSaleId: null }) }
      }
    })
  },
  openOrders() {
    wx.navigateTo({ url: '/pages/orders/index' })
  }
})
