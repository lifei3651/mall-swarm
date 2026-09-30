const request = require('./request')
const feedback = require('./feedback')
const session = require('./session')
const balanceMode = require('./balance-mode')
const emptySetupForm = () => ({ loginPassword: '', smsCode: '', newPassword: '', confirmPassword: '' })
const data = { balanceDialog: false, balanceLoading: false, balanceBusy: false, balancePassword: '', balanceWallet: null,
  balanceSetupBusy: false, balanceSetupSending: false, balanceSetupCooldown: 0,
  balanceSetupForm: emptySetupForm(), balanceSetupError: '', balanceSetupNotice: '' }
function snapshot(page) { return `${page.data.payOrderId}:${page.data.totalText}:${page.data.rows.map(row => row.order.id).join(',')}` }
function validateWallet(wallet, amount) {
  if (!balanceMode.balanceTransactionsEnabled(wallet)) throw new Error('本商城已暂停新增余额支付，可取消待付款订单并选择其他可用方式重新下单')
  if (!wallet || typeof wallet.hasPaymentPassword !== 'boolean' || wallet.balance == null || !Number.isFinite(Number(wallet.balance))) throw new Error('余额安全状态暂不可用，请重试')
  if (wallet.paymentPasswordLocked) throw new Error('支付密码已临时锁定，请稍后重新加载；不要反复尝试密码')
  if (Number(wallet.balance) < Number(amount)) throw new Error('账户可用余额不足，请核对余额或联系商城客服')
}
const methods = {
  clearBalanceSetup() {
    this.balanceSetupGeneration = (this.balanceSetupGeneration || 0) + 1
    clearInterval(this.balanceSetupTimer)
    this.setData({ balanceSetupBusy: false, balanceSetupSending: false, balanceSetupCooldown: 0,
      balanceSetupForm: emptySetupForm(), balanceSetupError: '', balanceSetupNotice: '' })
  },
  async openBalancePayment() {
    if (this.data.balanceLoading || this.data.balanceBusy || this.data.actingId || this.hidden || !this.data.payOrderId) return
    const token = session.getToken(), previous = snapshot(this), current = () => !this.disposed && !this.hidden && token === session.getToken()
    this.setData({ balanceLoading: true, balancePassword: '', actingId: 'balance' })
    try {
      if (!await this.load() || !current()) return
      if (previous !== snapshot(this) || this.data.paymentChannel !== 'BALANCE') throw new Error('订单状态或金额已更新，请核对后重新付款')
      const wallet = await request({ url: '/shop/wallet/summary' })
      if (!current()) return
      validateWallet(wallet, this.data.totalText)
      this.clearBalanceSetup()
      this.balanceOwner = token; this.balanceSnapshot = snapshot(this)
      this.setData({ balanceDialog: true, balanceWallet: wallet, actingId: 'balance' })
    } catch (error) { if (current()) await feedback.notice(error.message || '余额支付暂不可用') }
    finally { this.setData({ balanceLoading: false, ...(!this.data.balanceDialog ? { actingId: null } : {}) }) }
  },
  balanceInput(event) { if (!this.data.balanceBusy) this.setData({ balancePassword: String(event.detail.value || '').replace(/\D/g,'').slice(0,6) }) },
  closeBalance() {
    if (this.data.balanceBusy || this.data.balanceSetupBusy) return
    this.clearBalanceSetup()
    this.setData({ balanceDialog: false, balancePassword: '', actingId: null })
  },
  openBalanceLoginSetup() {
    if (this.data.balanceSetupBusy) return
    this.closeBalance()
    wx.navigateTo({ url: '/pages/account-security/index?mode=password' })
  },
  balanceSetupInput(event) {
    if (!this.data.balanceDialog || this.data.balanceSetupBusy) return
    const field = event.currentTarget.dataset.field
    if (!Object.prototype.hasOwnProperty.call(emptySetupForm(), field)) return
    let value = String(event.detail.value || '')
    if (field !== 'loginPassword') value = value.replace(/\D/g, '').slice(0, 6)
    this.setData({ [`balanceSetupForm.${field}`]: value, balanceSetupError: '' })
  },
  async sendBalanceSetupCode() {
    if (!this.data.balanceDialog || this.data.balanceWallet?.hasPaymentPassword || this.data.balanceSetupBusy
      || this.data.balanceSetupSending || this.data.balanceSetupCooldown || this.hidden) return
    const token = session.getToken(), generation = this.balanceSetupGeneration
    const current = () => !this.disposed && !this.hidden && this.data.balanceDialog
      && this.balanceOwner === token && token === session.getToken() && generation === this.balanceSetupGeneration
    this.setData({ balanceSetupSending: true, balanceSetupError: '' })
    try {
      await request({ url: '/sms/send/payment-password', method: 'POST' })
      if (!current()) return
      this.setData({ balanceSetupCooldown: 60 })
      clearInterval(this.balanceSetupTimer)
      this.balanceSetupTimer = setInterval(() => {
        if (!current()) return clearInterval(this.balanceSetupTimer)
        const next = Math.max(0, this.data.balanceSetupCooldown - 1)
        this.setData({ balanceSetupCooldown: next })
        if (!next) clearInterval(this.balanceSetupTimer)
      }, 1000)
      feedback.toast({ title: '验证码已发送', icon: 'success' })
    } catch (error) { if (current()) this.setData({ balanceSetupError: error.message || '验证码发送失败' }) }
    finally { if (current()) this.setData({ balanceSetupSending: false }) }
  },
  async saveBalanceSetup() {
    if (!this.data.balanceDialog || this.data.balanceWallet?.hasPaymentPassword || this.data.balanceSetupBusy || this.hidden) return
    const form = { ...this.data.balanceSetupForm }
    if (!form.loginPassword) return this.setData({ balanceSetupError: '请输入当前商城登录密码' })
    if (!/^\d{6}$/.test(form.smsCode)) return this.setData({ balanceSetupError: '请输入6位短信验证码' })
    if (!/^\d{6}$/.test(form.newPassword)) return this.setData({ balanceSetupError: '支付密码必须是6位数字' })
    if (form.newPassword !== form.confirmPassword) return this.setData({ balanceSetupError: '两次输入的支付密码不一致' })
    const token = session.getToken(), generation = this.balanceSetupGeneration
    const current = () => !this.disposed && !this.hidden && this.data.balanceDialog
      && this.balanceOwner === token && token === session.getToken() && generation === this.balanceSetupGeneration
    this.setData({ balanceSetupBusy: true, balanceSetupError: '' })
    try {
      await request({ url: '/shop/wallet/payment-password', method: 'PUT', data: {
        loginPassword: form.loginPassword, smsCode: form.smsCode, newPassword: form.newPassword
      } })
      if (!current()) return
      clearInterval(this.balanceSetupTimer)
      this.setData({ 'balanceWallet.hasPaymentPassword': true, balanceSetupNotice: '支付密码已设置，请输入新密码完成付款。',
        balanceSetupForm: emptySetupForm(), balanceSetupCooldown: 0 })
    } catch (error) { if (current()) this.setData({ balanceSetupError: error.message || '支付密码设置失败' }) }
    finally { if (current()) this.setData({ balanceSetupBusy: false, balanceSetupForm: emptySetupForm() }) }
  },
  async confirmBalancePayment() {
    if (!this.data.balanceDialog || this.data.balanceBusy || this.hidden || this.balanceOwner !== session.getToken()) return
    if (!/^\d{6}$/.test(this.data.balancePassword)) return feedback.notice('请输入6位数字支付密码，不是商城登录密码')
    const password = this.data.balancePassword, token = session.getToken(), expected = this.balanceSnapshot
    const current = () => !this.disposed && !this.hidden && token === session.getToken()
    this.setData({ balanceBusy: true, balancePassword: '' })
    let submitted = false
    try {
      if (!await this.load() || !current()) return
      if (expected !== snapshot(this) || this.data.paymentChannel !== 'BALANCE') throw new Error('订单状态或金额已更新，请核对后重新付款')
      const wallet = await request({ url: '/shop/wallet/summary' })
      if (!current()) return
      validateWallet(wallet, this.data.totalText)
      if (!wallet.hasPaymentPassword) throw new Error('请先设置独立支付密码')
      if (!this.balanceKey || this.balanceKeyOrder !== expected) { this.balanceKeyOrder = expected; this.balanceKey = `MINI-BALANCE-${Date.now()}-${Math.random().toString(36).slice(2)}` }
      submitted = true
      await request({ url: `/shop/wallet/orders/${this.data.payOrderId}/pay`, method: 'POST', data: { paymentPassword: password }, idempotencyKey: this.balanceKey })
      if (!current()) return
      this.setData({ balanceDialog: false })
      const loaded = await this.load()
      if (current()) await feedback.notice(loaded && this.data.rows.length && this.data.rows.every(row => [1,2,3,5].includes(Number(row.order.status))) ? '支付已确认，可在订单中查看发货进度。' : '付款结果仍需核对，请刷新订单；如已扣款，勿重复付款。', '请核对订单状态')
    } catch (error) {
      if (!current()) return
      this.setData({ balanceDialog: false })
      if (submitted) await this.load()
      if (current()) await feedback.notice(`${error.message || '余额支付未完成'}${submitted ? '。请先核对订单和余额明细；如已扣款，勿重复付款。' : ''}`, '暂未确认支付成功')
    } finally { this.setData({ balanceBusy: false, balancePassword: '', actingId: null }) }
  }
}
module.exports = { data, methods, validateWallet }
