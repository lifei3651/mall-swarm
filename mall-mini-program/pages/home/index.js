const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const format = require('../../utils/format')
const theme = require('../../utils/theme')
const display = require('../../utils/display-config')
const share = require('../../utils/share')
const quickCart = require('../../utils/quick-cart')
const categoryProduct = require('../../utils/category-product')
const searchHistory = require('../../utils/search-history')
const { decorateCampaignProducts } = require('../../utils/campaign-display')
const displayPrices = (products) => products.map(product => ({ ...product, priceInteger: product.priceText.split('.')[0], priceDecimal: product.priceText.split('.')[1] }))

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
    activeCategory: '', searchedKeyword: '', searchFocused: false, recentSearches: [], hotSearches: ['护理套装','健康生活','品质好物'], productsLoading: false, productError: '',
    ...theme.pageData(),
    logoFailed: false
  },
  onLoad() { this.setData({ recentSearches: searchHistory.list() }); this.loadHome() },
  onShow() { this.campaignClockActive = true; quickCart.show(this); share.prepare(this); theme.sync(this); this.startCampaignClock(); if (this.loadedOnce || this.reloadNeeded) { this.reloadNeeded = false; return this.loadHome(this.loadedOnce === true) } },
  onHide() { this.reloadNeeded = true; this.refreshing = null; this.pendingSearch = null; this.historyClearSequence = (this.historyClearSequence || 0) + 1; this.clearingHistory = false; this.campaignClockActive = false; this.productSequence = (this.productSequence || 0) + 1; clearTimeout(this.suggestionsTimer); this.setData({ searchFocused: false, productsLoading: false }); clearTimeout(this.campaignTimer); quickCart.hide(this); share.hide(this) },
  onUnload() { this.onHide() },
  onShareAppMessage() { return share.message(this, '/pages/home/index', this.data.home.brandName || this.data.brandName) },
  retryShare() { return share.prepare(this) },
  onPullDownRefresh() { this.loadHome().finally(() => wx.stopPullDownRefresh()) },
  async loadHome(silent = false) {
    if (this.refreshing) return this.refreshing
    const task = this.fetchHome(silent).finally(() => { if (this.refreshing === task) this.refreshing = null })
    this.refreshing = task
    return this.refreshing
  },
  async fetchHome(silent) {
    const sequence = this.productSequence = (this.productSequence || 0) + 1
    if (!silent) feedback.update(this, { loading: true, error: '' })
    try {
      const [home, productPage] = await Promise.all([
        request({ url: '/shop/home' }),
        request({ url: '/shop/products', params: { status: 1, pageNum: 1, pageSize: 60, keyword: this.data.searchedKeyword, categoryName: this.data.activeCategory } })
      ])
      if (sequence !== this.productSequence) return
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
      home.newArrivals = (home.newArrivals || []).map(format.product)
      home.liveRooms = (home.liveRooms || []).filter((item) => item && item.room && format.identifier(item.room.id)).map((item) => ({ ...item, key: format.identifier(item.room.id), room: { ...item.room, coverUrl: format.mediaUrl(item.room.coverUrl) } }))
      const decoration = display.home(home.displayConfig)
      let campaigns = [], campaignError = ''
      if (decoration.layoutTemplate === 'campaign-feed') {
        try {
          campaigns = await request({ url: '/shop/flash-sales' })
          if (!Array.isArray(campaigns)) throw new Error('活动数据不完整')
        } catch (_) { campaigns = []; campaignError = '活动信息暂不可用，以下按普通售价展示。点击重试' }
      }
      const brandCultureEnabled = display.toggle(home.brandCultureEnabled, false)
      if (sequence !== this.productSequence) return
      const palette = theme.remember(home)
      this.baseProducts = products
      feedback.update(this, { home, products: displayPrices(decorateCampaignProducts(products, campaigns, decoration.layoutTemplate)), campaigns, campaignError, ...palette, ...decoration, brandCultureEnabled, logoFailed: false, error: '' })
      this.startCampaignClock()
      this.loadedOnce = true
      // A slow homepage response must not rename the page the user has since opened.
      if (typeof getCurrentPages === 'function' && getCurrentPages().slice(-1)[0] === this) wx.setNavigationBarTitle({ title: home.brandName || '商城首页' })
    } catch (error) {
      if (sequence !== this.productSequence) return
      if (!silent) feedback.update(this, { error: error.message || '加载失败' })
      else feedback.toast({ title: '装修更新失败，暂保留原页面', icon: 'none' })
    } finally {
      if (sequence === this.productSequence) feedback.update(this, { loading: false })
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
        if (previous.priceText !== product.priceText) {
          patch[`products[${index}].priceText`] = product.priceText
          patch[`products[${index}].priceInteger`] = product.priceText.split('.')[0]
          patch[`products[${index}].priceDecimal`] = product.priceText.split('.')[1]
        }
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
  openNotice(event) { const id = format.identifier(event.currentTarget.dataset.id); wx.navigateTo({ url: id ? `/pages/notices/index?id=${id}` : '/pages/notices/index' }) },
  campaign(event) { const id = format.identifier(event.currentTarget.dataset.id); wx.navigateTo({ url: `/pages/campaign/index${id ? '?id=' + id : ''}` }) },
  allProducts() {
    wx.switchTab({ url: '/pages/category/index', success: () => {
      const pages = getCurrentPages(); const page = pages[pages.length - 1]
      if (page && page.showAll) page.showAll()
    } })
  },
  search() {
    if (this._inactive) return
    const keyword = String(this.data.keyword || '').trim()
    const key = JSON.stringify([keyword, this.data.activeCategory])
    if (this.pendingSearch && this.pendingSearch.key === key) return this.pendingSearch.task
    clearTimeout(this.suggestionsTimer)
    this.setData({ keyword, searchedKeyword: keyword, searchFocused: false, recentSearches: searchHistory.remember(keyword) })
    if (wx.hideKeyboard) wx.hideKeyboard()
    // Match H5 HomeView: submit filters this page; only "all products" changes tabs.
    const pending = { key }
    this.pendingSearch = pending
    pending.task = this.filterProducts(true).finally(() => {
      if (this.pendingSearch === pending) this.pendingSearch = null
    })
    return pending.task
  },
  focusSearch() { clearTimeout(this.suggestionsTimer); this.setData({ searchFocused: true, recentSearches: searchHistory.list() }) },
  blurSearch() { if (!this.clearingHistory) this.suggestionsTimer = setTimeout(() => this.setData({ searchFocused: false }), 150) },
  clearKeyword() { if (this._inactive) return; this.setData({ keyword: '' }); this.focusSearch() },
  clearSearchHistory() {
    if (this._inactive || this.clearingHistory || !this.data.recentSearches.length) return
    clearTimeout(this.suggestionsTimer)
    this.clearingHistory = true
    const sequence = this.historyClearSequence = (this.historyClearSequence || 0) + 1
    const current = () => !this._inactive && sequence === this.historyClearSequence
    wx.showModal({
      title: '清空搜索历史？', content: '将删除本机保存的全部搜索记录，清空后无法恢复。不会影响购物车或账号信息。',
      showCancel: true, confirmText: '清空', cancelText: '取消',
      success: result => {
        if (!current()) return
        try {
          if (result.confirm) this.setData({ recentSearches: searchHistory.clear() })
        } catch (_) { feedback.notice('搜索历史清空失败，本机记录仍然保留，请重试。') }
        finally { this.clearingHistory = false; this.focusSearch() }
      },
      fail: () => {
        if (!current()) return
        this.clearingHistory = false
        this.focusSearch()
        feedback.notice('未能打开清空确认窗口，搜索历史尚未删除，请重试。')
      }
    })
  },
  applySearch(event) { if (this._inactive) return; this.setData({ keyword: String(event.currentTarget.dataset.keyword || ''), activeCategory: '' }); return this.search() },
  clearFilter() { this.setData({ keyword: '', searchedKeyword: '', activeCategory: '' }); return this.filterProducts() },
  async filterProducts(scroll = false) {
    const sequence = this.productSequence = (this.productSequence || 0) + 1
    this.setData({ productsLoading: true, loading: false, productError: '' })
    try {
      const result = await request({ url: '/shop/products', params: { status: 1, pageNum: 1, pageSize: 60, keyword: this.data.searchedKeyword, categoryName: this.data.activeCategory } })
      if (sequence !== this.productSequence) return
      if (!result || !Array.isArray(result.list)) throw new Error('商品列表暂不可用，请重试')
      this.baseProducts = result.list.map(categoryProduct.card)
      this.setData({ products: displayPrices(decorateCampaignProducts(this.baseProducts, this.data.campaigns, this.data.layoutTemplate)) })
      this.startCampaignClock()
      if (scroll && wx.pageScrollTo) wx.pageScrollTo({ selector: '#home-product-section', duration: 200 })
    } catch (error) { if (sequence === this.productSequence) feedback.update(this, { productError: error.message || '商品搜索失败' }) }
    finally { if (sequence === this.productSequence) this.setData({ productsLoading: false }) }
  },
  retryProducts() { return this.filterProducts() },
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
    this.setData({ activeCategory: this.data.activeCategory === name ? '' : name })
    return this.filterProducts(true)
  },
  retry() { this.loadHome() }
})
