const auth = require('../../utils/auth')
const session = require('../../utils/session')
const invite = require('../../utils/invite')
const runtimeConfig = require('../../config/runtime')
const theme = require('../../utils/theme')

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
    '/pages/wallet/index': '查看钱包', '/pages/cart/index': '返回购物车',
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

Page({
  data: {
    ...theme.pageData(),
    loading: true, submitting: false, enabled: false, phoneEnabled: false,
    agreed: false, agreementRequired: false, privacyVersion: '', inviteCode: '', error: '',
    loginNotice: '', showLoginHelp: false, contextHint: ''
  },
  onLoad(options = {}) {
    theme.apply(this)
    try { this.redirect = decodeURIComponent(options.redirect || '') } catch (_) { this.redirect = '' }
    this.setData({ contextHint: loginContext(this.redirect) })
    this.loadRuntime()
  },
  async loadRuntime() {
    if (this.data.submitting) return
    const sequence = this._runtimeSequence = (this._runtimeSequence || 0) + 1
    this.setData({ loading: true, enabled: false, phoneEnabled: false, privacyVersion: '', error: '', loginNotice: '', showLoginHelp: false })
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
    if (!this.data.phoneEnabled) {
      this.setData({ error: '微信手机号快捷登录暂不可用，请稍后重试或联系商城客服。', loginNotice: '', showLoginHelp: true })
      return
    }
    const detail = event && event.detail || {}
    if (detail.errMsg !== 'getPhoneNumber:ok' || typeof detail.code !== 'string' || !detail.code.trim()) {
      const feedback = phoneAuthorizationFeedback(detail)
      this.setData({ error: feedback.error || '', loginNotice: feedback.notice || '', showLoginHelp: Boolean(feedback.error) })
      return
    }
    await this.executeLogin(detail.code)
  },
  async executeLogin(phoneCode) {
    if (this.data.submitting) return
    this.setData({ submitting: true, error: '', loginNotice: '', showLoginHelp: false })
    try {
      const result = await auth.login({ phoneCode, privacyConsentVersion: this.data.privacyVersion })
      if (result.phoneAuthorizationRequired) {
        this.setData({ loginNotice: '此微信尚未关联商城账号，请通过手机号验证后继续登录或注册。', showLoginHelp: !this.data.phoneEnabled })
        return
      }
      wx.showToast({ title: result.newMember ? '注册成功' : '登录成功', icon: 'success' })
      setTimeout(() => this.finish(), 450)
    } catch (error) { this.setData({ error: error && error.message || '登录失败，请重试或联系商城客服', showLoginHelp: true }) }
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
