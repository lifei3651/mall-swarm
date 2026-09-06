const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const format = require('../../utils/format')
const theme = require('../../utils/theme')
const display = require('../../utils/display-config')
const share = require('../../utils/share')

Page({
  data: {
    loading: true,
    error: '',
    home: {},
    products: [],
    campaigns: [], campaignError: '',
    keyword: '',
    ...theme.pageData(),
    logoFailed: false
  },
  onLoad() { this.loadHome() },
  onShow() { share.prepare(this); theme.sync(this); if (this.loadedOnce) this.loadHome(true) },
  onHide() { share.hide(this) },
  onUnload() { share.hide(this) },
  onShareAppMessage() { return share.message(this, '/pages/home/index', this.data.home.brandName || this.data.brandName) },
  retryShare() { return share.prepare(this) },
  onPullDownRefresh() { this.loadHome().finally(() => wx.stopPullDownRefresh()) },
  async loadHome(silent = false) {
    if (this.refreshing) return this.refreshing
    this.refreshing = this.fetchHome(silent).finally(() => { this.refreshing = null })
    return this.refreshing
  },
  async fetchHome(silent) {
    if (!silent) feedback.update(this, { loading: true, error: '' })
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
      let campaigns = [], campaignError = ''
      if (decoration.layoutTemplate === 'campaign-feed') {
        try {
          campaigns = (await request({ url: '/shop/flash-sales' }) || []).filter((row) => row && row.activity && row.product).map((row) => ({ ...row, priceText: format.money(row.activity.flashPrice), stateText: ({ ACTIVE: '进行中', UPCOMING: '即将开始', SOLD_OUT: '已抢完', ENDED: '已结束' })[row.activityState] || '暂不可用' }))
        } catch (_) { campaignError = '限时活动加载失败，点击重新查看' }
      }
      const brandCultureEnabled = display.toggle(home.brandCultureEnabled, false)
      feedback.update(this, { home, products, campaigns, campaignError, ...palette, ...decoration, brandCultureEnabled, logoFailed: false, error: '' })
      this.loadedOnce = true
      // A slow homepage response must not rename the page the user has since opened.
      if (typeof getCurrentPages === 'function' && getCurrentPages().slice(-1)[0] === this) wx.setNavigationBarTitle({ title: home.brandName || '商城首页' })
    } catch (error) {
      if (!silent) feedback.update(this, { error: error.message || '加载失败' })
      else feedback.toast({ title: '装修更新失败，暂保留原页面', icon: 'none' })
    } finally {
      feedback.update(this, { loading: false })
    }
  },
  openContent(event) {
    const type = event.currentTarget.dataset.type
    if (['culture', 'live', 'newArrivals'].includes(type)) wx.navigateTo({ url: `/pages/store-content/index?type=${type}` })
  },
  openLive(event) { wx.navigateTo({ url: `/pages/store-content/index?type=live&id=${event.currentTarget.dataset.id}` }) },
  arrivalImageError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (Number.isInteger(index) && this.data.home.newArrivals[index]) feedback.update(this, { [`home.newArrivals[${index}].imageFailed`]: true })
  },
  logoError() { feedback.update(this, { logoFailed: true }) },
  categoryIconError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index) || !this.data.home.categoryList || !this.data.home.categoryList[index]) return
    feedback.update(this, { [`home.categoryList[${index}].iconFailed`]: true })
  },
  bannerImageError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index) || !this.data.home.banners || !this.data.home.banners[index]) return
    feedback.update(this, { [`home.banners[${index}].imageFailed`]: true })
  },
  productImageError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index) || !this.data.products[index]) return
    feedback.update(this, { [`products[${index}].imageFailed`]: true })
  },
  onKeywordInput(event) { feedback.update(this, { keyword: event.detail.value }) },
  notices() { wx.navigateTo({ url: '/pages/notices/index' }) },
  campaign(event) { const id = format.identifier(event.currentTarget.dataset.id); wx.navigateTo({ url: `/pages/campaign/index${id ? '?id=' + id : ''}` }) },
  allProducts() {
    wx.switchTab({ url: '/pages/category/index', success: () => {
      const pages = getCurrentPages(); const page = pages[pages.length - 1]
      if (page && page.showAll) page.showAll()
    } })
  },
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
      feedback.toast({ title: '此活动链接暂不支持在小程序内打开', icon: 'none' })
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
