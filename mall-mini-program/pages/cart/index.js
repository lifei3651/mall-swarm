const cart = require('../../utils/cart')
const format = require('../../utils/format')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')

Page({
  data: { ...theme.pageData(), rows: [], total: '0.00', count: 0, allSelected: false },
  onShow() { theme.apply(this); this.refresh() },
  refresh() {
    const rows = cart.list().map((row) => ({
      ...row,
      coverUrl: format.mediaUrl(row.coverUrl),
      priceText: format.money(row.salePrice),
      lineTotal: format.money(Number(row.salePrice) * row.quantity)
    }))
    const selected = rows.filter((row) => row.selected)
    this.setData({
      rows,
      count: selected.reduce((sum, row) => sum + row.quantity, 0),
      total: format.money(selected.reduce((sum, row) => sum + Number(row.salePrice) * row.quantity, 0)),
      allSelected: rows.length > 0 && selected.length === rows.length
    })
  },
  toggle(event) {
    cart.update(event.currentTarget.dataset.key, { selected: (event.detail.value || []).includes('selected') })
    this.refresh()
  },
  quantity(event) {
    const key = event.currentTarget.dataset.key
    const row = this.data.rows.find((item) => item.key === key)
    cart.update(key, { quantity: Math.max(1, Math.min(99, row.quantity + Number(event.currentTarget.dataset.delta))) })
    this.refresh()
  },
  toggleAll(event) { cart.selectAll((event.detail.value || []).includes('selected')); this.refresh() },
  remove(event) {
    const key = event.currentTarget.dataset.key
    wx.showModal({
      title: '移除商品',
      content: '确定把这件商品移出购物车吗？',
      confirmText: '移除',
      confirmColor: this.data.themeColor,
      success: ({ confirm }) => {
        if (!confirm) return
        cart.remove(key)
        this.refresh()
      }
    })
  },
  openProduct(event) { wx.navigateTo({ url: `/pages/product/index?id=${event.currentTarget.dataset.id}` }) },
  checkout() {
    if (!this.data.count) { wx.showToast({ title: '请先选择商品', icon: 'none' }); return }
    if (!auth.requireLogin('/pages/cart/index')) return
    wx.navigateTo({ url: '/pages/checkout/index' })
  },
  goShopping() { wx.switchTab({ url: '/pages/home/index' }) }
})
