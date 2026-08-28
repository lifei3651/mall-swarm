/** 登录后只允许跳转到当前管理端的站内路由。 */
export const safeAdminRedirect = (value, fallback = '/dashboard') => {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return fallback
  if (value.includes('\\') || /[\u0000-\u001f\u007f]/.test(value)) return fallback
  try {
    let decoded = value
    for (let index = 0; index < 2; index += 1) {
      const next = decodeURIComponent(decoded)
      if (next === decoded) break
      decoded = next
    }
    if (decoded.startsWith('//') || decoded.includes('\\') || /[\u0000-\u001f\u007f]/.test(decoded)) return fallback
    const parsed = new URL(value, 'https://admin.invalid')
    if (parsed.origin !== 'https://admin.invalid'
      || ['/login', '/merchant/login', '/platform/login'].includes(parsed.pathname)) return fallback
    return `${parsed.pathname}${parsed.search}${parsed.hash}`
  } catch {
    return fallback
  }
}
