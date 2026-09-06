const request = require('./request')
const session = require('./session')
const invite = require('./invite')

const empty = () => ({ ready: false, membershipActive: false, canInvite: false, inviteCode: '', canViewWallet: false, canViewPayoutRecords: false })
async function load() {
  const token = session.getToken()
  if (!token) return empty()
  const data = await request({ url: '/shop/wechat-mini-program/member-capabilities' })
  if (session.getToken() !== token) throw new Error('登录状态已变化，请重新操作')
  if (!data || ['membershipActive', 'canInvite', 'canViewWallet', 'canViewPayoutRecords'].some((key) => typeof data[key] !== 'boolean')) {
    throw new Error('会员服务信息不完整，请重试')
  }
  const code = invite.normalizeInviteCode(data.inviteCode)
  return { ready: true, membershipActive: data.membershipActive, canInvite: data.membershipActive && data.canInvite && !!code,
    inviteCode: data.membershipActive && data.canInvite ? code : '', canViewWallet: data.canViewWallet, canViewPayoutRecords: data.canViewPayoutRecords }
}
module.exports = { empty, load }
