const feedback = require('./feedback')
const session = require('./session')
const capabilities = require('./member-capabilities')

function hide(page) {
  page._shareSequence = (page._shareSequence || 0) + 1
  page._shareState = null
  if (typeof wx.hideShareMenu === 'function') wx.hideShareMenu({})
}
async function prepare(page) {
  hide(page)
  const sequence = page._shareSequence, token = session.getToken()
  page.setData({ shareReady: false, shareError: '' })
  try {
    const rights = token ? await capabilities.load() : capabilities.empty()
    if (sequence !== page._shareSequence || token !== session.getToken() || page.data.loginVisible) return
    page._shareState = { token, code: rights.canInvite ? rights.inviteCode : '' }
    page.setData({ shareReady: true })
    if (typeof wx.showShareMenu === 'function') wx.showShareMenu({ menus: ['shareAppMessage'] })
    return rights
  } catch (_) {
    if (sequence === page._shareSequence) page.setData({ shareReady: false, shareError: '会员服务暂未核对成功，请重试' })
  }
}
function message(page, path, title) {
  const state = page._shareState
  const ready = state && state.token === session.getToken()
  // Only the current sender's server-verified code; never forward a captured recipient code.
  const code = ready ? state.code : ''
  if (!ready) feedback.toast({ title: '分享身份已变化，请返回重试', icon: 'none' })
  return { title: String(title || '商城').slice(0, 60), path: path + (code ? `${path.includes('?') ? '&' : '?'}inviteCode=${encodeURIComponent(code)}` : '') }
}
module.exports = { prepare, hide, message }
