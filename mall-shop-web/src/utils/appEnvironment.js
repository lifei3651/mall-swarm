const configuredApiBase = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const configuredWebOrigin = (import.meta.env.VITE_PUBLIC_WEB_ORIGIN || '').replace(/\/$/, '')

export const isNativeApp = import.meta.env.VITE_NATIVE_APP === 'true'
export const apiBaseUrl = configuredApiBase
export const publicWebOrigin = configuredWebOrigin || window.location.origin

export const toPublicWebUrl = (path = '/') => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${publicWebOrigin}${normalizedPath}`
}

/**
 * 团队端遗留注册链接一律回到公开商城注册页，只传递格式正确的邀请码。
 * 不透传 redirect、手机号等参数，注册完成后固定进入公开商城首页。
 */
export const toPublicRegistrationUrl = (query = {}) => {
  const rawInviteCode = query?.inviteCode || query?.code || ''
  const inviteCode = String(rawInviteCode).trim().toUpperCase()
  const suffix = /^[A-Z0-9]{8}$/.test(inviteCode)
    ? `?inviteCode=${encodeURIComponent(inviteCode)}`
    : ''
  return `${toPublicWebUrl('/register')}${suffix}`
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
