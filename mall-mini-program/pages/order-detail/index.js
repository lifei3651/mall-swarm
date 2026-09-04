const request = require('../../utils/request')
const auth = require('../../utils/auth')
const format = require('../../utils/format')
const orderCenter = require('../../utils/order-center')

const STATUS = { 0: '待付款', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已关闭', 5: '售后中' }

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function addressText(order) {
  return [order.receiverProvince, order.receiverCity, order.receiverDistrict, order.receiverDetailAddress]
    .filter(Boolean).join('') || order.receiverAddress || ''
}

Page({
  data: { loading: true, error: '', rows: [], paymentNo: '', totalText: '0.00', actingId: null },
  onLoad(options) {
    const paymentNo = orderCenter.normalizePaymentNo(options.orderNo || options.paymentNo)
    if (!paymentNo) {
      this.setData({ loading: false, error: '订单编号不正确' })
      return
    }
    this.paymentNo = paymentNo
    this.redirect = orderCenter.detailPath(paymentNo)
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
      const result = await request({
        url: '/shop/orders/payment-detail',
        params: { paymentNo: this.paymentNo }
      })
      const rows = (result || []).map((row) => {
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
          }))
        }
      })
      this.loadedOnce = true
      this.setData({
        rows,
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
  openOrders() {
    wx.navigateTo({ url: '/pages/orders/index' })
  }
})
