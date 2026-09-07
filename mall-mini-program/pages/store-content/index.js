const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const theme = require('../../utils/theme')
const display = require('../../utils/display-config')
const format = require('../../utils/format')
const live = require('../../utils/live')
const share = require('../../utils/share')
const session = require('../../utils/session')

Page({
  data: { ...theme.pageData(), loading: true, error: '', type: '', products: [], rooms: [], filteredRooms: [], liveTab: 'live', reservedIds: [], reservationReady: false, reservingId: '', comments: [], commentText: '', commentSaving: false, commentError: '', commentScroll: 0, shareReady: false, culture: {}, room: null, videoUrl: '', videoFailed: false },
  onLoad(options) {
    this.contentType = ['culture', 'newArrivals', 'live'].includes(options.type) ? options.type : ''
    this.roomId = format.identifier(options.id)
    this.setData({ liveTab: options.tab === 'upcoming' ? 'upcoming' : 'live' })
  },
  onShow() { this.hidden = false; this.disposed = false; return this.load() },
  onHide() { this.hidden = true; this.sequence = (this.sequence || 0) + 1; live.stop(this); this.setData({ comments: [], commentText: '', reservedIds: [], reservationReady: false, shareReady: false }) },
  onUnload() { this.onHide(); this.disposed = true },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()) },
  async load() {
    live.stop(this)
    const token = session.getToken(), sequence = this.sequence = (this.sequence || 0) + 1
    const current = () => !this.hidden && !this.disposed && sequence === this.sequence && token === session.getToken()
    feedback.update(this, { loading: true, error: '', videoFailed: false })
    try {
      if (!this.contentType) throw new Error('页面不存在')
      const home = await request({ url: '/shop/home' })
      if (!current()) return
      const config = home.displayConfig || {}
      const ext = display.extra(config)
      const enabled = this.contentType === 'culture' ? display.toggle(home.brandCultureEnabled, false) : this.contentType === 'live' ? display.toggle(config.liveSquareEnabled ?? ext.liveSquareEnabled) : display.toggle(config.newArrivalsEnabled ?? ext.newArrivalsEnabled)
      feedback.update(this, { ...theme.remember(home), type: this.contentType })
      if (!enabled) throw new Error('该页面暂未开放')
      wx.setNavigationBarTitle({ title: { culture: '品牌文化', newArrivals: '新品速递', live: '直播广场' }[this.contentType] })
      if (this.contentType === 'culture') {
        const culture = await request({ url: '/shop/brand-culture' })
        if (!current()) return
        if (!display.toggle(culture.enabled, false)) throw new Error('品牌文化页暂未开放')
        for (const key of ['title', 'subtitle', 'content', 'brandName']) culture[key] = String(culture[key] || '')
        culture.coverUrl = format.mediaUrl(culture.coverUrl)
        culture.detailImages = (culture.detailImages || []).map((url) => ({ url: format.mediaUrl(url), failed: false }))
        feedback.update(this, { culture })
      } else if (this.contentType === 'newArrivals') {
        const products = await request({ url: '/shop/new-arrivals', params: { limit: 60 } })
        if (!current()) return
        feedback.update(this, { products: (products || []).map(format.product) })
      } else if (this.roomId) {
        const room = live.room(await request({ url: `/shop/live-rooms/${this.roomId}` }))
        if (!current()) return
        if (!room || room.room.id !== String(this.roomId)) throw new Error('直播间不存在或已关闭')
        const url = String(room.room && room.room.watchUrl || '')
        // 不将任意后台链接当成网页执行，仅交给原生播放器播放 HTTPS 视频流。
        const videoUrl = ['LIVE', 'ENDED'].includes(room.roomState) && /^https:\/\/[^\s]+\.(m3u8|mp4)(?:[?#][^\s]*)?$/i.test(url) ? url : ''
        feedback.update(this, { room, videoUrl, products: (room.products || []).map(format.product) })
        wx.setNavigationBarTitle({ title: room.room && room.room.title || '直播间' })
        live.start(this)
      } else {
        const rooms = await request({ url: '/shop/live-rooms', params: { limit: 50 } })
        if (!current()) return
        feedback.update(this, { rooms: (rooms || []).map(live.room).filter(Boolean) })
        live.renderRooms(this); await live.reservations(this)
      }
    } catch (error) { if (current()) { live.stop(this); feedback.update(this, { error: error.message || '内容加载失败', room: null, products: [], rooms: [] }) } }
    finally { if (current()) feedback.update(this, { loading: false }) }
  },
  selectLiveTab(event) { this.setData({ liveTab: event.currentTarget.dataset.tab === 'upcoming' ? 'upcoming' : 'live' }); live.renderRooms(this) },
  reserve(event) { return live.toggleReservation(this, event.currentTarget.dataset.id) },
  commentInput(event) { this.setData({ commentText: String(event.detail.value || '').slice(0, 300) }) },
  sendComment() { return live.sendComment(this) },
  retryComments() { return live.comments(this) },
  onShareAppMessage() {
    if (!this.data.room || this.data.room.room.shareEnabled !== 1 || !this.data.shareReady || this.hidden) return { title: '商城', path: '/pages/home/index' }
    live.event(this, 'SHARE')
    return share.message(this, `/pages/store-content/index?type=live&id=${this.roomId}`, this.data.room.room.title)
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
  openRoom(event) { const id = format.identifier(event.currentTarget.dataset.id); if (id) wx.navigateTo({ url: `/pages/store-content/index?type=live&id=${id}` }) },
  openProduct(event) { const id = format.identifier(event.currentTarget.dataset.id); if (!id || (this.roomId && !this.data.products.some(item => String(item.id) === id))) return; if (this.roomId) live.event(this, 'PRODUCT_CLICK', id); wx.navigateTo({ url: `/pages/product/index?id=${id}` }) },
  retry() { this.load() }
})
