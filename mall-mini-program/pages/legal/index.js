const request = require('../../utils/request')
const theme = require('../../utils/theme')
const legal = require('../../utils/legal')
const format = require('../../utils/format')
Page({
  data: { ...theme.pageData(), type: '', loading: true, error: '', config: {}, content: '', faqs: [], entries: Object.entries(legal.titles).map(([type, title]) => ({ type, title })) },
  onLoad(options = {}) {
    theme.apply(this)
    const type = Object.hasOwnProperty.call(legal.titles, options.type) ? options.type : ''
    this.setData({ type })
    wx.setNavigationBarTitle({ title: legal.titles[type] || '商城说明' })
    this.load()
  },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const config = await request({ url: '/shop/legal-config' }) || {}
      const url = format.mediaUrl(config.businessLicenseUrl)
      config.businessLicenseUrl = /^https:\/\//i.test(url) ? url : ''
      this.setData({ config, content: legal.content(this.data.type, config), faqs: legal.faqs(config) })
    } catch (error) { this.setData({ error: error.message || '商城说明加载失败' }) }
    finally { this.setData({ loading: false }) }
  },
  open(event) { const type = event.currentTarget.dataset.type; if (Object.hasOwnProperty.call(legal.titles, type)) wx.navigateTo({ url: `/pages/legal/index?type=${type}` }) }
})
