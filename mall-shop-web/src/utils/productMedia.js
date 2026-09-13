const configuredPublicOrigin = String(import.meta.env?.VITE_PUBLIC_WEB_ORIGIN || '').replace(/\/$/, '')

export const productCardImage = (value) => {
  const url = String(value || '').trim()
  if (!url || /[?&]variant=card(?:&|$)/.test(url)) return url
  const browserOrigin = typeof window === 'undefined' ? '' : window.location.origin
  const internal = url.startsWith('/api/shop/media/images/')
    || url.startsWith('/shop/media/images/')
    || (configuredPublicOrigin && url.startsWith(`${configuredPublicOrigin}/api/shop/media/images/`))
    || (browserOrigin && url.startsWith(`${browserOrigin}/api/shop/media/images/`))
  return internal ? `${url}${url.includes('?') ? '&' : '?'}variant=card` : url
}
