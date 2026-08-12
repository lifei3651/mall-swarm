const SESSION_EXPIRE_KEY = 'admin_session_expire_time'
const SESSION_NOTICE_KEY = 'admin_session_notice'
export const ADMIN_SESSION_EXPIRED_EVENT = 'admin-session-expired'

let redirectingToLogin = false

const safeSessionStorageSet = (key, value) => {
  try {
    window.sessionStorage.setItem(key, value)
  } catch {
    // 极少数浏览器禁用会话存储时，仍继续执行退出和跳转。
  }
}

export const clearAdminSessionStorage = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('admin_session_present')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('permissions')
  localStorage.removeItem(SESSION_EXPIRE_KEY)
}

export const saveAdminSessionExpireTime = (expireTime) => {
  if (expireTime) {
    localStorage.setItem(SESSION_EXPIRE_KEY, expireTime)
  } else {
    localStorage.removeItem(SESSION_EXPIRE_KEY)
  }
}

export const isAdminSessionExpired = (now = Date.now()) => {
  const raw = localStorage.getItem(SESSION_EXPIRE_KEY)
  if (!raw) return false
  const expireAt = Date.parse(raw)
  return Number.isFinite(expireAt) && expireAt <= now
}

export const consumeAdminSessionNotice = () => {
  try {
    const notice = window.sessionStorage.getItem(SESSION_NOTICE_KEY) || ''
    window.sessionStorage.removeItem(SESSION_NOTICE_KEY)
    return notice
  } catch {
    return ''
  }
}

export const expireAdminSession = (message = '后台登录已失效，请重新登录') => {
  clearAdminSessionStorage()
  safeSessionStorageSet(SESSION_NOTICE_KEY, message)

  // 同步清理 Pinia 中仍保留的登录状态以及页面上可能残留的弹窗/遮罩。
  // 事件监听失败也不影响下面的强制登录页跳转。
  try {
    window.dispatchEvent(new CustomEvent(ADMIN_SESSION_EXPIRED_EVENT, { detail: { message } }))
  } catch {
    // 部分旧浏览器不支持 CustomEvent 时继续跳转即可。
  }
  if (redirectingToLogin) return

  const base = (import.meta.env.BASE_URL || '/').replace(/\/$/, '')
  const loginPath = `${base}/login`
  if (window.location.pathname === loginPath) return

  redirectingToLogin = true
  const currentPath = window.location.pathname.startsWith(base)
    ? window.location.pathname.slice(base.length) || '/dashboard'
    : '/dashboard'
  const redirect = `${currentPath}${window.location.search}${window.location.hash}`
  const target = `${loginPath}?redirect=${encodeURIComponent(redirect)}`
  try {
    window.location.replace(target)
  } catch {
    // replace 极少数情况下可能被浏览器扩展拦截，assign 作为兜底。
    window.location.assign(target)
  }
}
