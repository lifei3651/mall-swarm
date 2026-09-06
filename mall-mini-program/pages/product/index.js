const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const cart = require('../../utils/cart')
const format = require('../../utils/format')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const share = require('../../utils/share')
const purchaseLimit = require('../../utils/purchase-limit')

Page({
  data: {
    ...theme.pageData(),
    loading: true, error: '', product: {}, skus: [], skuIndex: 0, quantity: 1,
    priceText: '0.00', stock: 0, soldOut: false, selectedSku: {}, purchasePending: false
  },
  onLoad(options = {}) { theme.apply(this); this.productId = format.identifier(options.id); this.load() },
  onShow() { this.purchaseInactive = false; return share.prepare(this) },
  onHide() { this.purchaseInactive = true; this.purchaseSequence = (this.purchaseSequence || 0) + 1; this.setData({ purchasePending: false }); share.hide(this) },
  onUnload() { this.onHide() },
  onShareAppMessage() { return share.message(this, this.productId ? `/pages/product/index?id=${encodeURIComponent(this.productId)}` : '/pages/home/index', this.data.product.productName || this.data.brandName) },
  retryShare() { return share.prepare(this) },
  async load() {
    if (!this.productId) { feedback.update(this, { loading: false, error: '商品编号不正确' }); return }
    feedback.update(this, { loading: true, error: '' })
    try {
      const detail = await request({ url: `/shop/products/${this.productId}` })
      const product = format.product(detail.product)
      const skus = (detail.skus || []).filter((sku) => Number(sku.status ?? 1) === 1).map(format.sku)
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
    if (this.data.purchasePending) return
    const skuIndex = Number(event.currentTarget.dataset.index)
    const sku = this.data.skus[skuIndex]
    if (!sku || Number(sku.stock || 0) <= 0 || Number(this.data.product.status ?? 1) !== 1) return
    feedback.update(this, { skuIndex, selectedSku: sku, quantity: 1, priceText: sku.priceText, stock: Number(sku.stock || 0), soldOut: false })
  },
  changeQuantity(event) {
    if (this.data.purchasePending || ![1, -1].includes(Number(event.currentTarget.dataset.delta))) return
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
  addToCart() { return this.purchaseAction(false) },
  buyNow() { return this.purchaseAction(true) },
  async purchaseAction(direct) {
    if (this.data.purchasePending || this.purchaseInactive) return
    if (!auth.requireLogin(`/pages/product/index?id=${this.productId || this.data.product.id}`)) return
    const item = this.purchaseItem()
    if (!item) return
    const sequence = this.purchaseSequence = (this.purchaseSequence || 0) + 1
    const current = () => !this.purchaseInactive && sequence === this.purchaseSequence
    this.setData({ purchasePending: true })
    try {
      const selection = await purchaseLimit.checkAddition(item.productId, item.skuId, item.quantity, {
        isCurrent: current, getRows: direct ? () => [] : () => cart.list()
      })
      if (!selection || !current()) return
      if (direct) {
        if (!cart.beginDirectCheckout(selection.item)) throw new Error('登录信息不完整，请重新登录后购买')
        wx.navigateTo({ url: '/pages/checkout/index?direct=1' })
      } else {
        cart.add(selection.item)
        await feedback.notice(`已加入购物车，数量 +${selection.item.quantity}`, '操作完成')
      }
    } catch (error) { if (current()) await feedback.notice(error.message || '商品信息更新失败，请稍后重试', direct ? '暂时无法购买' : '未能加入购物车') }
    finally { if (sequence === this.purchaseSequence) this.setData({ purchasePending: false }) }
  },
  goCart() { wx.switchTab({ url: '/pages/cart/index' }) }
})
