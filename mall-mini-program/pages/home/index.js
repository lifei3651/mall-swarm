const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const format = require('../../utils/format')
const theme = require('../../utils/theme')
const display = require('../../utils/display-config')
const share = require('../../utils/share')
const quickCart = require('../../utils/quick-cart')
const categoryProduct = require('../../utils/category-product')
const { decorateCampaignProducts } = require('../../utils/campaign-display')

Page({
  ...quickCart.methods,
  quickCartRoute: '/pages/home/index',
  data: {
    ...quickCart.data,
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
  onShow() { this.campaignClockActive = true; quickCart.show(this); share.prepare(this); theme.sync(this); this.startCampaignClock(); if (this.loadedOnce) this.loadHome(true) },
  onHide() { this.campaignClockActive = false; clearTimeout(this.campaignTimer); quickCart.hide(this); share.hide(this) },
  onUnload() { this.onHide() },
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
      const products = (productPage && productPage.list ? productPage.list : []).map(categoryProduct.card)
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
          campaigns = await request({ url: '/shop/flash-sales' })
          if (!Array.isArray(campaigns)) throw new Error('活动数据不完整')
        } catch (_) { campaigns = []; campaignError = '活动信息暂不可用，以下按普通售价展示。点击重试' }
      }
      const brandCultureEnabled = display.toggle(home.brandCultureEnabled, false)
      this.baseProducts = products
      feedback.update(this, { home, products: decorateCampaignProducts(products, campaigns, decoration.layoutTemplate), campaigns, campaignError, ...palette, ...decoration, brandCultureEnabled, logoFailed: false, error: '' })
      this.startCampaignClock()
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
  startCampaignClock() {
    clearTimeout(this.campaignTimer)
    if (!this.campaignClockActive || this.data.layoutTemplate !== 'campaign-feed' || !this.data.products.some(product => product.campaign)) return
    this.campaignTimer = setTimeout(() => {
      if (!this.campaignClockActive) return
      const patch = {}
      decorateCampaignProducts(this.baseProducts || [], this.data.campaigns, this.data.layoutTemplate).forEach((product, index) => {
        const previous = this.data.products[index]
        if (!previous || JSON.stringify(previous.campaign) === JSON.stringify(product.campaign)) return
        patch[`products[${index}].campaign`] = product.campaign
        if (previous.priceText !== product.priceText) patch[`products[${index}].priceText`] = product.priceText
      })
      if (Object.keys(patch).length) this.setData(patch)
      this.startCampaignClock()
    }, 1000)
  },
  retryCampaigns() { return this.loadHome() },
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
    if (this.baseProducts && this.baseProducts[index]) this.baseProducts[index].imageFailed = true
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
  openProduct(event) {
    const id = format.identifier(event.currentTarget.dataset.id)
    if (!id) return
    const product = this.data.products.find(item => String(item.id) === id)
    if (product && product.campaign) return this.campaign({ currentTarget: { dataset: { id: product.campaign.id } } })
    wx.navigateTo({ url: `/pages/product/index?id=${id}` })
  },
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
