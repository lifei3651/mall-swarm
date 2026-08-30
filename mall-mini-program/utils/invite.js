const STORAGE_KEY = 'mall_pending_invite_code'
const INVITE_PATTERN = /^[A-Z0-9]{8}$/

function normalizeInviteCode(value) {
  const code = String(value || '').trim().toUpperCase()
  return INVITE_PATTERN.test(code) ? code : ''
}

function decodeScene(scene) {
  if (!scene) return ''
  try {
    const decoded = decodeURIComponent(scene)
    if (!decoded.includes('=')) return normalizeInviteCode(decoded)
    const values = {}
    decoded.split('&').forEach((entry) => {
      const parts = entry.split('=', 2)
      if (parts.length === 2) values[parts[0]] = parts[1]
    })
    return normalizeInviteCode(values.inviteCode || values.invite)
  } catch (_) {
    return ''
  }
}

function captureLaunchInvite(options = {}) {
  const query = options.query || {}
  const code = normalizeInviteCode(query.inviteCode || query.invite) || decodeScene(query.scene)
  if (code && typeof wx !== 'undefined') wx.setStorageSync(STORAGE_KEY, code)
  return code
}

function getPendingInvite() {
  if (typeof wx === 'undefined') return ''
  return normalizeInviteCode(wx.getStorageSync(STORAGE_KEY))
}

function clearPendingInvite() {
  if (typeof wx !== 'undefined') wx.removeStorageSync(STORAGE_KEY)
}

module.exports = { normalizeInviteCode, decodeScene, captureLaunchInvite, getPendingInvite, clearPendingInvite }
