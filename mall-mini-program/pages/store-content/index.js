const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const theme = require('../../utils/theme')
const display = require('../../utils/display-config')
const format = require('../../utils/format')

Page({
  data: { ...theme.pageData(), loading: true, error: '', type: '', products: [], rooms: [], culture: {}, room: null, videoUrl: '', videoFailed: false },
  onLoad(options) {
    this.contentType = ['culture', 'newArrivals', 'live'].includes(options.type) ? options.type : ''
    this.roomId = /^\d+$/.test(String(options.id || '')) ? options.id : ''
    this.load()
  },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  async load() {
    feedback.update(this, { loading: true, error: '', videoFailed: false })
    try {
      if (!this.contentType) throw new Error('页面不存在')
      const home = await request({ url: '/shop/home' })
      const config = home.displayConfig || {}
      const ext = display.extra(config)
      const enabled = this.contentType === 'culture' ? display.toggle(home.brandCultureEnabled, false) : this.contentType === 'live' ? display.toggle(config.liveSquareEnabled ?? ext.liveSquareEnabled) : display.toggle(config.newArrivalsEnabled ?? ext.newArrivalsEnabled)
      feedback.update(this, { ...theme.remember(home), type: this.contentType })
      if (!enabled) throw new Error('该页面暂未开放')
      wx.setNavigationBarTitle({ title: { culture: '品牌文化', newArrivals: '新品速递', live: '直播广场' }[this.contentType] })
      if (this.contentType === 'culture') {
        const culture = await request({ url: '/shop/brand-culture' })
        if (!display.toggle(culture.enabled, false)) throw new Error('品牌文化页暂未开放')
        for (const key of ['title', 'subtitle', 'content', 'brandName']) culture[key] = String(culture[key] || '')
        culture.coverUrl = format.mediaUrl(culture.coverUrl)
        culture.detailImages = (culture.detailImages || []).map((url) => ({ url: format.mediaUrl(url), failed: false }))
        feedback.update(this, { culture })
      } else if (this.contentType === 'newArrivals') {
        const products = await request({ url: '/shop/new-arrivals', params: { limit: 60 } })
        feedback.update(this, { products: (products || []).map(format.product) })
      } else if (this.roomId) {
        const room = await request({ url: `/shop/live-rooms/${this.roomId}` })
        const url = String(room.room && room.room.watchUrl || '')
        // 不将任意后台链接当成网页执行，仅交给原生播放器播放 HTTPS 视频流。
        const videoUrl = ['LIVE', 'ENDED'].includes(room.roomState) && /^https:\/\/[^\s]+\.(m3u8|mp4)(?:[?#][^\s]*)?$/i.test(url) ? url : ''
        feedback.update(this, { room, videoUrl, products: (room.products || []).map(format.product) })
        wx.setNavigationBarTitle({ title: room.room && room.room.title || '直播间' })
      } else {
        const rooms = await request({ url: '/shop/live-rooms', params: { limit: 40 } })
        feedback.update(this, { rooms: (rooms || []).filter((item) => item.room).map((item) => ({ ...item, room: { ...item.room, coverUrl: format.mediaUrl(item.room.coverUrl) } })) })
      }
    } catch (error) { feedback.update(this, { error: error.message || '内容加载失败' }) }
    finally { feedback.update(this, { loading: false }) }
  },
  productImageError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (Number.isInteger(index) && this.data.products[index]) feedback.update(this, { [`products[${index}].imageFailed`]: true })
  },
  detailImageError(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (Number.isInteger(index) && this.data.culture.detailImages[index]) feedback.update(this, { [`culture.detailImages[${index}].failed`]: true })
  },
  videoError() { feedback.update(this, { videoFailed: true }) },
  openRoom(event) { wx.navigateTo({ url: `/pages/store-content/index?type=live&id=${event.currentTarget.dataset.id}` }) },
  openProduct(event) { wx.navigateTo({ url: `/pages/product/index?id=${event.currentTarget.dataset.id}` }) },
  retry() { this.load() }
})
