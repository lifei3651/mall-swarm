const theme = require('../../utils/theme')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const session = require('../../utils/session')
const format = require('../../utils/format')
const feedback = require('../../utils/feedback')
const support = require('../../utils/support')
const emptyForm = () => ({ type: 'CONSULTATION', subject: '', content: '', orderId: '', afterSaleId: '' })
Page({
  data: { ...theme.pageData(), types: support.types, filters: support.filters, status: '', rows: [], loading: false, error: '', pageNum: 0, totalPage: 1, legal: {}, orders: [], orderChoices: [{ label: '不关联订单', id: '' }], afterSaleChoices: [{ label: '不关联售后', id: '' }], contextError: '', creating: false, submitting: false, form: emptyForm(), typeIndex: 0, orderIndex: 0, afterSaleIndex: 0 },
  onLoad(options = {}) { theme.apply(this); this.options = options },
  onShow() {
    this.inactive = false
    if (!auth.requireLogin('/pages/support/index')) return
    const token = session.getToken()
    if (this.owner !== token) { this.owner = token; this.requestIdentity = null; this.setData({ rows: [], form: emptyForm(), creating: false, orders: [] }) }
    this.load(true); this.loadContext()
    if (!this.opened && (this.options?.create || this.options?.orderId || this.options?.afterSaleId)) { this.opened = true; this.openCreate() }
  },
  onHide() { this.inactive = true; this.generation = (this.generation || 0) + 1; this.contextGeneration = (this.contextGeneration || 0) + 1; this.setData({ loading: false }) },
  onUnload() { this.onHide() },
  async load(reset = true) {
    reset = reset !== false
    if (this.inactive || (!reset && this.data.loading) || !auth.requireLogin('/pages/support/index')) return
    const generation = this.generation = (this.generation || 0) + 1, token = session.getToken()
    const current = () => !this.inactive && token === session.getToken() && generation === this.generation
    const pageNum = reset ? 1 : this.data.pageNum + 1
    this.setData({ loading: true, error: '' })
    try {
      const page = await request({ url: '/shop/service-tickets', params: { status: this.data.status || undefined, pageNum, pageSize: 20 } })
      if (!current()) return
      if (!page || !Array.isArray(page.list)) throw new Error('工单数据暂不可用，请重试')
      const incoming = page.list.filter(item => format.identifier(item.id)).map(support.decorate)
      this.setData({ rows: reset ? incoming : this.data.rows.concat(incoming), pageNum, totalPage: Number(page.totalPage || 1) })
    } catch (error) { if (current()) feedback.update(this, { error: error.message || '客服工单加载失败' }) }
    finally { if (current()) this.setData({ loading: false }) }
  },
  loadMore() { if (this.data.pageNum < this.data.totalPage) return this.load(false) },
  selectStatus(event) { const status = event.currentTarget.dataset.key; if (!support.filters.some(item => item.key === status)) return; this.setData({ status, rows: [] }); this.load(true) },
  async loadContext() {
    this.setData({ contextLoading: true })
    const token = session.getToken(), generation = this.contextGeneration = (this.contextGeneration || 0) + 1
    const current = () => !this.inactive && token === session.getToken() && generation === this.contextGeneration
    const results = await Promise.allSettled([request({ url: '/shop/legal-config' }), request({ url: '/shop/orders', params: { pageNum: 1, pageSize: 50 } })])
    if (!current()) return
    const orders = results[1].status === 'fulfilled' && Array.isArray(results[1].value?.list) ? results[1].value.list.filter(item => format.identifier(item.order?.id)) : []
    const selectedId = this.data.form.orderId
    if (selectedId && !orders.some(item => String(item.order.id) === selectedId)) {
      try { const detail = await request({ url: `/shop/orders/${selectedId}` }); if (detail?.order && format.identifier(detail.order.id) === selectedId) orders.push(detail) } catch (_) {}
    }
    if (!current()) return
    this.setData({ legal: results[0].status === 'fulfilled' ? results[0].value || {} : {}, orders, contextError: results[1].status === 'rejected' ? '关联订单加载失败，可重试；咨询和账号问题仍可提交。' : '', orderChoices: [{ label: '不关联订单', id: '' }, ...orders.map(item => ({ id: String(item.order.id), label: `${item.order.orderNo} · ${item.items?.[0]?.productName || '商城订单'}` }))] })
    this.syncSelections()
    this.setData({ contextLoading: false })
  },
  syncSelections() {
    const form = { ...this.data.form }, orderIndex = this.data.orderChoices.findIndex(item => item.id === form.orderId)
    if (orderIndex < 0) { form.orderId = ''; form.afterSaleId = '' }
    const selected = this.data.orders.find(item => String(item.order.id) === form.orderId)
    const afterSaleChoices = [{ label: '不关联售后', id: '' }, ...(selected?.afterSales || []).filter(item => format.identifier(item.id)).map(item => ({ id: String(item.id), label: item.afterSaleNo || String(item.id) }))]
    let afterSaleIndex = afterSaleChoices.findIndex(item => item.id === form.afterSaleId)
    if (afterSaleIndex < 0) { form.afterSaleId = ''; afterSaleIndex = 0 }
    this.setData({ form, orderIndex: Math.max(0, orderIndex), afterSaleChoices, afterSaleIndex })
  },
  openCreate() {
    if (this.data.submitting) return
    const options = this.options || {}, form = { ...this.data.form }
    if (format.identifier(options.orderId)) form.orderId = format.identifier(options.orderId)
    if (format.identifier(options.afterSaleId)) form.afterSaleId = format.identifier(options.afterSaleId)
    if (support.types.some(item => item.key === options.type)) form.type = options.type
    if (typeof options.subject === 'string') form.subject = options.subject.slice(0,100)
    this.setData({ creating: true, form, typeIndex: support.types.findIndex(item => item.key === form.type) })
    if (typeof wx.pageScrollTo === 'function') wx.pageScrollTo({ scrollTop: 0 })
  },
  cancelCreate() { if (!this.data.submitting) this.setData({ creating: false }) },
  input(event) { const field = event.currentTarget.dataset.field; if (!this.data.submitting && ['subject', 'content'].includes(field)) this.setData({ [`form.${field}`]: String(event.detail.value || '').slice(0, field === 'subject' ? 100 : 1000) }) },
  chooseType(event) { if (this.data.submitting) return; const index = Number(event.detail.value), item = support.types[index]; if (!item) return; this.setData({ typeIndex: index, 'form.type': item.key, ...(item.key === 'ACCOUNT' ? { 'form.orderId': '', 'form.afterSaleId': '' } : {}) }); this.syncSelections() },
  chooseOrder(event) { if (this.data.submitting) return; const item = this.data.orderChoices[Number(event.detail.value)]; if (item) { this.setData({ 'form.orderId': item.id, 'form.afterSaleId': '' }); this.syncSelections() } },
  chooseAfterSale(event) { if (this.data.submitting) return; const index = Number(event.detail.value), item = this.data.afterSaleChoices[index]; if (item) this.setData({ afterSaleIndex: index, 'form.afterSaleId': item.id }) },
  openTicket(event) { const id = format.identifier(event.currentTarget.dataset.id); if (id) wx.navigateTo({ url: `/pages/support-detail/index?id=${id}` }) },
  contact() { wx.navigateTo({ url: '/pages/legal/index?type=contact' }) },
  async submit() {
    if (this.data.submitting || this.inactive || !auth.requireLogin('/pages/support/index')) return
    const form = this.data.form, payload = { type: form.type, subject: form.subject.trim(), content: form.content.trim(), orderId: format.identifier(form.orderId) || null, afterSaleId: format.identifier(form.afterSaleId) || null }
    if (!payload.subject || !payload.content || (payload.type === 'AFTER_SALE_DISPUTE' && !payload.afterSaleId)) { await feedback.notice(!payload.subject ? '请填写问题标题' : !payload.content ? '请填写问题说明' : '售后争议必须选择一条售后记录'); return }
    const token = session.getToken(), current = () => !this.inactive && token === session.getToken()
    const idempotencyKey = support.keyFor(this, 'ticket-create', payload, token)
    this.setData({ submitting: true })
    try {
      const result = await request({ url: '/shop/service-tickets', method: 'POST', data: payload, idempotencyKey })
      if (!current()) return
      const id = format.identifier(result?.ticket?.id)
      if (!id) throw new Error('提交结果待确认，请先查看工单列表，不要反复提交。')
      this.requestIdentity = null; this.setData({ form: emptyForm(), creating: false })
      wx.navigateTo({ url: `/pages/support-detail/index?id=${id}` })
    } catch (error) { if (current()) await feedback.notice(error.message || '提交失败，内容已保留，请重试', '工单未能提交') }
    finally { this.setData({ submitting: false }) }
  }
})
