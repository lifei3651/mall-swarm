const request = require('../../utils/request')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')

function requestSubscribeMessage(templateIds) {
  return new Promise((resolve, reject) => {
    wx.requestSubscribeMessage({ tmplIds: templateIds, success: resolve, fail: reject })
  })
}

function requestId() {
  return `sub_${Date.now()}_${Math.random().toString(36).slice(2, 12)}`
}

function normalizeTemplates(templates) {
  return (templates || []).map((item) => {
    const availableGrants = Math.max(0, Number(item.availableGrants || 0))
    return {
      ...item,
      availableGrants,
      grantText: availableGrants ? `剩余 ${availableGrants} 次提醒` : '授权后可接收一次提醒'
    }
  })
}

Page({
  data: { ...theme.pageData(), loading: true, requesting: false, templates: [], error: '' },
  onLoad() { theme.apply(this); if (auth.requireLogin('/pages/subscriptions/index')) this.load() },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const templates = await request({ url: '/shop/wechat-mini-program/subscriptions' })
      this.setData({ templates: normalizeTemplates(templates) })
    } catch (error) { this.setData({ error: error.message || '提醒设置加载失败' }) }
    finally { this.setData({ loading: false }) }
  },
  async subscribe() {
    if (this.data.requesting || !this.data.templates.length) return
    if (!wx.requestSubscribeMessage) {
      wx.showModal({ title: '当前微信版本暂不支持', content: '请升级微信后再设置提醒。', showCancel: false })
      return
    }
    const templateIds = this.data.templates.map((item) => item.templateId).filter(Boolean).slice(0, 5)
    this.setData({ requesting: true })
    try {
      const result = await requestSubscribeMessage(templateIds)
      const acceptedTemplateIds = templateIds.filter((id) => result[id] === 'accept')
      if (!acceptedTemplateIds.length) {
        wx.showToast({ title: '本次未开启提醒', icon: 'none' })
        return
      }
      const templates = await request({
        url: '/shop/wechat-mini-program/subscriptions/grants',
        method: 'POST',
        data: { requestId: requestId(), acceptedTemplateIds }
      })
      this.setData({ templates: normalizeTemplates(templates) })
      wx.showToast({ title: '提醒已开启', icon: 'success' })
    } catch (error) {
      const cancelled = /cancel/i.test(String(error && (error.errMsg || error.message || error)))
      wx.showToast({ title: cancelled ? '已取消设置' : (error.message || '提醒设置失败'), icon: 'none' })
    } finally { this.setData({ requesting: false }) }
  }
})
