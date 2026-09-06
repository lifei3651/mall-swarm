const request = require('./request')
const session = require('./session')
const invite = require('./invite')

function wxLoginCode() {
  return new Promise((resolve, reject) => {
    wx.login({ timeout: 10000, success: ({ code }) => code ? resolve(code) : reject(new Error('微信登录凭证为空')), fail: reject })
  })
}

async function runtime() {
  return request({ url: '/shop/wechat-mini-program/runtime' })
}

async function login({ phoneCode = '', privacyConsentVersion, inviteCode = '' }) {
  const previousToken = session.getToken()
  const loginCode = await wxLoginCode()
  if (session.getToken() !== previousToken) throw new Error('登录状态已变化，请重新操作')
  const data = await request({
    url: '/shop/wechat-mini-program/auth/login',
    method: 'POST',
    data: {
      loginCode,
      phoneCode: phoneCode || undefined,
      inviteCode: invite.normalizeInviteCode(inviteCode) || undefined,
      privacyAgreed: true,
      privacyConsentVersion
    }
  })
  if (session.getToken() !== previousToken) throw new Error('登录状态已变化，请重新操作')
  if (data && data.accessToken) {
    session.saveSession(data)
    invite.clearPendingInvite()
  }
  return data
}

function requireLogin(redirect) {
  if (session.getToken()) return true
  const url = redirect ? `/pages/login/index?redirect=${encodeURIComponent(redirect)}` : '/pages/login/index'
  wx.navigateTo({ url })
  return false
}

module.exports = { runtime, login, requireLogin }
