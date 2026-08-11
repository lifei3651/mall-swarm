const recentReports = new Map()

const clean = (value, max = 500) => String(value || '')
  .replace(/([?&](?:token|code|password|secret|sign)=)[^&#\s]+/gi, '$1***')
  .replace(/Bearer\s+[A-Za-z0-9._~+\/-]+=*/gi, 'Bearer ***')
  .replace(/[\r\n\t]+/g, ' ')
  .slice(0, max)

const report = (router, source, error, info = '') => {
  const message = clean(error?.message || error || '未知前端错误')
  const route = clean(router?.currentRoute?.value?.path || window.location.pathname, 180)
  const fingerprint = `${source}:${message}:${route}`
  const now = Date.now()
  if (now - (recentReports.get(fingerprint) || 0) < 30_000) return
  recentReports.set(fingerprint, now)
  fetch('/api/shop/client-errors', {
    method: 'POST', credentials: 'same-origin', keepalive: true,
    headers: { 'Content-Type': 'application/json', 'X-Shop-Client': 'storefront' },
    body: JSON.stringify({ app: 'admin', source, name: clean(error?.name || 'Error', 80), message, route, info: clean(info, 180) }),
  }).catch(() => {})
}

export const installGlobalErrorHandling = (app, router) => {
  app.config.errorHandler = (error, _instance, info) => {
    if (import.meta.env.DEV) console.error(error)
    report(router, 'vue', error, info)
  }
  window.addEventListener('error', (event) => report(router, 'window', event.error || event.message))
  window.addEventListener('unhandledrejection', (event) => report(router, 'promise', event.reason))
}
