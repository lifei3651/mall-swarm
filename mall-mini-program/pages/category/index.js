const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const format = require('../../utils/format')
const theme = require('../../utils/theme')
const categoryProduct = require('../../utils/category-product')
const cart = require('../../utils/cart')
const session = require('../../utils/session')

Page({
  data: { ...theme.pageData(), categories: [], active: '', keyword: '', searchedKeyword: '', products: [], hotProducts: [], loading: true, error: '', pageNum: 0, total: 0, hasMore: false, loadingMore: false, moreError: '', browsingAll: false,
    sortMode: 'default', addingId: '', skuVisible: false, skuProduct: {}, skuOptions: [], selectedSkuId: '', selectedPrice: '', confirming: false },
  onLoad() { theme.apply(this); this.loadCategories() },
  onShow() { this._inactive = false; theme.apply(this) },
  onHide() { this._inactive = true; this.addSequence = (this.addSequence || 0) + 1; this.closeSku(); this.setData({ addingId: '', confirming: false }) },
  onUnload() { this.onHide(); this.productSequence = (this.productSequence || 0) + 1 },
  onPullDownRefresh() { Promise.all([theme.apply(this), this.loadCategories()]).finally(() => wx.stopPullDownRefresh()) },
  async loadCategories() {
    feedback.update(this, { loading: true, error: '' })
    try {
      const categories = await request({ url: '/shop/categories' })
      feedback.update(this, { categories: (categories || []).map((item) => ({ ...item, iconUrl: format.mediaUrl(item.iconUrl), iconFailed: false })) })
      await this.loadProducts()
    } catch (error) { feedback.update(this, { error: error.message, loading: false }) }
  },
  async loadProducts(reset = true) {
    if (!reset && (this.data.loading || this.data.loadingMore || !this.data.hasMore)) return
    if (reset) this.productQuery = { categoryName: this.data.active, keyword: String(this.data.keyword || '').trim(), sortMode: this.data.sortMode }
    const sequence = this.productSequence = (this.productSequence || 0) + 1
    const next = reset ? 1 : this.data.pageNum + 1
    const params = { ...this.productQuery, status: 1, pageNum: next, pageSize: 20 }
    feedback.update(this, reset ? { searchedKeyword: this.productQuery.keyword, loading: true, error: '', moreError: '', products: [], hotProducts: [], pageNum: 0, total: 0, hasMore: false, loadingMore: false } : { loadingMore: true, moreError: '' })
    try {
      const result = await request({ url: '/shop/products', params })
      if (sequence !== this.productSequence) return
      const incoming = (result.list || []).map(categoryProduct.card)
      const products = [...new Map((reset ? incoming : this.data.products.concat(incoming)).map((item) => [String(item.id), item])).values()]
      const total = Number.isFinite(Number(result.total)) ? Number(result.total) : products.length
      const hasMore = result.totalPage != null ? next < Number(result.totalPage) : result.total != null ? products.length < total : incoming.length === 20
      feedback.update(this, { products, total, pageNum: next, hasMore: hasMore && incoming.length > 0, hotProducts: [...products].sort((a, b) => Number(b.salesCount || 0) - Number(a.salesCount || 0)).slice(0, 8) })
    } catch (error) { if (sequence === this.productSequence) feedback.update(this, reset ? { error: error.message } : { moreError: error.message }) }
    finally { if (sequence === this.productSequence) feedback.update(this, { loading: false, loadingMore: false }) }
  },
  selectCategory(event) { feedback.update(this, { active: event.currentTarget.dataset.name || '', keyword: '', browsingAll: false, sortMode: 'default' }, () => this.loadProducts()) },
  changeSort(event) {
    const mode = event.currentTarget.dataset.mode
    if (!['default', 'sales', 'price'].includes(mode)) return
    const sortMode = mode === 'price' ? (this.data.sortMode === 'priceAsc' ? 'priceDesc' : 'priceAsc') : mode
    // Sorting covers the whole filtered catalogue, not a hot-products preview.
    this.setData({ sortMode, keyword: this.data.searchedKeyword, browsingAll: true }, () => this.loadProducts())
  },
  onKeywordInput(event) { feedback.update(this, { keyword: event.detail.value }) },
  search() { this.loadProducts() },
  clearSearch() { feedback.update(this, { keyword: '' }); return this.loadProducts() },
  clearFilter() { feedback.update(this, { keyword: '', active: '', browsingAll: false, sortMode: 'default' }); this.loadProducts() },
  showAll() { feedback.update(this, { keyword: '', active: '', browsingAll: true }); this.loadProducts() },
  loadMore() {
    if (this.data.guideEnabled && this.data.guideHasContent && !this.data.browsingAll && !this.data.active && !this.data.searchedKeyword && this.data.guideTemplate !== 'showcase') return
    return this.loadProducts(false)
  },
  retry() { return this.data.categories.length ? this.loadProducts() : this.loadCategories() },
  applyCategory(name) { feedback.update(this, { active: name || '', keyword: '', browsingAll: false }, () => this.loadProducts()) },
  applyKeyword(keyword) { feedback.update(this, { keyword: keyword || '', active: '', browsingAll: true }, () => this.loadProducts()) },
  productImageError(event) {
    const index = this.data.products.findIndex((item) => String(item.id) === String(event.currentTarget.dataset.id))
    if (!Number.isInteger(index) || !this.data.products[index]) return
    feedback.update(this, { [`products[${index}].imageFailed`]: true })
    const hotIndex = this.data.hotProducts.findIndex((item) => String(item.id) === String(event.currentTarget.dataset.id))
    if (hotIndex >= 0) feedback.update(this, { [`hotProducts[${hotIndex}].imageFailed`]: true })
  },
  categoryIconError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (Number.isInteger(index) && this.data.categories[index]) feedback.update(this, { [`categories[${index}].iconFailed`]: true })
  },
  async quickAdd(event) {
    const id = format.identifier(event.currentTarget.dataset.id)
    if (!id || this.data.addingId || this.data.skuVisible || this._inactive) return
    const sequence = this.addSequence = (this.addSequence || 0) + 1
    const token = session.getToken()
    const current = () => !this._inactive && sequence === this.addSequence && token === session.getToken()
    this.setData({ addingId: id })
    try {
      const detail = await request({ url: `/shop/products/${id}` })
      if (!current()) return
      if (String(detail.product && detail.product.id) !== id) throw new Error('商品信息不一致，请刷新后重试')
      if (Number(detail.product.status ?? 1) !== 1) throw new Error('该商品已下架，请刷新列表')
      const skus = (detail.skus || []).filter((item) => Number(item.status ?? 1) === 1)
      if (skus.length) {
        if (!skus.some((item) => Number(item.stock) > 0)) throw new Error('该商品所有规格均已售罄')
        this.skuDetail = detail
        this.setData({ skuVisible: true, skuProduct: categoryProduct.card(detail.product),
          skuOptions: skus.map(format.sku), selectedSkuId: '', selectedPrice: '', addingId: '' })
        this.setTabHidden(true)
      } else await this.addCurrentItem(detail, null, current)
    } catch (error) { if (current()) await feedback.notice(error.message || '加购失败，请稍后重试', '未能加入购物车') }
    finally { if (sequence === this.addSequence) this.setData({ addingId: '' }) }
  },
  setTabHidden(hidden) { const tab = this.getTabBar && this.getTabBar(); if (tab) tab.setData({ hidden }) },
  closeSku() {
    if (this.data.confirming && !this._inactive) return
    this.skuDetail = null
    this.setData({ skuVisible: false, selectedSkuId: '', selectedPrice: '' }); this.setTabHidden(false)
  },
  stopTap() {},
  selectCartSku(event) {
    if (this.data.confirming) return
    const sku = this.data.skuOptions.find((item) => String(item.id) === String(event.currentTarget.dataset.id))
    if (sku && Number(sku.stock) > 0) this.setData({ selectedSkuId: String(sku.id), selectedPrice: sku.priceText })
  },
  async confirmSku() {
    if (this.data.confirming || !this.skuDetail) return
    if (!this.data.selectedSkuId) { await feedback.notice('请先选择商品规格'); return }
    const id = format.identifier(this.skuDetail.product.id), skuId = this.data.selectedSkuId
    const sequence = this.addSequence = (this.addSequence || 0) + 1
    const token = session.getToken()
    const current = () => !this._inactive && sequence === this.addSequence && token === session.getToken()
    this.setData({ confirming: true })
    try {
      const detail = await request({ url: `/shop/products/${id}` })
      if (!current()) return
      if (String(detail.product && detail.product.id) !== id) throw new Error('商品信息不一致，请重新选择')
      await this.addCurrentItem(detail, skuId, current)
      if (current()) { this.setData({ confirming: false }); this.closeSku() }
    } catch (error) { if (current()) await feedback.notice(error.message || '加购失败，请重新选择', '未能加入购物车') }
    finally { if (sequence === this.addSequence) this.setData({ confirming: false }) }
  },
  async addCurrentItem(detail, skuId, current) {
    const selection = categoryProduct.purchase(detail, skuId, cart.list())
    if (session.getToken()) {
      const result = await request({ url: `/shop/products/${format.identifier(detail.product.id)}/purchase-limit/check`, method: 'POST', params: { quantity: selection.productQuantity } })
      if (!current()) return
      if (!result || result.allowed !== true) throw new Error(result && result.message || '未能确认商品限购，请稍后重试')
    }
    if (!current()) return
    // Recheck local quantities after the asynchronous limit check.
    const latest = categoryProduct.purchase(detail, skuId, cart.list())
    if (latest.productQuantity !== selection.productQuantity) throw new Error('购物车数量已变化，请重新加购')
    cart.add(latest.item)
    await feedback.notice('已加入购物车，数量 +1', '操作完成')
  },
  openProduct(event) { const id = format.identifier(event.currentTarget.dataset.id); if (id) wx.navigateTo({ url: `/pages/product/index?id=${id}` }) }
})
