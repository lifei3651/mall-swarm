const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const session = require('../../utils/session')
const theme = require('../../utils/theme')
const privacy = require('../../utils/privacy')
const avatar = require('../../utils/member-avatar')

const EMPTY_SECRETS = { password: '', currentPassword: '', newPassword: '', confirmPassword: '', smsCode: '' }
function passwordError(value, username, phone) {
  if (value.length < 10 || value.length > 32) return '登录密码需为10至32位'
  if (/\s/.test(value)) return '登录密码不能包含空格'
  const normalized = value.toLowerCase()
  if ((username && normalized.includes(username.toLowerCase())) || (phone && (normalized.includes(phone) || normalized.endsWith(phone.slice(-6))))) return '登录密码不能包含登录账号或手机号'
  return ''
}

Page({
  data: { ...theme.pageData(), ...EMPTY_SECRETS, loading: true, error: '', message: '', member: null,
    username: '', nickname: '', useWechatNickname: false, avatarSrc: avatar.fallback, maskedPhone: '', canSetupAccount: false, action: '', sendingCode: false, countdown: 0 },
  onLoad() { theme.apply(this) },
  onShow() {
    this.hidden = false
    theme.apply(this)
    this.updateCountdown()
    if (this.data.action) return
    if (auth.requireLogin('/pages/account-security/index')) return this.load()
    this.requestVersion = (this.requestVersion || 0) + 1
    feedback.update(this, { ...EMPTY_SECRETS, loading: false, member: null, nickname: '', username: '', maskedPhone: '', canSetupAccount: false })
  },
  onHide() {
    avatar.release(this.data.avatarSrc)
    this.hidden = true
    this.requestVersion = (this.requestVersion || 0) + 1
    clearTimeout(this.countdownTimer)
    feedback.update(this, { ...EMPTY_SECRETS, avatarSrc: avatar.fallback })
  },
  onUnload() { this.disposed = true; this.onHide() },
  async load() {
    const version = this.requestVersion = (this.requestVersion || 0) + 1
    feedback.update(this, { loading: true, error: '' })
    try {
      const member = await request({ url: '/shop/auth/me' })
      if (this.disposed || this.hidden || version !== this.requestVersion) return
      if (!member || !member.id) throw new Error('账号信息加载失败，请重新登录')
      const username = String(member.username || '')
      const phone = String(member.phone || '')
      feedback.update(this, { member, nickname: member.nickname || '', username: '', canSetupAccount: !username.trim() || username === phone,
        maskedPhone: /^1[3-9]\d{9}$/.test(phone) ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : '尚未绑定有效手机号' })
      const avatarSrc = await avatar.load(member.avatarUrl)
      if (this.disposed || this.hidden || version !== this.requestVersion) avatar.release(avatarSrc)
      else { avatar.release(this.data.avatarSrc); feedback.update(this, { avatarSrc }) }
    } catch (error) {
      if (!this.disposed && !this.hidden && version === this.requestVersion) feedback.update(this, { ...EMPTY_SECRETS, member: null, canSetupAccount: false, error: error.message || '账号信息加载失败' })
    } finally { if (!this.disposed && !this.hidden && version === this.requestVersion) feedback.update(this, { loading: false }) }
  },
  fieldInput(event) {
    if (this.data.action) return
    const field = event.currentTarget.dataset.field
    if (!['nickname', 'username', ...Object.keys(EMPTY_SECRETS)].includes(field)) return
    let value = String(event.detail.value || '')
    if (field === 'smsCode') value = value.replace(/\D/g, '').slice(0, 6)
    feedback.update(this, { [field]: value, error: '', message: '' })
  },
  async enableWechatNickname() {
    if (this.data.action) return
    try { await privacy.requireConsent(); if (!this.disposed) feedback.update(this, { useWechatNickname: true, error: '' }) }
    catch (error) { if (!this.disposed) feedback.update(this, { error: error.message, useWechatNickname: false }) }
  },
  async chooseAvatar(event) {
    if (this.data.action || !this.data.member || !event.detail.avatarUrl) return
    const token = session.getToken()
    const path = event.detail.avatarUrl
    this.requestVersion = (this.requestVersion || 0) + 1
    feedback.update(this, { action: 'avatar', loading: false, error: '', message: '' })
    try {
      await privacy.requireConsent()
      if (this.disposed || token !== session.getToken()) return
      const avatarUrl = await avatar.upload(path)
      if (this.disposed || token !== session.getToken()) return
      const member = { ...this.data.member, avatarUrl }
      wx.setStorageSync('mall_mini_member', member)
      feedback.update(this, { member })
      const avatarSrc = await avatar.load(avatarUrl, true)
      if (this.disposed || this.hidden || token !== session.getToken()) { avatar.release(avatarSrc); return }
      avatar.release(this.data.avatarSrc)
      feedback.update(this, { member, avatarSrc, message: '头像已更新' })
    } catch (error) { if (!this.disposed && token === session.getToken()) feedback.update(this, { error: error.message || '头像更新失败' }) }
    finally { avatar.release(path); if (!this.disposed) feedback.update(this, { action: '' }) }
  },
  async saveNickname(event) {
    if (this.data.action || this.data.loading || !this.data.member) return
    // Use the submitted native value: WeChat nickname moderation may clear it after blur.
    const nickname = String(event && event.detail && event.detail.value ? event.detail.value.nickname || '' : this.data.nickname).trim().replace(/\s+/g, ' ')
    if (!/^[\u3400-\u9fffA-Za-z0-9·_\- ]{2,20}$/.test(nickname)) { feedback.update(this, { error: '昵称需为2至20个字符，支持中文、字母、数字、空格、·、-和_' }); return }
    feedback.update(this, { action: 'nickname', error: '', message: '' })
    try {
      const member = await request({ url: '/shop/auth/nickname', method: 'PUT', data: { nickname } })
      if (!this.disposed && !this.hidden) feedback.update(this, { member: member || { ...this.data.member, nickname }, nickname, message: '昵称已保存' })
    } catch (error) { if (!this.disposed && !this.hidden) feedback.update(this, { error: error.message || '昵称保存失败' }) }
    finally { if (!this.disposed) feedback.update(this, { action: '' }) }
  },
  async setupAccount() {
    if (this.data.action || this.data.loading || !this.data.member || !this.data.canSetupAccount) return
    const username = this.data.username.trim()
    if (!/^[A-Za-z][A-Za-z0-9_]{3,19}$/.test(username)) { feedback.update(this, { error: '登录账号需为4至20位，以英文字母开头，仅支持字母、数字和下划线' }); return }
    const error = passwordError(this.data.password, username, String(this.data.member.phone || ''))
    if (error) { feedback.update(this, { error }); return }
    if (this.data.password !== this.data.confirmPassword) { feedback.update(this, { error: '两次输入的密码不一致' }); return }
    await this.saveCredentials('/shop/auth/account', { username, password: this.data.password }, 'account')
  },
  async changePassword() {
    if (this.data.action || this.data.loading || !this.data.member || this.data.canSetupAccount) return
    if (!this.data.currentPassword || this.data.currentPassword.length > 32) { feedback.update(this, { error: '请输入当前登录密码（不超过32位）' }); return }
    if (!/^\d{6}$/.test(this.data.smsCode)) { feedback.update(this, { error: '请输入绑定手机号收到的6位短信验证码' }); return }
    const error = passwordError(this.data.newPassword, String(this.data.member.username || ''), String(this.data.member.phone || ''))
    if (error) { feedback.update(this, { error }); return }
    if (this.data.newPassword !== this.data.confirmPassword) { feedback.update(this, { error: '两次输入的新密码不一致' }); return }
    if (this.data.newPassword === this.data.currentPassword) { feedback.update(this, { error: '新密码不能与当前密码相同' }); return }
    await this.saveCredentials('/shop/auth/password', { currentPassword: this.data.currentPassword, newPassword: this.data.newPassword, smsCode: this.data.smsCode }, 'password')
  },
  async saveCredentials(url, data, action) {
    feedback.update(this, { action, error: '', message: '' })
    try {
      // The shared request layer encrypts sensitive fields before wx.request.
      // No password / SMS code is placed in URLs, persistent storage or logs.
      await request({ url, method: 'PUT', data })
      session.clearSession()
      feedback.update(this, { ...EMPTY_SECRETS, member: null })
      if (!this.disposed && !this.hidden) {
        await feedback.toast({ title: '已保存，请重新登录', icon: 'none' })
        wx.redirectTo({ url: '/pages/login/index' })
      }
    } catch (error) {
      if (!this.disposed) feedback.update(this, { ...EMPTY_SECRETS, error: this.hidden ? '' : error.message || '保存失败，请重新填写后重试' })
    } finally { if (!this.disposed) feedback.update(this, { action: '' }) }
  },
  updateCountdown() {
    clearTimeout(this.countdownTimer)
    const countdown = Math.max(0, Math.ceil(((this.resendAt || 0) - Date.now()) / 1000))
    if (!this.disposed && !this.hidden) {
      feedback.update(this, { countdown })
      if (countdown > 0) this.countdownTimer = setTimeout(() => this.updateCountdown(), 1000)
    }
  },
  async sendCode() {
    if (this.data.action || this.data.loading || !this.data.member || this.data.canSetupAccount || this.data.sendingCode || (this.resendAt || 0) > Date.now()) return
    const phone = String(this.data.member.phone || '')
    if (!/^1[3-9]\d{9}$/.test(phone)) { feedback.update(this, { error: '绑定手机号不可用，请联系商城客服核验处理' }); return }
    feedback.update(this, { sendingCode: true, error: '', message: '' })
    try {
      // The server resolves the recipient again from the authenticated member.
      await request({ url: '/sms/send', method: 'POST', data: { phone, bizType: 8 } })
      this.resendAt = Date.now() + 60000
      this.updateCountdown()
      if (!this.disposed && !this.hidden) feedback.update(this, { message: '验证码已发送，5分钟内有效' })
    } catch (error) { if (!this.disposed && !this.hidden) feedback.update(this, { error: error.message || '验证码发送失败' }) }
    finally { if (!this.disposed) feedback.update(this, { sendingCode: false }) }
  }
})
