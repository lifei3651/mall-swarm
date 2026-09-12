const theme = require('../../utils/theme')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const session = require('../../utils/session')
const format = require('../../utils/format')
const feedback = require('../../utils/feedback')
const support = require('../../utils/support')
Page({
  data: { ...theme.pageData(), ticket: {}, replies: [], content: '', loading: false, error: '', submitting: false, closing: false },
  onLoad(options = {}) { theme.apply(this); this.ticketId = format.identifier(options.id) },
  onShow() {
    this.inactive = false
    if (this.owner !== session.getToken()) { this.owner = session.getToken(); this.requestIdentity = null; this.setData({ ticket: {}, replies: [], content: '' }) }
    if (!this.ticketId) { feedback.update(this, { error: '工单编号不正确' }); return }
    if (!auth.requireLogin(`/pages/support-detail/index?id=${this.ticketId}`)) return
    this.load(); this.schedulePoll()
  },
  onHide() { this.inactive = true; this.generation = (this.generation || 0) + 1; clearTimeout(this.poll); this.setData({ loading: false }) },
  onUnload() { this.onHide() },
  schedulePoll() { clearTimeout(this.poll); if (!this.inactive && this.data.ticket.status !== 'CLOSED') this.poll = setTimeout(async () => { if (!this.inactive) { await this.load(true); this.schedulePoll() } }, 30000) },
  applyDetail(detail) {
    if (format.identifier(detail?.ticket?.id) !== this.ticketId || !Array.isArray(detail.replies)) throw new Error('工单数据不完整，请重新加载')
    this.setData({ ticket: support.decorate(detail.ticket), replies: detail.replies.map(item => ({ ...item, senderClass: ['MEMBER', 'ADMIN', 'SYSTEM'].includes(item.senderType) ? item.senderType.toLowerCase() : 'system', timeText: support.time(item.createTime) })) })
  },
  async load(silent = false) {
    silent = silent === true
    if (this.inactive || !this.ticketId || this.data.submitting || this.data.closing || !auth.requireLogin(`/pages/support-detail/index?id=${this.ticketId}`)) return
    const generation = this.generation = (this.generation || 0) + 1, token = session.getToken()
    const current = () => !this.inactive && token === session.getToken() && generation === this.generation
    if (!silent) this.setData({ loading: true, error: '' })
    try { const result = await request({ url: `/shop/service-tickets/${this.ticketId}` }); if (current()) { this.applyDetail(result); this.setData({ error: '' }) } }
    catch (error) { if (current()) { if (silent) this.setData({ error: '工单刷新失败，请点击重新加载。已输入的回复已保留。' }); else feedback.update(this, { error: error.message || '工单加载失败' }) } }
    finally { if (current()) this.setData({ loading: false }) }
  },
  input(event) { if (!this.data.submitting && !this.data.closing) this.setData({ content: String(event.detail.value || '').slice(0,1000) }) },
  openOrder() { const id = format.identifier(this.data.ticket.orderId); if (id) wx.navigateTo({ url: `/pages/order-detail/index?id=${id}` }) },
  async reply() {
    if (this.inactive || this.data.submitting || this.data.closing || !this.data.ticket.id || this.data.ticket.status === 'CLOSED' || !auth.requireLogin(`/pages/support-detail/index?id=${this.ticketId}`)) return
    const content = this.data.content.trim()
    if (!content) { await feedback.notice('请填写回复内容'); return }
    const token = session.getToken(), current = () => !this.inactive && token === session.getToken()
    const idempotencyKey = support.keyFor(this, 'ticket-reply', { id: this.ticketId, content }, token)
    this.generation = (this.generation || 0) + 1; this.setData({ submitting: true, error: '' })
    try {
      const result = await request({ url: `/shop/service-tickets/${this.ticketId}/replies`, method: 'POST', data: { content }, idempotencyKey })
      if (!current()) return
      this.applyDetail(result); this.requestIdentity = null; this.setData({ content: '' })
      await feedback.success('回复已发送')
    } catch (error) { if (current()) await feedback.notice(error.message || '回复未能发送，内容已保留，请重试') }
    finally { this.setData({ submitting: false }) }
  },
  async closeTicket() {
    if (this.inactive || this.data.submitting || this.data.closing || !this.data.ticket.id || this.data.ticket.status === 'CLOSED') return
    const token = session.getToken(), current = () => !this.inactive && token === session.getToken()
    this.setData({ closing: true })
    try {
      const confirm = await new Promise(resolve => wx.showModal({ title: '确认关闭工单？', content: '关闭后不能继续回复；有新问题时可以重新提交工单。', confirmText: '确认关闭', success: result => resolve(result.confirm), fail: () => resolve(false) }))
      if (!confirm || !current()) return
      this.generation = (this.generation || 0) + 1
      const result = await request({ url: `/shop/service-tickets/${this.ticketId}/close`, method: 'PUT' })
      if (!current()) return
      this.applyDetail(result); clearTimeout(this.poll); await feedback.success('工单已关闭')
    } catch (error) { if (current()) await feedback.notice(error.message || '工单关闭失败，请重试') }
    finally { this.setData({ closing: false }) }
  }
})
