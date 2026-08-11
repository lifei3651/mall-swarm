import { hasShopSession } from '@/utils/shopSession'

export const AUTH_REQUIRED_EVENT = 'shop:auth-required'

export const notifyAuthRequired = (message = '请先登录') => {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT, { detail: { message } }))
}

export const loginRedirectLocation = (fullPath = '/') => ({
  name: 'Login',
  query: {
    ...(fullPath && fullPath !== '/login' ? { redirect: fullPath } : {}),
    authRequired: '1',
  },
})

export const requireShopSession = (router, fullPath = '/', message = '请先登录后再操作') => {
  if (hasShopSession()) return true
  notifyAuthRequired(message)
  router.push(loginRedirectLocation(fullPath))
  return false
}
