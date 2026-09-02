import { safeAdminRedirect } from '@/utils/safeRedirect'

export const PLATFORM_HOME_PATH = '/dashboard'
export const MERCHANT_HOME_PATH = '/merchant/home'

export const MERCHANT_WORKSPACE_PATHS = Object.freeze([
  MERCHANT_HOME_PATH,
  '/merchant/profile',
  '/change-password',
  '/shop/products',
  '/shop/service-addresses',
  '/shop/orders',
  '/shop/service-tickets',
  '/audit/merchant-finance',
])

const normalizePathname = (value) => {
  try {
    const pathname = new URL(String(value || ''), 'https://admin.invalid').pathname
    return pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname
  } catch {
    return ''
  }
}

export const isMerchantAccount = (admin) => Boolean(admin?.merchantId)

export const isMerchantWorkspacePath = (value) => (
  MERCHANT_WORKSPACE_PATHS.includes(normalizePathname(value))
)

export const adminHomePath = (admin) => (
  isMerchantAccount(admin) ? MERCHANT_HOME_PATH : PLATFORM_HOME_PATH
)

/**
 * 登录完成后只保留当前身份真正可访问的站内地址。
 * 后端权限仍是安全边界；这里负责避免商家误入平台页面空壳。
 */
export const resolveAdminRedirect = (value, admin) => {
  const fallback = adminHomePath(admin)
  const redirect = safeAdminRedirect(value, fallback)
  if (isMerchantAccount(admin)) {
    return isMerchantWorkspacePath(redirect) ? redirect : fallback
  }
  return normalizePathname(redirect) === MERCHANT_HOME_PATH ? fallback : redirect
}
