const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const cart = require('../../utils/cart')
const format = require('../../utils/format')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const share = require('../../utils/share')
const purchaseLimit = require('../../utils/purchase-limit')
const quantityRules = require('../../utils/quantity')
const reviews = require('../../utils/product-reviews')
const session = require('../../utils/session')

Page({
  ...reviews.methods,
  data: {
    ...theme.pageData(),
    ...reviews.data,
    loading: true, error: '', product: {}, skus: [], skuIndex: 0, quantity: 1, galleryHeight: 750,
    priceText: '0.00', stock: 0, soldOut: false, selectedSku: {}, purchasePending: false, quantityInput: '1', maxQuantity: 1, galleryIndex: 0, cartCount: 0
  },
  onLoad(options = {}) { theme.apply(this); this.productId = format.identifier(options.id); this.reviewOrderItemId = format.identifier(options.orderItemId); this.load() },
  onShow() {
    this.purchaseInactive = false; this.setData({ cartCount: cart.count() })
    if (this.reviewOwner !== session.getToken()) { this.reviewOwner = session.getToken(); this.setData({ ...reviews.data }) }
    if (!this.data.loading && this.productId) this.loadReviews()
    return share.prepare(this)
  },
  onHide() { this.purchaseInactive = true; this.purchaseSequence = (this.purchaseSequence || 0) + 1; this.setData({ purchasePending: false }); reviews.hide(this); share.hide(this) },
  onUnload() { this.onHide() },
  onShareAppMessage() { return share.message(this, this.productId ? `/pages/product/index?id=${encodeURIComponent(this.productId)}` : '/pages/home/index', this.data.product.productName || this.data.brandName) },
  async retryShare() {
    await share.prepare(this)
    if (this.data.shareError && !this.purchaseInactive) await feedback.notice(this.data.shareError, '暂时无法分享')
  },
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
      const stock = Math.max(0, Number(selected ? selected.stock : product.stock || 0))
      feedback.update(this, {
        product, galleryIndex: 0, galleryHeight: this.galleryHeights?.[product.gallery?.[0]] || 750,
        skus,
        skuIndex, selectedSku: selected || {},
        priceText: selected ? selected.priceText : product.priceText,
        stock, maxQuantity: quantityRules.maximum(stock, product.purchaseLimit), quantity: 1, quantityInput: '1',
        marketPriceText: format.money(selected ? selected.marketPrice : product.marketPrice), marketPrice: Number(selected ? selected.marketPrice : product.marketPrice || 0),
        freightLabel: this.freightLabel(product),
        soldOut: Number(product.status ?? 1) !== 1 || Math.max(0, Number(selected ? selected.stock : product.stock || 0)) <= 0
      })
      wx.setNavigationBarTitle({ title: detail.product.productName || '商品详情' })
      this.loadReviews()
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
    feedback.update(this, { skuIndex, selectedSku: sku, quantity: 1, quantityInput: '1', maxQuantity: quantityRules.maximum(sku.stock, this.data.product.purchaseLimit), marketPrice: Number(sku.marketPrice || 0), marketPriceText: format.money(sku.marketPrice), priceText: sku.priceText, stock: Number(sku.stock || 0), soldOut: false })
  },
  changeQuantity(event) {
    if (this.data.purchasePending || ![1, -1].includes(Number(event.currentTarget.dataset.delta))) return
    this.setQuantity(this.data.quantity + Number(event.currentTarget.dataset.delta))
  },
  setQuantity(value) { const quantity = quantityRules.resolve(value, quantityRules.maximum(this.data.stock, this.data.product.purchaseLimit)); this.setData({ quantity, quantityInput: String(quantity) }) },
  quantityChanged(event) {
    if (this.data.purchasePending) return
    const value = quantityRules.sanitize(event.detail.value)
    if (!value) { this.setData({ quantityInput: '' }); return '' }
    this.setQuantity(value); return this.data.quantityInput
  },
  commitQuantity() { if (!this.data.purchasePending) this.setQuantity(this.data.quantityInput) },
  galleryImageLoaded(event) {
    const { width, height } = event.detail || {}
    const src = event.currentTarget.dataset.src
    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0 || !this.data.product.gallery?.includes(src)) return
    this.galleryHeights = this.galleryHeights || {}
    this.galleryHeights[src] = Math.min(750, 750 * height / width)
    if (this.data.product.gallery[this.data.galleryIndex] === src) this.setData({ galleryHeight: this.galleryHeights[src] })
  },
  galleryChanged(event) {
    const galleryIndex = Number(event.detail.current) || 0
    const src = this.data.product.gallery?.[galleryIndex]
    this.setData({ galleryIndex, galleryHeight: this.galleryHeights?.[src] || 750 })
  },
  freightLabel(product) {
    return ({ 1: `统一运费 ¥${format.money(product.freightAmount)}`, 2: `满 ¥${format.money(product.freeShippingAmount)} 包邮，未满 ¥${format.money(product.freightAmount)}`, 3: '按配送地区计算，部分地区暂不配送' })[Number(product.freightType)] || '全国包邮'
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
    if (!this.data.quantityInput) this.commitQuantity()
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
        this.setData({ cartCount: cart.count() })
        await feedback.notice(`已加入购物车，数量 +${selection.item.quantity}`, '操作完成')
      }
    } catch (error) { if (current()) await feedback.notice(error.message || '商品信息更新失败，请稍后重试', direct ? '暂时无法购买' : '未能加入购物车') }
    finally { if (sequence === this.purchaseSequence) this.setData({ purchasePending: false }) }
  },
  goCart() { wx.switchTab({ url: '/pages/cart/index' }) },
  goHome() { wx.switchTab({ url: '/pages/home/index' }) }
})
