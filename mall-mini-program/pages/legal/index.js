const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const theme = require('../../utils/theme')
const legal = require('../../utils/legal')
const format = require('../../utils/format')
Page({
  data: { ...theme.pageData(), type: '', loading: true, error: '', contactError: '', config: {}, content: '', miniPrivacy: legal.miniPrivacy, faqs: [], entries: Object.entries(legal.titles).map(([type, title]) => ({ type, title })) },
  contactError(event) {
    if (this.hidden || /cancel/i.test(event?.detail?.errMsg || '')) return
    this.setData({ contactError: '微信客服暂时无法打开，可提交客服工单，或使用下方联系方式。' })
    return feedback.notice('微信客服暂时无法打开，可提交客服工单，或使用页面上的其他联系方式。')
  },
  contactStart() { this.setData({ contactError: '' }) },
  tickets() { wx.navigateTo({ url: '/pages/support/index', fail: () => feedback.notice('客服工单暂时无法打开，请稍后重试') }) },
  callPhone() {
    const phoneNumber = legal.contactValue(this.data.config.servicePhone)
    if (!phoneNumber) return
    wx.makePhoneCall({ phoneNumber, fail: error => { if (!/cancel/i.test(error?.errMsg || '')) feedback.notice('电话暂时无法拨出，请长按号码复制后拨打') } })
  },
  copyEmail() {
    const data = legal.contactValue(this.data.config.serviceEmail)
    if (data) wx.setClipboardData({ data, fail: () => feedback.notice('邮箱复制失败，请长按邮箱复制') })
  },
  onLoad(options = {}) {
    theme.apply(this)
    const type = Object.hasOwnProperty.call(legal.titles, options.type) ? options.type : ''
    feedback.update(this, { type })
    wx.setNavigationBarTitle({ title: legal.titles[type] || '商城说明' })
    this.load()
  },
  onShow() { this.hidden = false; if (this.reloadNeeded) { this.reloadNeeded = false; return this.load() } },
  onHide() { this.hidden = true; this.reloadNeeded = true; this.version = (this.version || 0) + 1 },
  onUnload() { this.onHide() },
  async load() {
    const version = this.version = (this.version || 0) + 1
    const current = () => !this.hidden && version === this.version
    feedback.update(this, { loading: true, error: '' })
    try {
      const config = await request({ url: '/shop/legal-config' }) || {}
      if (!current()) return
      config.servicePhone = legal.contactValue(config.servicePhone)
      config.serviceEmail = legal.contactValue(config.serviceEmail)
      const url = format.mediaUrl(config.businessLicenseUrl)
      config.businessLicenseUrl = /^https:\/\//i.test(url) ? url : ''
      feedback.update(this, { config, content: legal.content(this.data.type, config), faqs: legal.faqs(config) })
    } catch (error) { if (current()) feedback.update(this, { error: error.message || '商城说明加载失败' }) }
    finally { if (current()) feedback.update(this, { loading: false }) }
  },
  open(event) { const type = event.currentTarget.dataset.type; if (Object.hasOwnProperty.call(legal.titles, type)) wx.navigateTo({ url: `/pages/legal/index?type=${type}` }) }
})
