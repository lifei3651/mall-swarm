export const AUTH_REQUIRED_EVENT = 'shop:auth-required'

export const notifyAuthRequired = (message = '请先登录') => {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT, { detail: { message } }))
}

export const loginRedirectLocation = (fullPath = '/') => ({
  name: 'Login',
  query: fullPath && fullPath !== '/login' ? { redirect: fullPath } : {},
})
