const feedback = require('../../utils/feedback')
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
  onShow() {
    this.hidden = false; theme.apply(this)
    if (this.owner !== session.getToken()) this.setData({ templates: [], groups: [], requesting: false, error: '' })
    if (this.pendingGrant && this.pendingGrantToken === session.getToken()) return this.syncGrant()
    if (!this.data.requesting) return this.load()
  },
  onHide() { this.hidden = true; this.loadVersion = (this.loadVersion || 0) + 1; this.setData({ templates: [], groups: [] }) },
  onUnload() { this.disposed = true; this.loadVersion = (this.loadVersion || 0) + 1; this.pendingGrant = null; this.pendingGrantToken = null },
  async load() {
    const version = this.loadVersion = (this.loadVersion || 0) + 1
    const token = this.owner = session.getToken()
    const current = () => !this.hidden && !this.disposed && token && session.getToken() === token && version === this.loadVersion
    if (!auth.requireLogin('/pages/subscriptions/index')) {
      this.pendingGrant = null; this.pendingGrantToken = null
      feedback.update(this, { loading: false, templates: [], groups: [], pendingGrant: false })
      return
    }
    if (this.pendingGrant && this.pendingGrantToken !== session.getToken()) {
      this.pendingGrant = null; this.pendingGrantToken = null; feedback.update(this, { pendingGrant: false })
    }
    feedback.update(this, { loading: true, error: '' })
    try {
      const templates = await request({ url: '/shop/wechat-mini-program/subscriptions' })
      if (!current()) return
      feedback.update(this, { templates: normalizeTemplates(templates), groups: subscriptionGroups(templates) })
    } catch (error) { if (current()) feedback.update(this, { templates: [], groups: [], error: error.message || '提醒设置加载失败' }) }
    finally { if (current()) feedback.update(this, { loading: false }) }
  },
  async subscribe(event) {
    if (this.hidden || this.disposed || this.data.loading || this.data.requesting || !this.data.templates.length) return
    if (this.pendingGrant) { await this.syncGrant(); return }
    const token = session.getToken()
    if (!token || this.owner !== token) { await this.load(); return }
    if (!wx.requestSubscribeMessage) {
      wx.showModal({ title: '当前微信版本暂不支持', content: '请升级微信后再设置提醒。', showCancel: false })
      return
    }
    const group = this.data.groups.find((item) => item.key === String(event && event.currentTarget && event.currentTarget.dataset.group || '0'))
    const templateIds = group ? group.templateIds.slice(0, 3) : []
    if (!templateIds.length) return
    feedback.update(this, { requesting: true })
    try {
      const result = await requestSubscribeMessage(templateIds)
      if (this.disposed || session.getToken() !== token) return
      const acceptedTemplateIds = templateIds.filter((id) => result[id] === 'accept')
      if (!acceptedTemplateIds.length) {
        if (!this.hidden) feedback.toast({ title: '本次未开启提醒', icon: 'none' })
        return
      }
      this.pendingGrant = { requestId: requestId(), acceptedTemplateIds }
      this.pendingGrantToken = token
      feedback.update(this, { pendingGrant: true })
      if (!this.hidden) await this.syncGrant()
    } catch (error) {
      if (this.disposed || this.hidden || session.getToken() !== token) return
      const cancelled = /cancel/i.test(String(error && (error.errMsg || error.message || error)))
      feedback.toast({ title: cancelled ? '已取消设置' : (error.message || '提醒设置失败'), icon: 'none' })
    } finally {
      if (!this.disposed && session.getToken() === token) {
        this.setData({ requesting: false })
        // Native authorization may hide/show the page before returning a rejection.
        // Restore the cleared choices without opening another authorization prompt.
        if (!this.hidden && !this.pendingGrant && !this.data.templates.length) await this.load()
      }
    }
  },
  async syncGrant() {
    if (this.hidden || this.disposed || !this.pendingGrant || this.syncingGrant) return
    if (session.getToken() !== this.pendingGrantToken) {
      this.pendingGrant = null; this.pendingGrantToken = null
      feedback.update(this, { pendingGrant: false })
      feedback.toast({ title: '登录状态已变化，请重新设置提醒', icon: 'none' })
      await this.load(); return
    }
    this.syncingGrant = true
    const token = this.pendingGrantToken, grant = this.pendingGrant
    const current = () => !this.disposed && token === session.getToken() && this.pendingGrant === grant
    feedback.update(this, { requesting: true })
    try {
      const templates = await request({ url: '/shop/wechat-mini-program/subscriptions/grants', method: 'POST', data: grant })
      if (!current() || this.hidden) return
      this.pendingGrant = null; this.pendingGrantToken = null
      feedback.update(this, { pendingGrant: false, templates: normalizeTemplates(templates), groups: subscriptionGroups(templates) })
      feedback.toast({ title: '本组提醒已开启', icon: 'success' })
    } catch (_) {
      if (current() && !this.hidden) feedback.toast({ title: '授权结果尚未同步，请点击重试', icon: 'none' })
    } finally { this.syncingGrant = false; if (!this.disposed && token === session.getToken()) this.setData({ requesting: false, loading: false }) }
  }
})
