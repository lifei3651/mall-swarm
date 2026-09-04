const request = require('../../utils/request')
const auth = require('../../utils/auth')
const format = require('../../utils/format')
const orderCenter = require('../../utils/order-center')

const STATUS = { 0: '待付款', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已关闭', 5: '售后中' }
const AFTER_SALE_STATUS = { 0: '待审核', 1: '退款完成', 2: '已拒绝', 3: '已取消', 4: '待寄回', 5: '待商家收货', 6: '退款处理中', 7: '待商家换货发出', 8: '换货已发出' }
const AFTER_SALE_TYPE = { 1: '仅退款', 2: '退货退款', 3: '同规格换货' }

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function addressText(order) {
  return [order.receiverProvince, order.receiverCity, order.receiverDistrict, order.receiverDetailAddress]
    .filter(Boolean).join('') || order.receiverAddress || ''
}

Page({
  data: { loading: true, error: '', rows: [], paymentNo: '', totalText: '0.00', actingId: null, cancellingAfterSaleId: null },
  onLoad(options) {
    const orderId = Number(options.id)
    const paymentNo = orderCenter.normalizePaymentNo(options.orderNo || options.paymentNo)
    if (!orderId && !paymentNo) {
      this.setData({ loading: false, error: '订单编号不正确' })
      return
    }
    this.orderId = orderId || null
    this.paymentNo = paymentNo
    this.redirect = this.orderId ? `/pages/order-detail/index?id=${this.orderId}` : orderCenter.detailPath(paymentNo)
    this.setData({ paymentNo })
    if (auth.requireLogin(this.redirect)) this.load()
  },
  onShow() {
    if (this.loadedOnce && auth.requireLogin(this.redirect)) this.load()
  },
  onPullDownRefresh() {
    if (!this.paymentNo) {
      wx.stopPullDownRefresh()
      return
    }
    this.load().finally(() => wx.stopPullDownRefresh())
  },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const result = this.orderId
        ? await request({ url: `/shop/orders/${this.orderId}` })
        : await request({ url: '/shop/orders/payment-detail', params: { paymentNo: this.paymentNo } })
      const source = Array.isArray(result) ? result : (result ? [result] : [])
      const rows = source.map((row) => {
        const order = row.order || {}
        return {
          ...row,
          key: order.id,
          order: {
            ...order,
            statusText: STATUS[Number(order.status)] || '处理中',
            amountText: format.money(order.payAmount || order.totalAmount),
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
            statusText: AFTER_SALE_STATUS[Number(sale.status)] || '处理中',
            typeText: AFTER_SALE_TYPE[Number(sale.applyType)] || '售后申请',
            amountText: format.money(sale.refundAmount),
            createTimeText: formatTime(sale.createTime),
            cancellable: [0, 4].includes(Number(sale.status))
          }))
        }
      })
      this.loadedOnce = true
      this.setData({
        rows,
        paymentNo: this.paymentNo || (rows[0] && (rows[0].order.paymentOrderNo || rows[0].order.orderNo)) || '',
        totalText: format.money(rows.reduce((sum, row) => sum + Number(row.order.payAmount || 0), 0))
      })
    } catch (error) {
      this.setData({ error: error.message || '订单不存在或无权查看', rows: [] })
    } finally {
      this.setData({ loading: false })
    }
  },
  receive(event) {
    const orderId = Number(event.currentTarget.dataset.id)
    if (!orderId || this.data.actingId) return
    wx.showModal({
      title: '确认收到商品',
      content: '确认后订单将完成；如商品未收到或存在问题，请暂时不要确认。',
      confirmText: '确认收货',
      success: async ({ confirm }) => {
        if (!confirm) return
        this.setData({ actingId: orderId })
        try {
          await request({ url: `/shop/orders/${orderId}/receive`, method: 'PUT' })
          wx.showToast({ title: '已确认收货', icon: 'success' })
          await this.load()
        } catch (error) {
          wx.showToast({ title: error.message || '确认失败', icon: 'none', duration: 2600 })
        } finally {
          this.setData({ actingId: null })
        }
      }
    })
  },
  cancelOrder(event) {
    const orderId = Number(event.currentTarget.dataset.id)
    if (!orderId || this.data.actingId) return
    wx.showModal({
      title: '取消订单',
      content: '取消后将释放库存，这笔订单无法恢复。',
      confirmText: '确认取消',
      confirmColor: '#e7193f',
      success: async ({ confirm }) => {
        if (!confirm) return
        this.setData({ actingId: orderId })
        try {
          await request({ url: `/shop/orders/${orderId}/cancel`, method: 'PUT' })
          wx.showToast({ title: '订单已取消', icon: 'success' })
          await this.load()
        } catch (error) { wx.showToast({ title: error.message || '取消失败', icon: 'none' }) }
        finally { this.setData({ actingId: null }) }
      }
    })
  },
  cancelAfterSale(event) {
    const id = Number(event.currentTarget.dataset.id)
    if (!id || this.data.cancellingAfterSaleId) return
    wx.showModal({
      title: '取消售后申请',
      content: '取消后不会产生退款；如仍在售后期限内，可以重新申请。',
      confirmText: '确认取消',
      confirmColor: '#e7193f',
      success: async ({ confirm }) => {
        if (!confirm) return
        this.setData({ cancellingAfterSaleId: id })
        try {
          await request({ url: `/shop/after-sales/${id}/cancel`, method: 'PUT' })
          wx.showToast({ title: '售后申请已取消', icon: 'success' })
          await this.load()
        } catch (error) { wx.showToast({ title: error.message || '取消失败', icon: 'none' }) }
        finally { this.setData({ cancellingAfterSaleId: null }) }
      }
    })
  },
  openOrders() {
    wx.navigateTo({ url: '/pages/orders/index' })
  }
})
