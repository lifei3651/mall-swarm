const KEY = 'shop_pending_invitation_v3'
const MAX_AGE = 24 * 60 * 60 * 1000
export function pendingInvitation(storage = sessionStorage, now = Date.now()) {
  try {
    const value = JSON.parse(storage.getItem(KEY) || 'null')
    if (!value || !/^[A-Z0-9]{8}$/.test(value.code) || value.expiresAt <= now || value.expiresAt > now + MAX_AGE) {
      storage.removeItem(KEY); return ''
    }
    return value.code
  } catch (_) { storage.removeItem(KEY); return '' }
}
export function captureInvitation(query, storage = sessionStorage, now = Date.now()) {
  const raw = query?.inviteCode || query?.code
  if (typeof raw === 'string') {
    const code = raw.trim().toUpperCase()
    if (/^[A-Z0-9]{8}$/.test(code)) storage.setItem(KEY, JSON.stringify({ code, expiresAt: now + MAX_AGE }))
  }
  return pendingInvitation(storage, now)
}
export function clearInvitation(storage = sessionStorage) { storage.removeItem(KEY) }
let inFlight
export async function bindPendingInvitation({ hasSession, restoreSession, bind, owner, storage = sessionStorage }) {
  const code = pendingInvitation(storage)
  if (!code || inFlight) return inFlight
  inFlight = (async () => {
    if (!hasSession() && !await restoreSession()) return
    const expectedOwner = owner()
    try {
      const result = await bind(code)
      if (!hasSession() || owner() !== expectedOwner) return
      if (pendingInvitation(storage) === code) clearInvitation(storage)
      return result
    } catch (_) { /* Retain the share for an explicit retry without blocking login. */ }
  })().finally(() => { inFlight = null })
  return inFlight
}
