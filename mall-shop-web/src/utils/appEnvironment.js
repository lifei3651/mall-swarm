const configuredApiBase = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const configuredWebOrigin = (import.meta.env.VITE_PUBLIC_WEB_ORIGIN || '').replace(/\/$/, '')

export const isNativeApp = import.meta.env.VITE_NATIVE_APP === 'true'
export const apiBaseUrl = configuredApiBase
export const publicWebOrigin = configuredWebOrigin || window.location.origin

export const toPublicWebUrl = (path = '/') => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${publicWebOrigin}${normalizedPath}`
}

export const resolvePublicMediaUrls = (value) => {
  if (!isNativeApp || !configuredWebOrigin || value == null) return value

  if (typeof value === 'string') {
    return value.startsWith('/api/shop/media/') ? `${configuredWebOrigin}${value}` : value
  }

  if (Array.isArray(value)) return value.map(resolvePublicMediaUrls)

  if (typeof value === 'object') {
    Object.keys(value).forEach((key) => {
      value[key] = resolvePublicMediaUrls(value[key])
    })
  }

  return value
}
