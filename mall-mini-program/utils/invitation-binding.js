const request = require('./request')
const session = require('./session')
const invite = require('./invite')
const feedback = require('./feedback')
let inFlight
async function bindPendingInvite(preferredCode = '') {
  const token = session.getToken(), state = invite.getState(), code = invite.normalizeInviteCode(preferredCode) || state.selected?.code
  if (!token || !code || (!preferredCode && (state.candidate || state.invalid || state.expired))) return
  if (inFlight) return inFlight
  inFlight = (async () => {
    try {
      const result = await request({ url: '/shop/invite/bind', method: 'POST', data: { inviteCode: code } })
      if (session.getToken() !== token) return
      if (invite.getPendingInvite() === code) invite.clearPendingInvite()
      if (result === 'BOUND') feedback.toast({ title: '邀请关系已绑定', icon: 'none' })
    } catch (_) {
      if (session.getToken() === token) feedback.toast({ title: '邀请暂未绑定，请重新打开好友分享重试', icon: 'none' })
    }
  })().finally(() => { inFlight = null })
  return inFlight
}
module.exports = { bindPendingInvite }
