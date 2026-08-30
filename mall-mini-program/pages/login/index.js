const auth = require('../../utils/auth')
const session = require('../../utils/session')
const invite = require('../../utils/invite')
const runtimeConfig = require('../../config/runtime')

Page({
  data: {
    loading: true, submitting: false, enabled: false, phoneEnabled: false,
    agreed: false, privacyVersion: '', inviteCode: '', error: ''
  },
  onLoad(options) {
    try { this.redirect = decodeURIComponent(options.redirect || '') } catch (_) { this.redirect = '' }
    this.loadRuntime()
  },
  async loadRuntime() {
    try {
      const runtime = await auth.runtime()
      if (runtime.enabled && runtime.privacyConsentVersion !== runtimeConfig.PRIVACY_CONSENT_VERSION) {
        this.setData({
          enabled: false,
          phoneEnabled: false,
          privacyVersion: '',
          inviteCode: invite.getPendingInvite(),
          error: '小程序隐私版本与服务器配置不一致，请联系商城客服更新小程序后再登录'
        })
        return
      }
      this.setData({
        enabled: runtime.enabled,
        phoneEnabled: runtime.phoneAuthorizationEnabled,
        privacyVersion: runtime.privacyConsentVersion,
        inviteCode: invite.getPendingInvite()
      })
    } catch (error) { this.setData({ error: error.message }) }
    finally { this.setData({ loading: false }) }
  },
  toggleAgreement() { this.setData({ agreed: !this.data.agreed }) },
  checkReady() {
    if (!this.data.enabled) { wx.showToast({ title: '当前客户尚未开通小程序登录', icon: 'none' }); return false }
    if (!this.data.agreed) { wx.showToast({ title: '请先阅读并同意隐私政策', icon: 'none' }); return false }
    return true
  },
  async returningLogin() {
    if (this.checkReady()) await this.executeLogin('')
  },
  async phoneLogin(event) {
    if (!this.checkReady()) return
    if (event.detail.errMsg !== 'getPhoneNumber:ok' || !event.detail.code) {
      wx.showToast({ title: '需要手机号授权才能完成首次注册', icon: 'none' })
      return
    }
    await this.executeLogin(event.detail.code)
  },
  async executeLogin(phoneCode) {
    this.setData({ submitting: true, error: '' })
    try {
      const result = await auth.login({ phoneCode, privacyConsentVersion: this.data.privacyVersion })
      if (result.phoneAuthorizationRequired) {
        wx.showToast({ title: '首次登录请点击手机号一键注册', icon: 'none' })
        return
      }
      wx.showToast({ title: result.newMember ? '注册成功' : '登录成功', icon: 'success' })
      setTimeout(() => this.finish(), 450)
    } catch (error) { this.setData({ error: error.message || '登录失败' }) }
    finally { this.setData({ submitting: false }) }
  },
  finish() {
    if (!session.getToken()) return
    if (this.redirect && this.redirect.startsWith('/pages/')) {
      wx.redirectTo({ url: this.redirect, fail: () => wx.switchTab({ url: '/pages/profile/index' }) })
    } else wx.switchTab({ url: '/pages/profile/index' })
  },
  openPrivacy() {
    wx.showModal({
      title: '隐私保护说明',
      content: '仅在您主动授权后取得微信绑定手机号，用于创建或匹配商城账号。微信标识只在服务器加密保存，不展示给其他用户，也不会返回到小程序页面。',
      showCancel: false
    })
  }
})
