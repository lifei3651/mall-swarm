const request = require('./request')
const feedback = require('./feedback')
const session = require('./session')
const data = { balanceDialog: false, balanceLoading: false, balanceBusy: false, balancePassword: '', balanceWallet: null }
function snapshot(page) { return `${page.data.payOrderId}:${page.data.totalText}:${page.data.rows.map(row => row.order.id).join(',')}` }
function validateWallet(wallet, amount) {
  if (!wallet || typeof wallet.hasPaymentPassword !== 'boolean' || wallet.balance == null || !Number.isFinite(Number(wallet.balance))) throw new Error('余额安全状态暂不可用，请重试')
  if (wallet.paymentPasswordLocked) throw new Error('支付密码已临时锁定，请稍后重新加载；不要反复尝试密码')
  if (Number(wallet.balance) < Number(amount)) throw new Error('账户可用余额不足，请核对余额或联系商城客服')
}
const methods = {
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
      this.balanceOwner = token; this.balanceSnapshot = snapshot(this)
      this.setData({ balanceDialog: true, balanceWallet: wallet, actingId: 'balance' })
    } catch (error) { if (current()) await feedback.notice(error.message || '余额支付暂不可用') }
    finally { this.setData({ balanceLoading: false, ...(!this.data.balanceDialog ? { actingId: null } : {}) }) }
  },
  balanceInput(event) { if (!this.data.balanceBusy) this.setData({ balancePassword: String(event.detail.value || '').replace(/\D/g,'').slice(0,6) }) },
  closeBalance() { if (!this.data.balanceBusy) this.setData({ balanceDialog: false, balancePassword: '', actingId: null }) },
  setupPaymentPassword() { if (!this.data.balanceBusy) { this.closeBalance(); wx.navigateTo({ url: '/pages/account-settings/index?section=payment' }) } },
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
