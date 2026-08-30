const cart = require('../../utils/cart')
const format = require('../../utils/format')
const auth = require('../../utils/auth')

Page({
  data: { rows: [], total: '0.00', count: 0 },
  onShow() { this.refresh() },
  refresh() {
    const rows = cart.list().map((row) => ({
      ...row,
      priceText: format.money(row.salePrice),
      lineTotal: format.money(Number(row.salePrice) * row.quantity)
    }))
    const selected = rows.filter((row) => row.selected)
    this.setData({
      rows,
      count: selected.reduce((sum, row) => sum + row.quantity, 0),
      total: format.money(selected.reduce((sum, row) => sum + Number(row.salePrice) * row.quantity, 0))
    })
  },
  toggle(event) {
    cart.update(event.currentTarget.dataset.key, { selected: !event.currentTarget.dataset.selected })
    this.refresh()
  },
  quantity(event) {
    const key = event.currentTarget.dataset.key
    const row = this.data.rows.find((item) => item.key === key)
    cart.update(key, { quantity: Math.max(1, Math.min(99, row.quantity + Number(event.currentTarget.dataset.delta))) })
    this.refresh()
  },
  remove(event) { cart.remove(event.currentTarget.dataset.key); this.refresh() },
  openProduct(event) { wx.navigateTo({ url: `/pages/product/index?id=${event.currentTarget.dataset.id}` }) },
  checkout() {
    if (!this.data.count) { wx.showToast({ title: '请先选择商品', icon: 'none' }); return }
    if (!auth.requireLogin('/pages/cart/index')) return
    wx.showModal({
      title: '支付能力待客户配置',
      content: '商品、购物车和微信登录基座已就绪。微信支付必须使用客户自己的商户号完成 API v3 配置后才开放下单，当前不会创建无法支付的订单。',
      showCancel: false
    })
  },
  goShopping() { wx.switchTab({ url: '/pages/home/index' }) }
})
