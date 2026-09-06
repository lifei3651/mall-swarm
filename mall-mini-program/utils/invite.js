const runtime = require('../config/runtime')
const STORAGE_KEY = `mall_pending_invite_v2:${runtime.API_BASE_URL}`
const MAX_AGE = 24 * 60 * 60 * 1000
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
    const values = Object.create(null)
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
  if (typeof wx === 'undefined') return ''
  if (wx.getStorageSync('mall_mini_access_token')) { clearPendingInvite(); return '' }
  const query = options.query || {}
  const code = normalizeInviteCode(query.inviteCode || query.invite) || decodeScene(query.scene)
  if (code) {
    const state = getState()
    if (!state.selected) write({ selected: { code, source: 'link' }, candidate: null, expiresAt: Date.now() + MAX_AGE })
    else if (state.selected.code !== code) write({ ...state, candidate: { code, source: 'link' } })
  } else if (query.inviteCode || query.invite || query.scene) {
    const state = getState()
    // An explicitly supplied but malformed invitation must not silently become an uninvited registration.
    write(state.selected ? { ...state, candidate: { code: '', source: 'link', invalid: true } } : emptyState(false, true))
  }
  return code
}

function emptyState(expired = false, invalid = false) { return { selected: null, candidate: null, expiresAt: 0, expired, invalid } }
function write(state) { wx.setStorageSync(STORAGE_KEY, state) }
function getState() {
  if (typeof wx === 'undefined') return emptyState()
  // Old string-only records have no trustworthy age or source; never migrate them.
  const legacy = wx.getStorageSync('mall_pending_invite_code')
  wx.removeStorageSync('mall_pending_invite_code')
  const state = wx.getStorageSync(STORAGE_KEY)
  if (state && state.invalid === true) return emptyState(false, true)
  if ((state && state.expired === true) || (!state && legacy)) { write(emptyState(true)); return emptyState(true) }
  if (state && Number.isFinite(state.expiresAt) && state.expiresAt > 0 && state.expiresAt <= Date.now()) {
    write(emptyState(true))
    return emptyState(true)
  }
  if (!state || !Number.isFinite(state.expiresAt) || state.expiresAt <= Date.now()
    || state.expiresAt > Date.now() + MAX_AGE || !state.selected
    || !normalizeInviteCode(state.selected.code)) {
    wx.removeStorageSync(STORAGE_KEY)
    return emptyState()
  }
  return { selected: { code: normalizeInviteCode(state.selected.code), source: state.selected.source === 'manual' ? 'manual' : 'link' },
    candidate: state.candidate && normalizeInviteCode(state.candidate.code) ? { code: normalizeInviteCode(state.candidate.code), source: 'link' }
      : state.candidate && state.candidate.invalid === true ? { code: '', source: 'link', invalid: true } : null,
    expiresAt: state.expiresAt }
}
function getPendingInvite() {
  return getState().selected?.code || ''
}
function chooseCandidate(useNew) {
  const state = getState()
  if (!state.selected) return
  if (useNew && (!state.candidate || state.candidate.invalid)) return
  write({ ...state, selected: useNew ? state.candidate : state.selected, candidate: null })
}
function setManualInvite(value) {
  const code = normalizeInviteCode(value)
  if (!code) return false
  write({ selected: { code, source: 'manual' }, candidate: null, expiresAt: Date.now() + MAX_AGE })
  return true
}

function clearPendingInvite() {
  if (typeof wx !== 'undefined') { wx.removeStorageSync(STORAGE_KEY); wx.removeStorageSync('mall_pending_invite_code') }
}

module.exports = { normalizeInviteCode, decodeScene, captureLaunchInvite, getPendingInvite, clearPendingInvite, getState, chooseCandidate, setManualInvite }
