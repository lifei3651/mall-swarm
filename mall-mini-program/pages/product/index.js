const request = require('../../utils/request')
const cart = require('../../utils/cart')
const format = require('../../utils/format')

Page({
  data: { loading: true, error: '', product: {}, skus: [], skuIndex: 0, quantity: 1 },
  onLoad(options) { this.productId = Number(options.id); this.load() },
  async load() {
    try {
      const detail = await request({ url: `/shop/products/${this.productId}` })
      this.setData({ product: format.product(detail.product), skus: detail.skus || [] })
      wx.setNavigationBarTitle({ title: detail.product.productName || '商品详情' })
    } catch (error) { this.setData({ error: error.message }) }
    finally { this.setData({ loading: false }) }
  },
  selectSku(event) { this.setData({ skuIndex: Number(event.currentTarget.dataset.index) }) },
  changeQuantity(event) { this.setData({ quantity: Math.max(1, Math.min(99, this.data.quantity + Number(event.currentTarget.dataset.delta))) }) },
  addToCart() {
    const product = this.data.product
    const sku = this.data.skus[this.data.skuIndex]
    cart.add({ productId: product.id, skuId: sku ? sku.id : null, productName: product.productName,
      coverUrl: product.coverUrl, salePrice: sku ? Number(sku.salePrice || product.salePrice) : product.salePrice,
      skuName: sku ? (sku.skuName || sku.specName || '') : '', quantity: this.data.quantity })
    wx.showToast({ title: '已加入购物车', icon: 'success' })
  },
  goCart() { wx.switchTab({ url: '/pages/cart/index' }) }
})
