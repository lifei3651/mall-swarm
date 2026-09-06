const request = require('../../utils/request')
const auth = require('../../utils/auth')
const session = require('../../utils/session')
const theme = require('../../utils/theme')
const format = require('../../utils/format')
const feedback = require('../../utils/feedback')
const emptyForm = () => ({ withdrawAmount: '', accountName: '', paymentPassword: '', smsCode: '' })
function blockReason(summary) {
  if (!summary.distributionActivated) return '当前账号尚未开通余额提现资格，请联系商城客服核对会员状态。'
  if (!summary.realNameVerified || !summary.adultVerified) return '提现需要完成实名及成年校验。请先在商城H5的账号安全中完成实名认证，或联系客服协助；小程序不能跳过此校验。'
  if (!summary.hasPaymentPassword) return '请先在商城H5的账号安全中设置6位支付密码，再返回刷新。支付密码不是登录密码。'
  if (summary.paymentPasswordLocked) return '支付密码已锁定，请稍后重试；请勿反复尝试密码。'
  return ''
}
Page({
  data: { ...theme.pageData(), loading: true, submitting: false, sendingCode: false, countdown: 0, error: '', balance: '--', blockReason: '', maskedPhone: '', form: emptyForm() },
  onShow() { this.hidden = false; theme.apply(this); if (auth.requireLogin('/pages/withdraw/index')) return this.load() },
  onHide() { this.hidden = true; this.version = (this.version || 0) + 1; clearTimeout(this.timer); this.setData({ form: emptyForm(), maskedPhone: '', balance: '--' }); this.member = null },
  onUnload() { this.disposed = true; this.onHide() },
  async load() {
    if (this.data.submitting) return
    const version = this.version = (this.version || 0) + 1
    feedback.update(this, { loading: true, error: '', blockReason: '', form: emptyForm() })
    try {
      const [summary, member] = await Promise.all([request({ url: '/shop/wallet/summary' }), request({ url: '/shop/auth/me' })])
      if (version !== this.version || this.hidden) return
      if (!summary || summary.balance === null || summary.balance === undefined || !Number.isFinite(Number(summary.balance)) || Number(summary.balance) < 0 || !member) throw new Error('提现资料不完整，请刷新后重试')
      this.member = member; this.available = Number(summary.balance)
      const phone = String(member.phone || '')
      feedback.update(this, { balance: format.money(summary.balance), maskedPhone: /^1[3-9]\d{9}$/.test(phone) ? `${phone.slice(0,3)}****${phone.slice(-4)}` : '手机号不可用', blockReason: blockReason(summary) })
      this.updateCountdown()
    } catch (error) { if (version === this.version) feedback.update(this, { error: error.message || '提现资料加载失败' }) }
    finally { if (version === this.version) this.setData({ loading: false }) }
  },
  input(event) {
    if (this.data.submitting || this.data.loading) return
    const field = event.currentTarget.dataset.field
    if (!Object.prototype.hasOwnProperty.call(emptyForm(), field)) return
    let value = String(event.detail.value || '')
    if (field === 'smsCode' || field === 'paymentPassword') value = value.replace(/\D/g, '').slice(0,6)
    this.setData({ [`form.${field}`]: value })
  },
  updateCountdown() {
    clearTimeout(this.timer)
    if (this.hidden || this.disposed) return
    const countdown = Math.max(0, Math.ceil(((this.resendAt || 0) - Date.now()) / 1000))
    this.setData({ countdown })
    if (countdown) this.timer = setTimeout(() => this.updateCountdown(),1000)
  },
  async sendCode() {
    if (this.data.loading || this.data.submitting || this.data.sendingCode || this.data.countdown || this.data.blockReason || !this.member) return
    if (!/^1[3-9]\d{9}$/.test(String(this.member.phone || ''))) return feedback.notice('绑定手机号不可用，请联系客服核对。')
    const version = this.version, token = session.getToken()
    this.setData({ sendingCode: true })
    try {
      await request({ url: '/sms/send', method: 'POST', data: { phone: this.member.phone, bizType: 5 } })
      if (version !== this.version || token !== session.getToken()) return
      this.resendAt = Date.now() + 60000; this.updateCountdown()
      feedback.notice('提现验证码已发送至绑定手机号，5分钟内有效。请勿提供给他人。', '验证码已发送')
    } catch (error) { if (version === this.version) feedback.notice(error.message, '验证码未发送') }
    finally { if (!this.disposed) this.setData({ sendingCode: false }) }
  },
  async submit() {
    if (this.data.loading || this.data.submitting || !this.member) return
    if (this.data.blockReason) return feedback.notice(this.data.blockReason, '提现条件未满足')
    const form = this.data.form, amount = String(form.withdrawAmount).trim()
    if (!/^(?:0|[1-9]\d{0,7})(?:\.\d{1,2})?$/.test(amount) || Number(amount) <= 0) return feedback.notice('请输入大于0、最多两位小数的提现金额。')
    if (Number(amount) > this.available) return feedback.notice('提现金额不能超过可提现余额。')
    if (!form.accountName.trim() || form.accountName.trim().length > 64) return feedback.notice('请输入与实名认证一致的收款人姓名。')
    if (!/^\d{6}$/.test(form.paymentPassword)) return feedback.notice('请输入6位支付密码，不是商城登录密码。')
    if (!/^\d{6}$/.test(form.smsCode)) return feedback.notice('请输入绑定手机号收到的6位提现验证码。')
    // Lock before confirmation, including rapid repeated taps.
    const token = session.getToken(), version = this.version
    this.setData({ submitting: true })
    const confirm = await new Promise((resolve) => wx.showModal({ title: '确认提现', content: `申请提现 ¥${format.money(amount)} 至当前账号绑定的微信。实际到账以审核及打款结果为准。`, success: resolve, fail: () => resolve({ confirm: false }) }))
    if (!confirm.confirm || token !== session.getToken() || version !== this.version) { if (!this.disposed) this.setData({ submitting: false }); return }
    try {
      await request({ url: '/shop/wallet/withdrawals', method: 'POST', idempotencyKey: `MINI-WITHDRAW-${Date.now()}-${Math.random().toString(36).slice(2)}`,
        data: { withdrawType: 2, withdrawAmount: amount, accountName: form.accountName.trim(), paymentPassword: form.paymentPassword, smsCode: form.smsCode } })
      this.setData({ form: emptyForm() })
      if (version === this.version && token === session.getToken()) {
        await feedback.notice('提现申请已提交，不代表已经到账。请在提现记录查看审核、打款进度及微信收款确认。', '申请已提交')
        if (version === this.version && token === session.getToken() && !this.hidden) this.history()
      }
    } catch (error) {
      this.setData({ form: emptyForm() })
      if (version === this.version && token === session.getToken()) {
        await feedback.notice(`${error.message || '暂未获取到处理结果'}\n请先到提现记录核对是否已生成申请，再决定是否重试，避免重复提现。`, '请核对提现结果')
        if (version === this.version && token === session.getToken() && !this.hidden) this.history()
      }
    } finally { if (!this.disposed) this.setData({ submitting: false }) }
  },
  history() { wx.redirectTo({ url: '/pages/payout/index?history=1' }) }
})
