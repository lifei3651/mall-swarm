const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const format = require('../../utils/format')
const theme = require('../../utils/theme')
const categoryProduct = require('../../utils/category-product')
const quickCart = require('../../utils/quick-cart')

Page({
  ...quickCart.methods,
  data: { ...theme.pageData(), categories: [], active: '', keyword: '', searchedKeyword: '', products: [], hotProducts: [], loading: true, error: '', pageNum: 0, total: 0, hasMore: false, loadingMore: false, moreError: '', browsingAll: false,
    sortMode: 'default', ...quickCart.data },
  onLoad() { theme.apply(this); this.loadCategories() },
  onShow() { quickCart.show(this); theme.apply(this) },
  onHide() { quickCart.hide(this) },
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
  openProduct(event) { const id = format.identifier(event.currentTarget.dataset.id); if (id) wx.navigateTo({ url: `/pages/product/index?id=${id}` }) }
})
