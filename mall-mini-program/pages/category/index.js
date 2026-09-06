const request = require('../../utils/request')
const format = require('../../utils/format')
const theme = require('../../utils/theme')

Page({
  data: { ...theme.pageData(), categories: [], active: '', keyword: '', searchedKeyword: '', products: [], hotProducts: [], loading: true, error: '', pageNum: 0, total: 0, hasMore: false, loadingMore: false, moreError: '', browsingAll: false },
  onLoad() { theme.apply(this); this.loadCategories() },
  onShow() { theme.apply(this) },
  onPullDownRefresh() { Promise.all([theme.apply(this), this.loadCategories()]).finally(() => wx.stopPullDownRefresh()) },
  async loadCategories() {
    this.setData({ loading: true, error: '' })
    try {
      const categories = await request({ url: '/shop/categories' })
      this.setData({ categories: (categories || []).map((item) => ({ ...item, iconUrl: format.mediaUrl(item.iconUrl), iconFailed: false })) })
      await this.loadProducts()
    } catch (error) { this.setData({ error: error.message, loading: false }) }
  },
  async loadProducts(reset = true) {
    if (!reset && (this.data.loading || this.data.loadingMore || !this.data.hasMore)) return
    if (reset) this.productQuery = { categoryName: this.data.active, keyword: String(this.data.keyword || '').trim() }
    const sequence = this.productSequence = (this.productSequence || 0) + 1
    const next = reset ? 1 : this.data.pageNum + 1
    const params = { ...this.productQuery, status: 1, pageNum: next, pageSize: 20 }
    this.setData(reset ? { searchedKeyword: this.productQuery.keyword, loading: true, error: '', moreError: '', products: [], hotProducts: [], pageNum: 0, total: 0, hasMore: false, loadingMore: false } : { loadingMore: true, moreError: '' })
    try {
      const result = await request({ url: '/shop/products', params })
      if (sequence !== this.productSequence) return
      const incoming = (result.list || []).map(format.product)
      const products = [...new Map((reset ? incoming : this.data.products.concat(incoming)).map((item) => [String(item.id), item])).values()]
      const total = Number.isFinite(Number(result.total)) ? Number(result.total) : products.length
      const hasMore = result.totalPage != null ? next < Number(result.totalPage) : result.total != null ? products.length < total : incoming.length === 20
      this.setData({ products, total, pageNum: next, hasMore: hasMore && incoming.length > 0, hotProducts: [...products].sort((a, b) => Number(b.salesCount || 0) - Number(a.salesCount || 0)).slice(0, 8) })
    } catch (error) { if (sequence === this.productSequence) this.setData(reset ? { error: error.message } : { moreError: error.message }) }
    finally { if (sequence === this.productSequence) this.setData({ loading: false, loadingMore: false }) }
  },
  selectCategory(event) { this.setData({ active: event.currentTarget.dataset.name || '', keyword: '', browsingAll: false }, () => this.loadProducts()) },
  onKeywordInput(event) { this.setData({ keyword: event.detail.value }) },
  search() { this.loadProducts() },
  clearSearch() { this.setData({ keyword: '' }); return this.loadProducts() },
  clearFilter() { this.setData({ keyword: '', active: '', browsingAll: false }); this.loadProducts() },
  showAll() { this.setData({ keyword: '', active: '', browsingAll: true }); this.loadProducts() },
  loadMore() {
    if (this.data.guideEnabled && this.data.guideHasContent && !this.data.browsingAll && !this.data.active && !this.data.searchedKeyword && this.data.guideTemplate !== 'showcase') return
    return this.loadProducts(false)
  },
  retry() { return this.data.categories.length ? this.loadProducts() : this.loadCategories() },
  applyCategory(name) { this.setData({ active: name || '', keyword: '', browsingAll: false }, () => this.loadProducts()) },
  applyKeyword(keyword) { this.setData({ keyword: keyword || '', active: '', browsingAll: true }, () => this.loadProducts()) },
  productImageError(event) {
    const index = this.data.products.findIndex((item) => String(item.id) === String(event.currentTarget.dataset.id))
    if (!Number.isInteger(index) || !this.data.products[index]) return
    this.setData({ [`products[${index}].imageFailed`]: true })
    const hotIndex = this.data.hotProducts.findIndex((item) => String(item.id) === String(event.currentTarget.dataset.id))
    if (hotIndex >= 0) this.setData({ [`hotProducts[${hotIndex}].imageFailed`]: true })
  },
  categoryIconError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (Number.isInteger(index) && this.data.categories[index]) this.setData({ [`categories[${index}].iconFailed`]: true })
  },
  openProduct(event) { const id = format.identifier(event.currentTarget.dataset.id); if (id) wx.navigateTo({ url: `/pages/product/index?id=${id}` }) }
})
