const request = require('../../utils/request')
const format = require('../../utils/format')

Page({
  data: {
    loading: true,
    error: '',
    home: {},
    products: [],
    keyword: '',
    themeColor: '#e7193f',
    logoFailed: false
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
      home.logoUrl = format.mediaUrl(home.logoUrl)
      home.banners = (home.banners || []).map((item) => ({ ...item, imageUrl: format.mediaUrl(item.imageUrl) }))
      home.categoryList = (home.categoryList || []).map((item) => ({
        ...item,
        iconUrl: format.mediaUrl(item.iconUrl),
        initial: String(item.categoryName || '商').slice(0, 1)
      }))
      const themeColor = /^#[0-9a-fA-F]{6}$/.test(home.themeColor || '') ? home.themeColor : '#e7193f'
      getApp().globalData.brand = home
      this.setData({ home, products, themeColor, logoFailed: false })
      wx.setNavigationBarTitle({ title: home.brandName || '商城首页' })
      wx.setTabBarStyle({ selectedColor: themeColor })
    } catch (error) {
      this.setData({ error: error.message || '加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },
  logoError() { this.setData({ logoFailed: true }) },
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
