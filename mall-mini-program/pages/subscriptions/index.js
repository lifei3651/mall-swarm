const request = require('../../utils/request')
const auth = require('../../utils/auth')
const session = require('../../utils/session')
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

// wx.requestSubscribeMessage 单次最多 3 个模板；每批都由用户单独点击，不能异步连弹授权。
function subscriptionGroups(templates) {
  const unique = (templates || []).filter((item, index, rows) => item.templateId && rows.findIndex((row) => row.templateId === item.templateId) === index)
  const groups = []
  for (let index = 0; index < unique.length; index += 3) {
    const items = unique.slice(index, index + 3)
    groups.push({ key: String(index / 3), templateIds: items.map((item) => item.templateId), title: items.map((item) => item.title).join('、') })
  }
  return groups
}

Page({
  data: { ...theme.pageData(), loading: true, requesting: false, templates: [], groups: [], pendingGrant: false, error: '' },
  onLoad() { theme.apply(this) },
  onShow() { theme.apply(this); if (!this.data.requesting) return this.load() },
  onUnload() { this.disposed = true; this.loadVersion = (this.loadVersion || 0) + 1; this.pendingGrant = null; this.pendingGrantToken = null },
  async load() {
    const version = this.loadVersion = (this.loadVersion || 0) + 1
    if (!auth.requireLogin('/pages/subscriptions/index')) {
      this.pendingGrant = null; this.pendingGrantToken = null
      this.setData({ loading: false, templates: [], groups: [], pendingGrant: false })
      return
    }
    if (this.pendingGrant && this.pendingGrantToken !== session.getToken()) {
      this.pendingGrant = null; this.pendingGrantToken = null; this.setData({ pendingGrant: false })
    }
    this.setData({ loading: true, error: '' })
    try {
      const templates = await request({ url: '/shop/wechat-mini-program/subscriptions' })
      if (this.disposed || version !== this.loadVersion) return
      this.setData({ templates: normalizeTemplates(templates), groups: subscriptionGroups(templates) })
    } catch (error) { if (!this.disposed && version === this.loadVersion) this.setData({ templates: [], groups: [], error: error.message || '提醒设置加载失败' }) }
    finally { if (!this.disposed && version === this.loadVersion) this.setData({ loading: false }) }
  },
  async subscribe(event) {
    if (this.disposed || this.data.loading || this.data.requesting || !this.data.templates.length) return
    if (this.pendingGrant) { await this.syncGrant(); return }
    const token = session.getToken()
    if (!token) { await this.load(); return }
    if (!wx.requestSubscribeMessage) {
      wx.showModal({ title: '当前微信版本暂不支持', content: '请升级微信后再设置提醒。', showCancel: false })
      return
    }
    const group = this.data.groups.find((item) => item.key === String(event && event.currentTarget && event.currentTarget.dataset.group || '0'))
    const templateIds = group ? group.templateIds.slice(0, 3) : []
    if (!templateIds.length) return
    this.setData({ requesting: true })
    try {
      const result = await requestSubscribeMessage(templateIds)
      if (this.disposed || session.getToken() !== token) return
      const acceptedTemplateIds = templateIds.filter((id) => result[id] === 'accept')
      if (!acceptedTemplateIds.length) {
        wx.showToast({ title: '本次未开启提醒', icon: 'none' })
        return
      }
      this.pendingGrant = { requestId: requestId(), acceptedTemplateIds }
      this.pendingGrantToken = token
      this.setData({ pendingGrant: true })
      await this.syncGrant()
    } catch (error) {
      if (this.disposed) return
      const cancelled = /cancel/i.test(String(error && (error.errMsg || error.message || error)))
      wx.showToast({ title: cancelled ? '已取消设置' : (error.message || '提醒设置失败'), icon: 'none' })
    } finally { if (!this.disposed) this.setData({ requesting: false }) }
  },
  async syncGrant() {
    if (this.disposed || !this.pendingGrant || this.syncingGrant) return
    if (session.getToken() !== this.pendingGrantToken) {
      this.pendingGrant = null; this.pendingGrantToken = null
      this.setData({ pendingGrant: false })
      wx.showToast({ title: '登录状态已变化，请重新设置提醒', icon: 'none' })
      await this.load(); return
    }
    this.syncingGrant = true
    this.setData({ requesting: true })
    try {
      const templates = await request({ url: '/shop/wechat-mini-program/subscriptions/grants', method: 'POST', data: this.pendingGrant })
      if (this.disposed) return
      this.pendingGrant = null; this.pendingGrantToken = null
      this.setData({ pendingGrant: false, templates: normalizeTemplates(templates), groups: subscriptionGroups(templates) })
      wx.showToast({ title: '本组提醒已开启', icon: 'success' })
    } catch (_) {
      if (!this.disposed) wx.showToast({ title: '授权结果尚未同步，请点击重试', icon: 'none' })
    } finally { this.syncingGrant = false; if (!this.disposed) this.setData({ requesting: false }) }
  }
})
