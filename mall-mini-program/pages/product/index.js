const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const cart = require('../../utils/cart')
const format = require('../../utils/format')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const share = require('../../utils/share')

Page({
  data: {
    ...theme.pageData(),
    loading: true, error: '', product: {}, skus: [], skuIndex: 0, quantity: 1,
    priceText: '0.00', stock: 0, soldOut: false, selectedSku: {}
  },
  onLoad(options = {}) { theme.apply(this); this.productId = format.identifier(options.id); this.load() },
  onShow() { return share.prepare(this) },
  onHide() { share.hide(this) },
  onUnload() { share.hide(this) },
  onShareAppMessage() { return share.message(this, this.productId ? `/pages/product/index?id=${encodeURIComponent(this.productId)}` : '/pages/home/index', this.data.product.productName || this.data.brandName) },
  retryShare() { return share.prepare(this) },
  async load() {
    if (!this.productId) { feedback.update(this, { loading: false, error: '商品编号不正确' }); return }
    feedback.update(this, { loading: true, error: '' })
    try {
      const detail = await request({ url: `/shop/products/${this.productId}` })
      const product = format.product(detail.product)
      const skus = (detail.skus || []).map(format.sku)
      const availableIndex = skus.findIndex((sku) => Number(sku.stock || 0) > 0)
      const skuIndex = availableIndex >= 0 ? availableIndex : 0
      const selected = skus[skuIndex]
      feedback.update(this, {
        product,
        skus,
        skuIndex, selectedSku: selected || {},
        priceText: selected ? selected.priceText : product.priceText,
        stock: Math.max(0, Number(selected ? selected.stock : product.stock || 0)),
        soldOut: Number(product.status ?? 1) !== 1 || Math.max(0, Number(selected ? selected.stock : product.stock || 0)) <= 0
      })
      wx.setNavigationBarTitle({ title: detail.product.productName || '商品详情' })
    } catch (error) { feedback.update(this, { error: error.message }) }
    finally { feedback.update(this, { loading: false }) }
  },
  retry() { this.load() },
  productImageError() { feedback.update(this, { 'product.imageFailed': true }) },
  selectSku(event) {
    const skuIndex = Number(event.currentTarget.dataset.index)
    const sku = this.data.skus[skuIndex]
    if (!sku || Number(sku.stock || 0) <= 0 || Number(this.data.product.status ?? 1) !== 1) return
    feedback.update(this, { skuIndex, selectedSku: sku, quantity: 1, priceText: sku.priceText, stock: Number(sku.stock || 0), soldOut: false })
  },
  changeQuantity(event) {
    const maximum = Math.max(1, Math.min(99, Number(this.data.stock || 1), Number(this.data.product.purchaseLimit) > 0 ? Number(this.data.product.purchaseLimit) : 99))
    feedback.update(this, { quantity: Math.max(1, Math.min(maximum, this.data.quantity + Number(event.currentTarget.dataset.delta))) })
  },
  purchaseItem() {
    const product = this.data.product
    const sku = this.data.skus[this.data.skuIndex]
    if (this.data.loading || this.data.error || !format.identifier(product.id)) return null
    if (this.data.soldOut) {
      feedback.toast({ title: '该商品暂时缺货', icon: 'none' })
      return null
    }
    const item = { productId: product.id, skuId: sku ? sku.id : null, productName: product.productName,
      coverUrl: product.coverUrl, salePrice: sku ? Number(sku.salePrice ?? product.salePrice) : product.salePrice,
      skuName: sku ? (sku.skuName || sku.specName || '') : '', quantity: this.data.quantity }
    return item
  },
  addToCart() {
    const item = this.purchaseItem()
    if (!item) return
    cart.add(item)
    feedback.toast({ title: '已加入购物车', icon: 'success' })
  },
  buyNow() {
    if (!auth.requireLogin(`/pages/product/index?id=${this.productId}`)) return
    const item = this.purchaseItem()
    if (!item || !cart.beginDirectCheckout(item)) return
    wx.navigateTo({ url: '/pages/checkout/index?direct=1' })
  },
  goCart() { wx.switchTab({ url: '/pages/cart/index' }) }
})
