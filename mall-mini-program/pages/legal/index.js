const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const theme = require('../../utils/theme')
const legal = require('../../utils/legal')
const format = require('../../utils/format')
Page({
  data: { ...theme.pageData(), type: '', loading: true, error: '', config: {}, content: '', miniPrivacy: legal.miniPrivacy, faqs: [], entries: Object.entries(legal.titles).map(([type, title]) => ({ type, title })) },
  onLoad(options = {}) {
    theme.apply(this)
    const type = Object.hasOwnProperty.call(legal.titles, options.type) ? options.type : ''
    feedback.update(this, { type })
    wx.setNavigationBarTitle({ title: legal.titles[type] || '商城说明' })
    this.load()
  },
  async load() {
    feedback.update(this, { loading: true, error: '' })
    try {
      const config = await request({ url: '/shop/legal-config' }) || {}
      config.servicePhone = legal.contactValue(config.servicePhone)
      config.serviceEmail = legal.contactValue(config.serviceEmail)
      const url = format.mediaUrl(config.businessLicenseUrl)
      config.businessLicenseUrl = /^https:\/\//i.test(url) ? url : ''
      feedback.update(this, { config, content: legal.content(this.data.type, config), faqs: legal.faqs(config) })
    } catch (error) { feedback.update(this, { error: error.message || '商城说明加载失败' }) }
    finally { feedback.update(this, { loading: false }) }
  },
  open(event) { const type = event.currentTarget.dataset.type; if (Object.hasOwnProperty.call(legal.titles, type)) wx.navigateTo({ url: `/pages/legal/index?type=${type}` }) }
})
