const feedback = require('./feedback')
const auth = require('./auth')
const session = require('./session')
const invitation = require('./login-invitation')
const runtimeConfig = require('../config/runtime')
const theme = require('./theme')

function loginContext(redirect) {
  const [path, query = ''] = redirect.split('?')
  if (path === '/pages/orders/index') {
    const tab = (query.match(/(?:^|&)tab=([^&]*)/) || [])[1]
    const label = ({ 'pending-payment': '查看待支付订单', 'pending-shipment': '查看待发货订单',
      'pending-receipt': '查看待收货订单', 'after-sale': '查看退款与售后' })[tab]
    return typeof label === 'string' ? label : '查看我的订单'
  }
  const label = ({ '/pages/address/index': '管理收货地址', '/pages/account-security/index': '完善账号资料',
    '/pages/messages/index': '查看消息与提醒', '/pages/payout/index': '查看收款记录',
    '/pages/wallet/index': '查看钱包', '/pages/withdraw/index': '申请提现', '/pages/cart/index': '返回购物车',
    '/pages/checkout/index': '继续核对订单', '/pages/product/index': '返回商品详情' })[path]
  return typeof label === 'string' ? label : ''
}

// Native failure text varies by WeChat version. Never display the raw payload or
// infer certification status from a generic failure; unknown failures stay retryable.
function phoneAuthorizationFeedback(detail) {
  const message = typeof detail.errMsg === 'string' ? detail.errMsg.toLowerCase() : ''
  if (/cancel|user deny|auth deny|用户拒绝|取消/.test(message)) {
    return { notice: '已取消手机号授权，未完成登录。你可以重新选择手机号，或返回继续浏览。' }
  }
  if (/no permission|unauthorized|permission denied|not support|not available|无权限|未开通|不支持/.test(message)) {
    return { error: '微信手机号快捷登录暂不可用，请稍后重试或联系商城客服。' }
  }
  if (/network|timeout|网络|超时/.test(message)) {
    return { error: '网络连接未完成，请检查网络后重试手机号登录。' }
  }
  if (!message || message === 'getphonenumber:ok') {
    return { error: '未收到有效的手机号授权凭证，请重新选择手机号后重试。' }
  }
  return { error: '微信手机号授权未完成，请重新尝试；仍无法登录时可联系商城客服。' }
}

module.exports = {
  data: {
    ...theme.pageData(),
    ...invitation.data,
    loading: true, submitting: false, enabled: false, phoneEnabled: false,
    agreed: false, agreementRequired: false, privacyVersion: '', inviteCode: '', error: '',
    loginNotice: '', showLoginHelp: false, contextHint: ''
  },
  ...invitation.methods,
  onLoad(options = {}) {
    this._inactive = false
    theme.apply(this)
    try { this.redirect = decodeURIComponent(options.redirect || '') } catch (_) { this.redirect = '' }
    feedback.update(this, { contextHint: loginContext(this.redirect) })
    this.syncInvitation(true)
    return this.loadRuntime()
  },
  async loadRuntime() {
    if (this._inactive || this.data.submitting) return
    const sequence = this._runtimeSequence = (this._runtimeSequence || 0) + 1
    feedback.update(this, { loading: true, enabled: false, phoneEnabled: false, privacyVersion: '', error: '', loginNotice: '', showLoginHelp: false })
    try {
      const runtime = await auth.runtime()
      if (sequence !== this._runtimeSequence) return
      if (!runtime || runtime.enabled !== true) {
        feedback.update(this, { error: '商城登录服务暂未就绪，请稍后重试或联系商城客服' })
        return
      }
      if (runtime.enabled && runtime.privacyConsentVersion !== runtimeConfig.PRIVACY_CONSENT_VERSION) {
        feedback.update(this, {
          enabled: false,
          phoneEnabled: false,
          agreed: false,
          privacyVersion: '',
          error: '小程序隐私版本与服务器配置不一致，请联系商城客服更新小程序后再登录'
        })
        return
      }
      feedback.update(this, {
        enabled: true,
        phoneEnabled: runtime.phoneAuthorizationEnabled === true,
        privacyVersion: runtime.privacyConsentVersion
      })
    } catch (error) {
      if (sequence === this._runtimeSequence) feedback.update(this, { error: error && error.message || '商城配置加载失败，请重试' })
    } finally {
      if (sequence === this._runtimeSequence) { this._runtimeChecked = true; feedback.update(this, { loading: false }) }
    }
  },
  agreementChange(event) {
    const agreed = (event.detail.value || []).includes('agreed')
    feedback.update(this, { agreed, agreementRequired: false })
  },
  requireAgreement() { if (this.checkReady()) this.invitationReady() },
  onShow() {
    theme.apply(this)
    feedback.update(this, { logoFailed: false })
    if (!this.data.submitting) this.syncInvitation()
    if (this._runtimeChecked && !this.data.submitting && (!this.data.enabled || !this.data.phoneEnabled)) this.loadRuntime()
  },
  onUnload() {
    this._inactive = true
    this._runtimeSequence = (this._runtimeSequence || 0) + 1
    this._loginSequence = (this._loginSequence || 0) + 1
    this._inviteSequence = (this._inviteSequence || 0) + 1
  },
  logoError() { feedback.update(this, { logoFailed: true }) },
  checkReady() {
    if (this._inactive || this.data.loading || this.data.submitting) return false
    if (!this.data.enabled) { feedback.toast({ title: '商城登录服务暂未就绪，请重试', icon: 'none' }); return false }
    if (!this.data.agreed) {
      feedback.update(this, { agreementRequired: true })
      feedback.toast({ title: '请先阅读并同意相关协议', icon: 'none' })
      return false
    }
    return true
  },
  async returningLogin() {
    if (this.checkReady()) await this.executeLogin('')
  },
  async phoneLogin(event) {
    if (!this.checkReady()) return
    if (!this.data.phoneEnabled) {
      feedback.update(this, { error: '微信手机号快捷登录暂不可用，请稍后重试或联系商城客服。', loginNotice: '', showLoginHelp: true })
      return
    }
    const detail = event && event.detail || {}
    if (detail.errMsg !== 'getPhoneNumber:ok' || typeof detail.code !== 'string' || !detail.code.trim()) {
      const authorizationResult = phoneAuthorizationFeedback(detail)
      feedback.update(this, { error: authorizationResult.error || '', loginNotice: authorizationResult.notice || '', showLoginHelp: true })
      return
    }
    if (this.invitationReady()) await this.executeLogin(detail.code)
  },
  async executeLogin(phoneCode) {
    if (this._inactive || this.data.submitting) return
    const sequence = this._loginSequence = (this._loginSequence || 0) + 1
    const inviteCode = phoneCode ? this._verifiedInviteCode || '' : ''
    feedback.update(this, { submitting: true, error: '', loginNotice: '', showLoginHelp: false })
    try {
      const result = await auth.login({ phoneCode, inviteCode, privacyConsentVersion: this.data.privacyVersion })
      if (sequence !== this._loginSequence || this._inactive) return
      if (result && result.phoneAuthorizationRequired) {
        feedback.update(this, { loginNotice: '此微信尚未关联商城账号，请通过手机号验证后继续登录或注册。', showLoginHelp: !this.data.phoneEnabled })
        return
      }
      if (!result || typeof result.accessToken !== 'string' || !result.accessToken || session.getToken() !== result.accessToken) {
        throw new Error('登录未完成，请重试或联系商城客服')
      }
      feedback.toast({ title: result.newMember ? '注册成功' : '登录成功', icon: 'success' })
      if (result.newMember) this.redirect = '/pages/home/index'
      this.finish()
    } catch (error) {
      if (sequence === this._loginSequence && !this._inactive) feedback.update(this, { error: error && error.message || '登录失败，请重试或联系商城客服', showLoginHelp: true })
    } finally {
      if (sequence === this._loginSequence && !this._inactive) feedback.update(this, { submitting: false })
    }
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
}
