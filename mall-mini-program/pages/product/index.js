const request = require('../../utils/request')
const cart = require('../../utils/cart')
const format = require('../../utils/format')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')

Page({
  data: {
    ...theme.pageData(),
    loading: true, error: '', product: {}, skus: [], skuIndex: 0, quantity: 1,
    priceText: '0.00', stock: 0, soldOut: false
  },
  onLoad(options = {}) { theme.apply(this); this.productId = format.identifier(options.id); this.load() },
  async load() {
    if (!this.productId) { this.setData({ loading: false, error: '商品编号不正确' }); return }
    this.setData({ loading: true, error: '' })
    try {
      const detail = await request({ url: `/shop/products/${this.productId}` })
      const product = format.product(detail.product)
      const skus = (detail.skus || []).map((sku) => ({ ...sku, priceText: format.money(sku.salePrice) }))
      const availableIndex = skus.findIndex((sku) => Number(sku.stock || 0) > 0)
      const skuIndex = availableIndex >= 0 ? availableIndex : 0
      const selected = skus[skuIndex]
      this.setData({
        product,
        skus,
        skuIndex,
        priceText: selected ? selected.priceText : product.priceText,
        stock: Math.max(0, Number(selected ? selected.stock : product.stock || 0)),
        soldOut: Number(product.status ?? 1) !== 1 || Math.max(0, Number(selected ? selected.stock : product.stock || 0)) <= 0
      })
      wx.setNavigationBarTitle({ title: detail.product.productName || '商品详情' })
    } catch (error) { this.setData({ error: error.message }) }
    finally { this.setData({ loading: false }) }
  },
  retry() { this.load() },
  productImageError() { this.setData({ 'product.imageFailed': true }) },
  selectSku(event) {
    const skuIndex = Number(event.currentTarget.dataset.index)
    const sku = this.data.skus[skuIndex]
    if (!sku || Number(sku.stock || 0) <= 0 || Number(this.data.product.status ?? 1) !== 1) return
    this.setData({ skuIndex, quantity: 1, priceText: sku.priceText, stock: Number(sku.stock || 0), soldOut: false })
  },
  changeQuantity(event) {
    const maximum = Math.max(1, Math.min(99, Number(this.data.stock || 1)))
    this.setData({ quantity: Math.max(1, Math.min(maximum, this.data.quantity + Number(event.currentTarget.dataset.delta))) })
  },
  purchaseItem() {
    const product = this.data.product
    const sku = this.data.skus[this.data.skuIndex]
    if (this.data.loading || this.data.error || !format.identifier(product.id)) return null
    if (this.data.soldOut) {
      wx.showToast({ title: '该商品暂时缺货', icon: 'none' })
      return null
    }
    const item = { productId: product.id, skuId: sku ? sku.id : null, productName: product.productName,
      coverUrl: product.coverUrl, salePrice: sku ? Number(sku.salePrice ?? product.salePrice) : product.salePrice,
      skuName: sku ? (sku.skuName || sku.specName || '') : '', quantity: this.data.quantity }
    cart.add(item)
    return `${item.productId}:${item.skuId || 0}`
  },
  addToCart() {
    if (!this.purchaseItem()) return
    wx.showToast({ title: '已加入购物车', icon: 'success' })
  },
  buyNow() {
    if (!auth.requireLogin(`/pages/product/index?id=${this.productId}`)) return
    const key = this.purchaseItem()
    if (!key) return
    cart.selectOnly(key)
    wx.navigateTo({ url: '/pages/checkout/index' })
  },
  goCart() { wx.switchTab({ url: '/pages/cart/index' }) }
})
