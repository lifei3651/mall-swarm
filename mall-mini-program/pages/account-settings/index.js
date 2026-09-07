const request = require('../../utils/request')
const session = require('../../utils/session')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const feedback = require('../../utils/feedback')
const sections = { security: '支付安全', payment: '支付密码', phone: '更换手机号', identity: '实名认证' }
const empty = () => ({ oldPassword: '', loginPassword: '', newPassword: '', confirmPassword: '', smsCode: '', currentPhoneSmsCode: '', newPhone: '', newPhoneSmsCode: '', realName: '', idCard: '', sensitiveInfoConsent: false })
const phoneValid = value => /^1[3-9]\d{9}$/.test(String(value || ''))
Page({
  data: { ...theme.pageData(), section: 'security', loading: true, error: '', busy: false, sending: '', countdowns: { payment: 0, current: 0, new: 0 }, member: null, maskedPhone: '', wallet: null, identity: null, form: empty() },
  onLoad(options = {}) { this.setData({ section: Object.hasOwnProperty.call(sections, options.section) ? options.section : 'security' }); theme.apply(this); wx.setNavigationBarTitle({ title: sections[this.data.section] }) },
  onShow() {
    this.inactive = false; theme.apply(this)
    const token = session.getToken()
    if (token !== this.owner) { this.owner = token; this.deadlines = {}; this.setData({ form: empty(), member: null, wallet: null, identity: null }) }
    this.tick()
    if (auth.requireLogin(`/pages/account-settings/index?section=${this.data.section}`)) return this.load()
  },
  onHide() { this.inactive = true; this.sequence = (this.sequence || 0) + 1; clearTimeout(this.timer); this.setData({ form: empty() }) },
  onUnload() { this.onHide() },
  guard() { const token = session.getToken(), sequence = this.sequence; return () => !this.inactive && token === session.getToken() && sequence === this.sequence },
  async load() {
    if (this.data.busy) return
    this.sequence = (this.sequence || 0) + 1; const current = this.guard()
    this.setData({ loading: true, error: '' })
    try {
      const [member, wallet, identity] = await Promise.all([request({ url: '/shop/auth/me' }), this.data.section !== 'phone' ? request({ url: '/shop/wallet/summary' }) : null, this.data.section === 'identity' ? request({ url: '/shop/real-name/status' }) : null])
      if (!current()) return
      if (!member || !member.id || (this.data.section !== 'phone' && (!wallet || typeof wallet.hasPaymentPassword !== 'boolean')) || (this.data.section === 'identity' && (!identity || typeof identity.verified !== 'boolean'))) throw new Error('账号安全信息不完整，请重新加载')
      this.setData({ member, wallet, identity, maskedPhone: phoneValid(member.phone) ? String(member.phone).replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '尚未绑定有效手机号' })
    } catch (error) { if (current()) feedback.update(this, { error: error.message || '账号安全信息加载失败' }) }
    finally { if (current()) this.setData({ loading: false }) }
  },
  input(event) {
    if (this.data.busy) return
    const field = event.currentTarget.dataset.field
    if (!Object.hasOwnProperty.call(empty(), field) || field === 'sensitiveInfoConsent') return
    let value = String(event.detail.value || '')
    if (['oldPassword','newPassword','confirmPassword','smsCode','currentPhoneSmsCode','newPhoneSmsCode'].includes(field)) value = value.replace(/\D/g,'').slice(0,6)
    if (field === 'newPhone') value = value.replace(/\D/g,'').slice(0,11)
    if (field === 'idCard') value = value.replace(/[^0-9xX]/g,'').toUpperCase().slice(0,18)
    this.setData({ [`form.${field}`]: value })
  },
  consent(event) { if (!this.data.busy) this.setData({ 'form.sensitiveInfoConsent': (event.detail.value || []).includes('agree') }) },
  privacy() { wx.navigateTo({ url: '/pages/legal/index?type=privacy' }) },
  open(event) { const section = event.currentTarget.dataset.section; if (Object.hasOwnProperty.call(sections, section)) wx.navigateTo({ url: `/pages/account-settings/index?section=${section}` }) },
  loginPassword() { wx.navigateTo({ url: '/pages/account-security/index?mode=password' }) },
  tick() {
    clearTimeout(this.timer); if (this.inactive) return
    const countdowns = Object.fromEntries(['payment','current','new'].map(key => [key, Math.max(0, Math.ceil(((this.deadlines || {})[key] || 0) - Date.now()) / 1000)]).map(([key,value]) => [key,Math.ceil(value)]))
    this.setData({ countdowns }); if (Object.values(countdowns).some(Boolean)) this.timer = setTimeout(() => this.tick(), 1000)
  },
  async sendCode(event) {
    const type = event.currentTarget.dataset.type
    if (!['payment','current','new'].includes(type) || this.data.loading || this.data.error || this.data.busy || this.data.sending || !this.data.member || this.data.countdowns[type]) return
    if ((type === 'payment') !== (this.data.section === 'payment') || (type !== 'payment' && this.data.section !== 'phone')) return
    const phone = type === 'new' ? this.data.form.newPhone : this.data.member.phone
    if (!phoneValid(phone)) return feedback.notice('请输入正确的11位手机号')
    if (type === 'new' && phone === this.data.member.phone) return feedback.notice('新手机号不能与当前手机号相同')
    const current = this.guard(); this.setData({ sending: type })
    try {
      await request(type === 'payment' ? { url: '/sms/send/payment-password', method: 'POST' } : { url: '/sms/send', method: 'POST', data: { phone, bizType: type === 'current' ? 9 : 10 } })
      if (!current()) return
      this.deadlines = { ...this.deadlines, [type]: Date.now() + 60000 }; this.tick()
      await feedback.notice('验证码已发送，5分钟内有效。请勿提供给他人。', '验证码已发送')
    } catch (error) { if (current()) await feedback.notice(error.message || '验证码发送失败') }
    finally { this.setData({ sending: '' }) }
  },
  async savePayment() {
    if (!this.canSubmit('payment')) return
    const wallet = this.data.wallet, form = { ...this.data.form }
    if (wallet.paymentPasswordLocked) return feedback.notice('支付密码已临时锁定，请稍后重新加载状态，不要反复尝试')
    if (!/^\d{6}$/.test(form.newPassword)) return feedback.notice('支付密码必须是6位数字')
    if (form.newPassword !== form.confirmPassword) return feedback.notice('两次输入的支付密码不一致')
    if (wallet.hasPaymentPassword && !/^\d{6}$/.test(form.oldPassword)) return feedback.notice('请输入当前6位支付密码')
    if (!wallet.hasPaymentPassword && !form.loginPassword) return feedback.notice('请输入当前商城登录密码；尚未设置商城账号时，请先设置账号')
    if (!/^\d{6}$/.test(form.smsCode)) return feedback.notice('请输入6位短信验证码')
    const current = this.guard(); this.setData({ busy: true })
    try {
      await request({ url: '/shop/wallet/payment-password', method: 'PUT', data: { oldPassword: wallet.hasPaymentPassword ? form.oldPassword : '', loginPassword: wallet.hasPaymentPassword ? '' : form.loginPassword, newPassword: form.newPassword, smsCode: form.smsCode } })
      if (current()) { this.setData({ form: empty(), 'wallet.hasPaymentPassword': true }); await feedback.notice('支付密码已保存', '操作完成') }
    } catch (error) { if (current()) await feedback.notice(error.message || '支付密码保存失败') }
    finally { this.setData({ busy: false, form: empty() }) }
  },
  async savePhone() {
    if (!this.canSubmit('phone')) return
    const form = { ...this.data.form }
    if (!/^\d{6}$/.test(form.currentPhoneSmsCode)) return feedback.notice('请输入当前手机号收到的6位验证码')
    if (!phoneValid(form.newPhone) || form.newPhone === this.data.member.phone) return feedback.notice('请输入与当前号码不同的有效新手机号')
    if (!/^\d{6}$/.test(form.newPhoneSmsCode)) return feedback.notice('请输入新手机号收到的6位验证码')
    const current = this.guard(); this.setData({ busy: true })
    try {
      await request({ url: '/shop/auth/phone', method: 'PUT', data: { currentPhoneSmsCode: form.currentPhoneSmsCode, newPhone: form.newPhone, newPhoneSmsCode: form.newPhoneSmsCode } })
      if (!current()) return
      session.clearSession(); this.setData({ form: empty(), member: null })
      await feedback.notice('手机号已更新，请重新登录', '操作完成')
      if (!this.inactive && !session.getToken()) wx.redirectTo({ url: '/pages/login/index' })
    } catch (error) { if (current()) await feedback.notice(error.message || '手机号更换失败') }
    finally { this.setData({ busy: false, form: empty() }) }
  },
  async verifyIdentity() {
    if (!this.canSubmit('identity') || this.data.identity.verified) return
    if (this.data.identity.verificationAvailable !== true) return feedback.notice('实名认证通道尚未启用，请联系客服')
    const form = this.data.form, realName = form.realName.trim(), idCard = form.idCard.trim()
    if (!realName || realName.length > 64) return feedback.notice('请输入身份证上的真实姓名')
    if (!/^[1-9]\d{16}[0-9X]$/.test(idCard)) return feedback.notice('请输入正确的18位身份证号')
    if (form.sensitiveInfoConsent !== true) return feedback.notice('请先阅读隐私政策并单独同意实名认证授权')
    const current = this.guard(); this.setData({ busy: true })
    try {
      const identity = await request({ url: '/shop/real-name/verify', method: 'POST', data: { realName, idCard, sensitiveInfoConsent: true } })
      if (!current()) return
      if (!identity || identity.verified !== true) throw new Error('暂未确认认证成功，请刷新认证状态后再试')
      this.setData({ identity, form: empty() }); await feedback.notice('实名认证已完成', '操作完成')
    } catch (error) { if (current()) await feedback.notice(error.message || '实名认证失败，请稍后重试') }
    finally { this.setData({ busy: false, form: empty() }) }
  },
  canSubmit(section) { return this.data.section === section && !this.inactive && !this.data.loading && !this.data.error && !this.data.busy && !this.data.sending && Boolean(this.data.member) && session.getToken() === this.owner },
  back() { wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/index' }) }) },
  support() { wx.navigateTo({ url: '/pages/support/index?create=1&type=ACCOUNT' }) }
})
