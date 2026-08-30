const request = require('../../utils/request')
const format = require('../../utils/format')

Page({
  data: { categories: [], active: '', keyword: '', products: [], loading: true, error: '' },
  onLoad() { this.loadCategories() },
  onPullDownRefresh() { this.loadProducts().finally(() => wx.stopPullDownRefresh()) },
  async loadCategories() {
    try {
      const categories = await request({ url: '/shop/categories' })
      this.setData({ categories: categories || [] })
      await this.loadProducts()
    } catch (error) { this.setData({ error: error.message, loading: false }) }
  },
  async loadProducts() {
    this.setData({ loading: true, error: '' })
    try {
      const result = await request({ url: '/shop/products', params: {
        categoryName: this.data.active, keyword: this.data.keyword, status: 1, pageNum: 1, pageSize: 60
      } })
      this.setData({ products: (result.list || []).map(format.product) })
    } catch (error) { this.setData({ error: error.message }) }
    finally { this.setData({ loading: false }) }
  },
  selectCategory(event) { this.setData({ active: event.currentTarget.dataset.name || '' }, () => this.loadProducts()) },
  onKeywordInput(event) { this.setData({ keyword: event.detail.value }) },
  search() { this.loadProducts() },
  applyCategory(name) { this.setData({ active: name || '' }, () => this.loadProducts()) },
  applyKeyword(keyword) { this.setData({ keyword: keyword || '' }, () => this.loadProducts()) },
  openProduct(event) { wx.navigateTo({ url: `/pages/product/index?id=${event.currentTarget.dataset.id}` }) }
})
