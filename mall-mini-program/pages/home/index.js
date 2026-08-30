const request = require('../../utils/request')
const format = require('../../utils/format')

Page({
  data: {
    loading: true,
    error: '',
    home: {},
    products: [],
    keyword: '',
    themeColor: '#e7193f'
  },
  onLoad() { this.loadHome() },
  onPullDownRefresh() { this.loadHome().finally(() => wx.stopPullDownRefresh()) },
  async loadHome() {
    this.setData({ loading: true, error: '' })
    try {
      const [home, productPage] = await Promise.all([
        request({ url: '/shop/home' }),
        request({ url: '/shop/products', params: { status: 1, pageNum: 1, pageSize: 20 } })
      ])
      const products = (productPage && productPage.list ? productPage.list : []).map(format.product)
      home.categoryList = (home.categoryList || []).map((item) => ({
        ...item,
        initial: String(item.categoryName || '商').slice(0, 1)
      }))
      const themeColor = /^#[0-9a-fA-F]{6}$/.test(home.themeColor || '') ? home.themeColor : '#e7193f'
      getApp().globalData.brand = home
      this.setData({ home, products, themeColor })
      wx.setNavigationBarTitle({ title: home.brandName || '商城首页' })
      wx.setTabBarStyle({ selectedColor: themeColor })
    } catch (error) {
      this.setData({ error: error.message || '加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },
  onKeywordInput(event) { this.setData({ keyword: event.detail.value }) },
  search() {
    const keyword = String(this.data.keyword || '').trim()
    wx.switchTab({ url: '/pages/category/index', success: () => {
      const pages = getCurrentPages()
      const page = pages[pages.length - 1]
      if (page && page.applyKeyword) page.applyKeyword(keyword)
    } })
  },
  openProduct(event) { wx.navigateTo({ url: `/pages/product/index?id=${event.currentTarget.dataset.id}` }) },
  openCategory(event) {
    const name = event.currentTarget.dataset.name || ''
    wx.switchTab({ url: '/pages/category/index', success: () => {
      const pages = getCurrentPages()
      const page = pages[pages.length - 1]
      if (page && page.applyCategory) page.applyCategory(name)
    } })
  },
  retry() { this.loadHome() }
})
