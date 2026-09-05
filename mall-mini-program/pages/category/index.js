const request = require('../../utils/request')
const format = require('../../utils/format')
const theme = require('../../utils/theme')

Page({
  data: { ...theme.pageData(), categories: [], active: '', keyword: '', products: [], hotProducts: [], loading: true, error: '' },
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
  async loadProducts() {
    const sequence = this.productSequence = (this.productSequence || 0) + 1
    this.setData({ loading: true, error: '' })
    try {
      const result = await request({ url: '/shop/products', params: {
        categoryName: this.data.active, keyword: this.data.keyword, status: 1, pageNum: 1, pageSize: 60
      } })
      if (sequence !== this.productSequence) return
      const products = (result.list || []).map(format.product)
      this.setData({ products, hotProducts: [...products].sort((a, b) => Number(b.salesCount || 0) - Number(a.salesCount || 0)).slice(0, 8) })
    } catch (error) { if (sequence === this.productSequence) this.setData({ error: error.message }) }
    finally { if (sequence === this.productSequence) this.setData({ loading: false }) }
  },
  selectCategory(event) { this.setData({ active: event.currentTarget.dataset.name || '' }, () => this.loadProducts()) },
  onKeywordInput(event) { this.setData({ keyword: event.detail.value }) },
  search() { this.loadProducts() },
  clearFilter() { this.setData({ keyword: '', active: '' }); this.loadProducts() },
  retry() { return this.data.categories.length ? this.loadProducts() : this.loadCategories() },
  applyCategory(name) { this.setData({ active: name || '' }, () => this.loadProducts()) },
  applyKeyword(keyword) { this.setData({ keyword: keyword || '' }, () => this.loadProducts()) },
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
  openProduct(event) { wx.navigateTo({ url: `/pages/product/index?id=${event.currentTarget.dataset.id}` }) }
})
