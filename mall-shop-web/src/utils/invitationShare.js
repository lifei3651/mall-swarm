// Outgoing links use the authenticated sender's code, never a received invite.
export async function invitationShareUrl(path, dependencies) {
  let deps = dependencies
  if (!deps) {
    const [session, api, environment] = await Promise.all([
      import('./shopSession.js'), import('../api/shop.js'), import('./appEnvironment.js'),
    ])
    deps = {
      toUrl: environment.toPublicWebUrl,
      hasSession: session.hasShopSession,
      restoreSession: session.restoreShopSession,
      owner: () => { try { return JSON.parse(localStorage.getItem('shop_member') || 'null')?.id } catch (_) { return null } },
      loadInviteInfo: api.getInviteInfo,
    }
  }
  const url = deps.toUrl(path)
  try {
    if (!deps.hasSession() && !await deps.restoreSession()) return url
    const owner = deps.owner()
    if (!owner || !deps.hasSession()) return url
    const payload = await deps.loadInviteInfo()
    if (!deps.hasSession() || deps.owner() !== owner) return url
    const info = payload?.data
    const code = String(info?.inviteCode || '').trim().toUpperCase()
    if (info?.invitationEnabled !== true || !/^[A-Z0-9]{8}$/.test(code)) return url
    return `${url}${url.includes('?') ? '&' : '?'}inviteCode=${encodeURIComponent(code)}`
  } catch (_) { return url }
}
