const request = require('../../utils/request')
const format = require('../../utils/format')
const theme = require('../../utils/theme')
const display = require('../../utils/display-config')

Page({
  data: {
    loading: true,
    error: '',
    home: {},
    products: [],
    keyword: '',
    ...theme.pageData(),
    logoFailed: false
  },
  onLoad() { this.loadHome() },
  onShow() { theme.sync(this); if (this.loadedOnce) this.loadHome(true) },
  onPullDownRefresh() { this.loadHome().finally(() => wx.stopPullDownRefresh()) },
  async loadHome(silent = false) {
    if (this.refreshing) return this.refreshing
    this.refreshing = this.fetchHome(silent).finally(() => { this.refreshing = null })
    return this.refreshing
  },
  async fetchHome(silent) {
    if (!silent) this.setData({ loading: true, error: '' })
    try {
      const [home, productPage] = await Promise.all([
        request({ url: '/shop/home' }),
        request({ url: '/shop/products', params: { status: 1, pageNum: 1, pageSize: 20 } })
      ])
      const products = (productPage && productPage.list ? productPage.list : []).map(format.product)
      home.logoUrl = format.mediaUrl(home.logoUrl)
      home.banners = (home.banners || []).map((item) => ({
        ...item,
        imageUrl: format.mediaUrl(item.imageUrl),
        imageFailed: false
      }))
      home.categoryList = (home.categoryList || []).map((item) => ({
        ...item,
        iconUrl: format.mediaUrl(item.iconUrl),
        iconFailed: false,
        initial: String(item.categoryName || '商').slice(0, 1)
      }))
      const palette = theme.remember(home)
      home.newArrivals = (home.newArrivals || []).map(format.product)
      home.liveRooms = (home.liveRooms || []).filter((item) => item && item.room).map((item) => ({ ...item, room: { ...item.room, coverUrl: format.mediaUrl(item.room.coverUrl) } }))
      const decoration = display.home(home.displayConfig)
      const brandCultureEnabled = display.toggle(home.brandCultureEnabled, false)
      this.setData({ home, products, ...palette, ...decoration, brandCultureEnabled, logoFailed: false, error: '' })
      this.loadedOnce = true
      wx.setNavigationBarTitle({ title: home.brandName || '商城首页' })
    } catch (error) {
      if (!silent) this.setData({ error: error.message || '加载失败' })
      else wx.showToast({ title: '装修更新失败，暂保留原页面', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  openContent(event) {
    const type = event.currentTarget.dataset.type
    if (['culture', 'live', 'newArrivals'].includes(type)) wx.navigateTo({ url: `/pages/store-content/index?type=${type}` })
  },
  openLive(event) { wx.navigateTo({ url: `/pages/store-content/index?type=live&id=${event.currentTarget.dataset.id}` }) },
  arrivalImageError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (Number.isInteger(index) && this.data.home.newArrivals[index]) this.setData({ [`home.newArrivals[${index}].imageFailed`]: true })
  },
  logoError() { this.setData({ logoFailed: true }) },
  categoryIconError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index) || !this.data.home.categoryList || !this.data.home.categoryList[index]) return
    this.setData({ [`home.categoryList[${index}].iconFailed`]: true })
  },
  bannerImageError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index) || !this.data.home.banners || !this.data.home.banners[index]) return
    this.setData({ [`home.banners[${index}].imageFailed`]: true })
  },
  productImageError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index) || !this.data.products[index]) return
    this.setData({ [`products[${index}].imageFailed`]: true })
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
  openBanner(event) {
    const type = String(event.currentTarget.dataset.type || '').toUpperCase()
    const value = String(event.currentTarget.dataset.value || '').trim()
    if (type === 'BRAND_CULTURE' && this.data.brandCultureEnabled) {
      this.openContent({ currentTarget: { dataset: { type: 'culture' } } })
      return
    }
    if (type === 'URL') {
      wx.showToast({ title: '此活动链接暂不支持在小程序内打开', icon: 'none' })
      return
    }
    if (type === 'PRODUCT' && /^\d+$/.test(value)) {
      wx.navigateTo({ url: `/pages/product/index?id=${value}` })
      return
    }
    if (type === 'CATEGORY' && value) this.openCategory({ currentTarget: { dataset: { name: value } } })
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
