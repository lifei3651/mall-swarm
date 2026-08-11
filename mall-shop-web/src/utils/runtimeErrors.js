const recentReports = new Map()

const clean = (value, max = 500) => String(value || '')
  .replace(/([?&](?:token|code|password|secret|sign)=)[^&#\s]+/gi, '$1***')
  .replace(/Bearer\s+[A-Za-z0-9._~+\/-]+=*/gi, 'Bearer ***')
  .replace(/[\r\n\t]+/g, ' ')
  .slice(0, max)

const routePath = (router) => clean(router?.currentRoute?.value?.path || window.location.pathname, 180)

const report = (router, appName, source, error, info = '') => {
  const message = clean(error?.message || error || '未知前端错误')
  const fingerprint = `${source}:${message}:${routePath(router)}`
  const now = Date.now()
  if (now - (recentReports.get(fingerprint) || 0) < 30_000) return
  recentReports.set(fingerprint, now)

  const payload = {
    app: appName,
    source,
    name: clean(error?.name || 'Error', 80),
    message,
    route: routePath(router),
    info: clean(info, 180),
  }
  fetch('/api/shop/client-errors', {
    method: 'POST',
    credentials: 'same-origin',
    keepalive: true,
    headers: { 'Content-Type': 'application/json', 'X-Shop-Client': 'storefront' },
    body: JSON.stringify(payload),
  }).catch(() => {})
}

export const installGlobalErrorHandling = (app, router, appName) => {
  app.config.errorHandler = (error, _instance, info) => {
    if (import.meta.env.DEV) console.error(error)
    report(router, appName, 'vue', error, info)
  }
  window.addEventListener('error', (event) => report(router, appName, 'window', event.error || event.message))
  window.addEventListener('unhandledrejection', (event) => report(router, appName, 'promise', event.reason))
}
