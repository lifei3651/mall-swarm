/** 登录或敏感操作完成后只允许跳转到当前商城的站内路由。 */
export const safeShopRedirect = (value, fallback = '/') => {
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
    const parsed = new URL(value, 'https://shop.invalid')
    if (parsed.origin !== 'https://shop.invalid' || ['/login', '/register'].includes(parsed.pathname)) return fallback
    return `${parsed.pathname}${parsed.search}${parsed.hash}`
  } catch {
    return fallback
  }
}
