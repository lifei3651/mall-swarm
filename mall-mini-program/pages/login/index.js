const auth = require('../../utils/auth')
const session = require('../../utils/session')
const invite = require('../../utils/invite')
const runtimeConfig = require('../../config/runtime')
const theme = require('../../utils/theme')

Page({
  data: {
    ...theme.pageData(),
    loading: true, submitting: false, enabled: false, phoneEnabled: false,
    agreed: false, agreementRequired: false, privacyVersion: '', inviteCode: '', error: ''
  },
  onLoad(options = {}) {
    theme.apply(this)
    try { this.redirect = decodeURIComponent(options.redirect || '') } catch (_) { this.redirect = '' }
    this.loadRuntime()
  },
  async loadRuntime() {
    if (this.data.submitting) return
    const sequence = this._runtimeSequence = (this._runtimeSequence || 0) + 1
    this.setData({ loading: true, enabled: false, phoneEnabled: false, privacyVersion: '', error: '' })
    try {
      const runtime = await auth.runtime()
      if (sequence !== this._runtimeSequence) return
      if (!runtime || runtime.enabled !== true) {
        this.setData({ error: '商城登录服务暂未就绪，请稍后重试或联系商城客服' })
        return
      }
      if (runtime.enabled && runtime.privacyConsentVersion !== runtimeConfig.PRIVACY_CONSENT_VERSION) {
        this.setData({
          enabled: false,
          phoneEnabled: false,
          agreed: false,
          privacyVersion: '',
          inviteCode: invite.getPendingInvite(),
          error: '小程序隐私版本与服务器配置不一致，请联系商城客服更新小程序后再登录'
        })
        return
      }
      this.setData({
        enabled: true,
        phoneEnabled: runtime.phoneAuthorizationEnabled === true,
        privacyVersion: runtime.privacyConsentVersion,
        inviteCode: invite.getPendingInvite()
      })
    } catch (error) {
      if (sequence === this._runtimeSequence) this.setData({ error: error && error.message || '商城配置加载失败，请重试' })
    } finally {
      if (sequence === this._runtimeSequence) { this._runtimeChecked = true; this.setData({ loading: false }) }
    }
  },
  agreementChange(event) {
    const agreed = (event.detail.value || []).includes('agreed')
    this.setData({ agreed, agreementRequired: false })
  },
  requireAgreement() { this.checkReady() },
  onShow() {
    theme.apply(this)
    this.setData({ logoFailed: false })
    if (this._runtimeChecked && !this.data.submitting && (!this.data.enabled || !this.data.phoneEnabled)) this.loadRuntime()
  },
  onUnload() { this._runtimeSequence = (this._runtimeSequence || 0) + 1 },
  logoError() { this.setData({ logoFailed: true }) },
  checkReady() {
    if (this.data.loading || this.data.submitting) return false
    if (!this.data.enabled) { wx.showToast({ title: '商城登录服务暂未就绪，请重试', icon: 'none' }); return false }
    if (!this.data.agreed) {
      this.setData({ agreementRequired: true })
      wx.showToast({ title: '请先阅读并同意相关协议', icon: 'none' })
      return false
    }
    return true
  },
  async returningLogin() {
    if (this.checkReady()) await this.executeLogin('')
  },
  async phoneLogin(event) {
    if (!this.checkReady()) return
    if (!this.data.phoneEnabled) { wx.showToast({ title: '手机号快捷注册暂不可用', icon: 'none' }); return }
    if (event.detail.errMsg !== 'getPhoneNumber:ok' || !event.detail.code) {
      wx.showToast({ title: '需要手机号授权才能完成首次注册', icon: 'none' })
      return
    }
    await this.executeLogin(event.detail.code)
  },
  async executeLogin(phoneCode) {
    if (this.data.submitting) return
    this.setData({ submitting: true, error: '' })
    try {
      const result = await auth.login({ phoneCode, privacyConsentVersion: this.data.privacyVersion })
      if (result.phoneAuthorizationRequired) {
        wx.showToast({ title: '首次使用请选择手机号快捷登录 / 注册', icon: 'none' })
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
      const pagePath = this.redirect.split('?')[0]
      const tabPages = new Set(['/pages/home/index', '/pages/category/index', '/pages/cart/index', '/pages/profile/index'])
      if (tabPages.has(pagePath)) wx.switchTab({ url: pagePath })
      else wx.redirectTo({ url: this.redirect, fail: () => wx.switchTab({ url: '/pages/profile/index' }) })
    } else wx.switchTab({ url: '/pages/profile/index' })
  },
  openPrivacy() { wx.navigateTo({ url: '/pages/legal/index?type=privacy' }) },
  openAgreement() { wx.navigateTo({ url: '/pages/legal/index?type=agreement' }) }
})
