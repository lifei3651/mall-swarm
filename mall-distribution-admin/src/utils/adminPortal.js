export const ADMIN_PORTAL_PLATFORM = 'PLATFORM'
export const ADMIN_PORTAL_MERCHANT = 'MERCHANT'

const ADMIN_PORTAL_KEY = 'admin_portal'

export const normalizeAdminPortal = (value, fallback = ADMIN_PORTAL_MERCHANT) => {
  const normalized = String(value || '').trim().toUpperCase()
  return [ADMIN_PORTAL_PLATFORM, ADMIN_PORTAL_MERCHANT].includes(normalized) ? normalized : fallback
}

export const adminPortalLoginPath = (portal) => (
  normalizeAdminPortal(portal) === ADMIN_PORTAL_PLATFORM ? '/platform/login' : '/merchant/login'
)

export const adminPortalForAccount = (admin) => (
  admin?.merchantId ? ADMIN_PORTAL_MERCHANT : ADMIN_PORTAL_PLATFORM
)

export const readAdminPortal = () => {
  try {
    return normalizeAdminPortal(localStorage.getItem(ADMIN_PORTAL_KEY))
  } catch {
    return ADMIN_PORTAL_MERCHANT
  }
}

export const saveAdminPortal = (portal) => {
  const normalized = normalizeAdminPortal(portal)
  try {
    localStorage.setItem(ADMIN_PORTAL_KEY, normalized)
  } catch {
    // 浏览器禁用本地存储时，只影响下次失效后的返回入口。
  }
  return normalized
}
