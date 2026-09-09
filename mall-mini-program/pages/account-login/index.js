const request = require('../../utils/request')
const session = require('../../utils/session')
const invite = require('../../utils/invite')
const invitation = require('../../utils/login-invitation')
const theme = require('../../utils/theme')
const feedback = require('../../utils/feedback')
const config = require('../../config/runtime')
const flow = require('../../utils/login-flow')
const { normalizeLoginAccountInput, validateLoginAccount, resolveRegistrationErrorField } = require('../../utils/h5-rules/loginAccount')
const titles = { password: '账号密码登录', sms: '短信验证码登录', register: '注册商城账号', reset: '找回登录密码' }
const empty = () => ({ account: '', phone: '', username: '', password: '', confirmPassword: '', smsCode: '', captchaCode: '' })
const phoneValid = value => /^1[3-9]\d{9}$/.test(value)
Page({
  data: { ...theme.pageData(), ...invitation.data, mode: 'password', form: empty(), agreed: false,
    submitting: false, sending: false, cooldown: 0, captchaBusy: false, captchaId: '', captchaImage: '',
    error: '', errorField: '', resetStep: 1 },
  ...invitation.methods,
  onLoad(options = {}) {
    this._inactive = false; this._epoch = 0; this._deadlines = {}; this._owner = session.getToken()
    try { this.redirect = decodeURIComponent(options.redirect || '') } catch (_) { this.redirect = '' }
    this.syncInvitation(true)
    return this.setMode(titles[options.mode] ? options.mode : 'password')
  },
  onShow() {
    const returning = this._inactive
    this._inactive = false; theme.apply(this); this.tick(); this.syncInvitation(true)
    if (session.getToken() !== this._owner) { this.onHide(); flow.finish.call(this); return }
    // onLoad already starts the first request; a fast failure must not show it twice.
    if (returning && this.data.mode !== 'sms' && !this.data.captchaImage) return this.refreshCaptcha()
  },
  onHide() {
    this._inactive = true; this._epoch++; this._inviteSequence = (this._inviteSequence || 0) + 1
    clearTimeout(this._timer)
    this.setData({ submitting: false, sending: false, captchaBusy: false, resetStep: 1, 'form.password': '', 'form.confirmPassword': '', 'form.smsCode': '', 'form.captchaCode': '' })
  },
  onUnload() { this.onHide() },
  current() { const epoch = this._epoch, token = session.getToken(); return () => !this._inactive && epoch === this._epoch && token === session.getToken() },
  async setMode(mode) {
    if (!Object.hasOwnProperty.call(titles, mode) || this.data.submitting || this.data.sending) return
    this._epoch++; const phone = this.data.form.phone
    this.setData({ mode, resetStep: 1, form: { ...empty(), phone, account: phone }, error: '', errorField: '', captchaId: '', captchaImage: '' })
    wx.setNavigationBarTitle({ title: titles[mode] }); theme.apply(this); this.tick()
    if (mode !== 'sms') await this.refreshCaptcha()
  },
  switchMode(event) { return this.setMode(event.currentTarget.dataset.mode) },
  input(event) {
    if (this.data.submitting || this.data.sending) return
    const field = event.currentTarget.dataset.field
    if (!Object.hasOwnProperty.call(empty(), field)) return
    let value = String(event.detail.value || '')
    if (field === 'phone') value = value.replace(/\D/g, '').slice(0, 11)
    if (field === 'smsCode') value = value.replace(/\D/g, '').slice(0, 6)
    if (field === 'captchaCode') value = value.replace(/[^a-zA-Z0-9]/g, '').slice(0, 4)
    if (field === 'username') value = normalizeLoginAccountInput(value)
    this.setData({ [`form.${field}`]: value, errorField: '', error: '' }); if (field === 'phone') this.tick()
  },
  consent(event) { if (!this.data.submitting) this.setData({ agreed: (event.detail.value || []).includes('agree') }) },
  async refreshCaptcha() {
    if (this._inactive || this.data.submitting || this.data.captchaBusy || this.data.mode === 'sms') return
    const current = this.current(); this.setData({ captchaBusy: true, captchaId: '', captchaImage: '', 'form.captchaCode': '' })
    try {
      const data = await request({ url: '/captcha', params: { scene: 'shop' } })
      if (!current()) return
      if (!data || !data.captchaId || !/^data:image\/(png|jpeg);base64,[A-Za-z0-9+/=]+$/.test(data.image || '')) throw new Error('图形验证码未能加载，请点击刷新')
      this.setData({ captchaId: data.captchaId, captchaImage: data.image })
    } catch (error) { if (current()) this.showError(error.message || '图形验证码加载失败', 'captchaCode') }
    finally { if (current()) this.setData({ captchaBusy: false }) }
  },
  deadlineKey() { return `${this.data.mode}:${this.data.form.phone}` },
  tick() {
    clearTimeout(this._timer); if (this._inactive) return
    const cooldown = Math.max(0, Math.ceil(((this._deadlines || {})[this.deadlineKey()] || 0) - Date.now()) / 1000)
    this.setData({ cooldown: Math.ceil(cooldown) }); if (cooldown) this._timer = setTimeout(() => this.tick(), 1000)
  },
  async sendCode() {
    if (this._inactive || this.data.submitting || this.data.sending || this.data.cooldown || this.data.mode === 'password') return
    if (!phoneValid(this.data.form.phone)) return this.showError('请输入正确的11位手机号', 'phone')
    if (!this.data.agreed) return this.showError('请先阅读并同意用户服务协议和隐私政策', 'agreement')
    const current = this.current(), key = this.deadlineKey(), mode = this.data.mode
    this.setData({ sending: true, error: '' })
    try {
      await request(mode === 'sms' ? { url: '/sms/send/login', method: 'POST', data: { phone: this.data.form.phone } }
        : { url: '/sms/send', method: 'POST', data: { phone: this.data.form.phone, bizType: mode === 'register' ? 1 : 3 } })
      this._deadlines[key] = Date.now() + 60000
      if (current()) { this.tick(); await feedback.notice('验证码已发送，5分钟内有效。请勿提供给他人。', '验证码已发送') }
    } catch (error) { if (current()) this.showError(error.message || '验证码发送失败', 'smsCode') }
    finally { if (current()) this.setData({ sending: false }) }
  },
  showError(error, field = '') { feedback.update(this, { error, errorField: field || resolveRegistrationErrorField(error) }) },
  validate() {
    const { mode, form, captchaId, agreed } = this.data
    if (!agreed) return ['请先阅读并同意用户服务协议和隐私政策', 'agreement']
    if (mode !== 'password' && !phoneValid(form.phone)) return ['请输入正确的11位手机号', 'phone']
    if (mode === 'password' && !form.account.trim()) return ['请输入手机号或登录账号', 'account']
    if (mode === 'password' && !form.password) return ['请输入登录密码', 'password']
    if (mode === 'register') { const error = validateLoginAccount(form.username); if (error) return [error, 'username'] }
    if ((mode === 'register' || (mode === 'reset' && this.data.resetStep === 2))) {
      if (form.password.length < 6 || form.password.length > 32) return ['登录密码需为6至32位', 'password']
      if (form.password !== form.confirmPassword) return ['两次输入的登录密码不一致', 'confirmPassword']
    }
    if (mode !== 'password' && !/^\d{6}$/.test(form.smsCode)) return ['请输入6位短信验证码', 'smsCode']
    if (mode !== 'sms' && (!captchaId || !/^[A-Za-z0-9]{4}$/.test(form.captchaCode))) return ['请输入4位图形验证码', 'captchaCode']
    return null
  },
  async submit() {
    if (this._inactive || this.data.submitting || this.data.sending || this.data.captchaBusy) return
    const invalid = this.validate(); if (invalid) return this.showError(...invalid)
    const mode = this.data.mode
    if (mode === 'register' && !this.invitationReady()) return
    if (mode === 'reset' && this.data.resetStep === 1) { this.setData({ resetStep: 2, error: '', errorField: '' }); return }
    const current = this.current(), form = { ...this.data.form }, captcha = { captchaId: this.data.captchaId, captchaCode: form.captchaCode }
    this.setData({ submitting: true, error: '', errorField: '' })
    try {
      if (mode === 'reset') {
        await request({ url: '/shop/auth/resetPassword', method: 'POST', data: { phone: form.phone, smsCode: form.smsCode, newPassword: form.password, ...captcha } })
        if (!current()) return
        session.clearSession(); this.setData({ submitting: false }); await this.setMode('password')
        await feedback.notice('登录密码已重置，请使用新密码登录。', '操作完成'); return
      }
      const credentials = mode === 'register' ? { phone: form.phone, username: form.username, password: form.password, smsCode: form.smsCode, inviteCode: this._verifiedInviteCode || undefined, ...captcha }
        : mode === 'sms' ? { account: form.phone, smsCode: form.smsCode, loginType: 'sms' }
        : { account: form.account.trim(), password: form.password, loginType: 'password', ...captcha }
      const result = await request({ url: `/shop/wechat-mini-program/auth/account-${mode === 'register' ? 'register' : 'login'}`, method: 'POST',
        data: { credentials, privacyAgreed: true, privacyConsentVersion: config.PRIVACY_CONSENT_VERSION } })
      if (!current()) return
      if (!result || typeof result.accessToken !== 'string' || !result.accessToken || !result.member?.id) throw new Error('登录结果不完整，请重新登录')
      session.saveSession(result); invite.clearPendingInvite(); this._owner = session.getToken()
      this.setData({ form: empty(), submitting: false })
      if (mode === 'register') this.redirect = '/pages/home/index'
      flow.finish.call(this)
    } catch (error) {
      if (current()) { this.setData({ submitting: false }); this.showError(error.message || '操作未完成，请重试'); if (mode !== 'sms') { if (mode === 'reset') this.setData({ resetStep: 1 }); await this.refreshCaptcha() } }
    } finally { form.password = ''; form.confirmPassword = ''; form.smsCode = ''; if (current()) this.setData({ submitting: false }) }
  },
  backToReset() { if (!this.data.submitting) this.setData({ resetStep: 1 }) },
  privacy() { wx.navigateTo({ url: '/pages/legal/index?type=privacy' }) },
  agreement() { wx.navigateTo({ url: '/pages/legal/index?type=agreement' }) }
})
