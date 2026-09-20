const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const session = require('../../utils/session')
const theme = require('../../utils/theme')
const privacy = require('../../utils/privacy')
const avatar = require('../../utils/member-avatar')

const EMPTY_SECRETS = { password: '', currentPassword: '', newPassword: '', confirmPassword: '', smsCode: '' }
function passwordError(value, username, phone) {
  if (value.length < 6 || value.length > 32) return '登录密码需为6至32位'
  if (/\s/.test(value)) return '登录密码不能包含空格'
  const normalized = value.toLowerCase()
  if ((username && normalized.includes(username.toLowerCase())) || (phone && (normalized.includes(phone) || normalized.endsWith(phone.slice(-6))))) return '登录密码不能包含登录账号或手机号'
  return ''
}
function cancelled(error) { return /cancel/i.test(String(error && error.errMsg || error && error.message || '')) }
function accountIdentityFallback(member) {
  const username = String(member && member.username || '').trim()
  const phone = String(member && member.phone || '').trim()
  const canSetupLoginAccount = !username || username === phone
  return { accountMode: canSetupLoginAccount ? 'PHONE' : 'CUSTOM', accountDisplay: canSetupLoginAccount ? '手机号账号' : username,
    canSetupLoginAccount, inviterStatus: 'UNKNOWN', inviterName: '' }
}
function inviterDisplay(identity) {
  if (identity.inviterStatus === 'BOUND') return identity.inviterName || '商城会员'
  if (identity.inviterStatus === 'NONE') return '未绑定'
  if (identity.inviterStatus === 'INVALID') return '关系待核验'
  return '暂不可查询'
}
function chooseAlbumAvatar() {
  if (typeof wx.chooseMedia !== 'function') return Promise.reject(new Error('当前微信版本不支持头像选择，请升级微信后重试'))
  return new Promise((resolve, reject) => wx.chooseMedia({ count: 1, mediaType: ['image'], sourceType: ['album'], sizeType: ['compressed'],
    success(result) {
      const path = result && result.tempFiles && result.tempFiles[0] && result.tempFiles[0].tempFilePath
      if (path) resolve(path)
      else reject(new Error('没有读取到所选图片，请重新选择'))
    }, fail: reject
  }))
}
function cropAvatar(path) {
  if (typeof wx.cropImage !== 'function') return Promise.reject(new Error('当前微信版本不支持头像裁剪，请升级微信后重试'))
  return new Promise((resolve, reject) => wx.cropImage({ src: path, cropScale: '1:1',
    success(result) { result && result.tempFilePath ? resolve(result.tempFilePath) : reject(new Error('头像裁剪未完成，请重新选择')) }, fail: reject
  }))
}

Page({
  contact() { wx.navigateTo({ url: '/pages/legal/index?type=contact', fail: () => feedback.notice('客服页面暂时无法打开，请返回“我的”重试') }) },
  data: { ...theme.pageData(), ...EMPTY_SECRETS, loading: true, error: '', message: '', member: null,
    username: '', nickname: '', mode: 'profile', useWechatNickname: false, avatarSrc: avatar.fallback, maskedPhone: '', canSetupAccount: false,
    accountDisplay: '', inviterStatus: 'UNKNOWN', inviterDisplay: '暂不可查询', action: '', sendingCode: false, countdown: 0 },
  onLoad(options = {}) { theme.apply(this); const mode = ['password', 'nickname'].includes(options.mode) ? options.mode : 'profile'; this.setData({ mode }); if (wx.setNavigationBarTitle) wx.setNavigationBarTitle({ title: { password: '登录密码', nickname: '修改昵称', profile: '个人资料' }[mode] }) },
  onShow() {
    this.hidden = false
    theme.apply(this)
    this.updateCountdown()
    if (this.data.action) return
    if (auth.requireLogin(`/pages/account-security/index${this.data.mode === 'profile' ? '' : '?mode=' + this.data.mode}`)) return this.load()
    this.requestVersion = (this.requestVersion || 0) + 1
    feedback.update(this, { ...EMPTY_SECRETS, loading: false, member: null, nickname: '', username: '', maskedPhone: '', canSetupAccount: false,
      accountDisplay: '', inviterStatus: 'UNKNOWN', inviterDisplay: '暂不可查询' })
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
    const token = session.getToken()
    feedback.update(this, { loading: true, error: '' })
    try {
      const member = await request({ url: '/shop/auth/me' })
      if (this.disposed || this.hidden || token !== session.getToken() || version !== this.requestVersion) return
      if (!member || !member.id) throw new Error('账号信息加载失败，请重新登录')
      const phone = String(member.phone || '')
      let identity = accountIdentityFallback(member)
      try {
        const response = await request({ url: '/shop/auth/account-identity' })
        if (response && ['PHONE', 'CUSTOM'].includes(response.accountMode)) identity = { ...identity, ...response }
      } catch (_) {}
      if (this.disposed || this.hidden || token !== session.getToken() || version !== this.requestVersion) return
      feedback.update(this, { member, nickname: member.nickname || '', username: '', canSetupAccount: !!identity.canSetupLoginAccount,
        accountDisplay: identity.accountDisplay || accountIdentityFallback(member).accountDisplay,
        inviterStatus: identity.inviterStatus || 'UNKNOWN', inviterDisplay: inviterDisplay(identity),
        maskedPhone: /^1[3-9]\d{9}$/.test(phone) ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : '尚未绑定有效手机号' })
      const avatarSrc = await avatar.load(member.avatarUrl)
      if (this.disposed || this.hidden || token !== session.getToken() || version !== this.requestVersion) avatar.release(avatarSrc)
      else { avatar.release(this.data.avatarSrc); feedback.update(this, { avatarSrc }) }
    } catch (error) {
      if (!this.disposed && !this.hidden && version === this.requestVersion) feedback.update(this, { ...EMPTY_SECRETS, member: null, canSetupAccount: false,
        accountDisplay: '', inviterStatus: 'UNKNOWN', inviterDisplay: '暂不可查询', error: error.message || '账号信息加载失败' })
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
  editNickname() { if (!this.data.action) wx.navigateTo({ url: '/pages/account-security/index?mode=nickname' }) },
  async enableWechatNickname() {
    if (this.data.action) return
    try { await privacy.requireConsent(); if (!this.disposed) feedback.update(this, { useWechatNickname: true, error: '' }) }
    catch (error) { if (!this.disposed) feedback.update(this, { error: error.message, useWechatNickname: false }) }
  },
  async chooseAvatar() {
    if (this.data.action || !this.data.member) return
    const token = session.getToken()
    let selectedPath = ''
    let croppedPath = ''
    this.requestVersion = (this.requestVersion || 0) + 1
    feedback.update(this, { action: 'avatar', loading: false, error: '', message: '' })
    try {
      await privacy.requireConsent()
      if (this.disposed || token !== session.getToken()) return
      selectedPath = await chooseAlbumAvatar()
      if (this.disposed || this.hidden || token !== session.getToken()) return
      croppedPath = await cropAvatar(selectedPath)
      if (this.disposed || this.hidden || token !== session.getToken()) return
      const avatarUrl = await avatar.upload(croppedPath)
      if (this.disposed || token !== session.getToken()) return
      const member = { ...this.data.member, avatarUrl }
      wx.setStorageSync('mall_mini_member', member)
      feedback.update(this, { member, action: '' })
      if (!this.disposed && !this.hidden && token === session.getToken()) wx.navigateBack({ delta: 1 })
    } catch (error) {
      if (!cancelled(error) && !this.disposed && token === session.getToken()) feedback.update(this, { error: error.message || '头像更新失败' })
    } finally {
      avatar.release(selectedPath)
      if (croppedPath !== selectedPath) avatar.release(croppedPath)
      if (!this.disposed) feedback.update(this, { action: '' })
    }
  },
  async saveNickname(event) {
    if (this.data.action || this.data.loading || !this.data.member) return
    // Use the submitted native value: WeChat nickname moderation may clear it after blur.
    const nickname = String(event && event.detail && event.detail.value ? event.detail.value.nickname || '' : this.data.nickname).trim().replace(/\s+/g, ' ')
    if (!/^[\u3400-\u9fffA-Za-z0-9·_\- ]{2,20}$/.test(nickname)) { feedback.update(this, { error: '昵称需为2至20个字符，支持中文、字母、数字、空格、·、-和_' }); return }
    const token = session.getToken()
    feedback.update(this, { action: 'nickname', error: '', message: '' })
    try {
      const member = await request({ url: '/shop/auth/nickname', method: 'PUT', data: { nickname } })
      if (!this.disposed && !this.hidden && token === session.getToken()) feedback.update(this, { member: member || { ...this.data.member, nickname }, nickname, message: '昵称已保存' })
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
    const token = session.getToken()
    feedback.update(this, { action, error: '', message: '' })
    try {
      // The shared request layer encrypts sensitive fields before wx.request.
      // No password / SMS code is placed in URLs, persistent storage or logs.
      await request({ url, method: 'PUT', data })
      if (token !== session.getToken()) return
      session.clearSession()
      feedback.update(this, { ...EMPTY_SECRETS, member: null })
      if (!this.disposed && !this.hidden) {
        await feedback.success('已保存，请重新登录')
        if (!session.getToken()) wx.redirectTo({ url: '/pages/login/index' })
      }
    } catch (error) {
      if (!this.disposed) feedback.update(this, { ...EMPTY_SECRETS, error: this.hidden ? '' : error.message || '保存失败，请重新填写后重试' })
    } finally { if (!this.disposed) feedback.update(this, { action: '' }) }
  },
  loginPassword() { wx.navigateTo({ url: '/pages/account-security/index?mode=password' }) },
  changePhone() { wx.navigateTo({ url: '/pages/account-settings/index?section=phone' }) },
  paymentSecurity() { wx.navigateTo({ url: '/pages/account-settings/index?section=security' }) },
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
