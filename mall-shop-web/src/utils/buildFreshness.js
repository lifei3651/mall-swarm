const REFRESH_KEY = 'lingqi_mall_latest_entry'

export const extractModuleEntry = (html) => {
  const scriptTag = String(html || '').match(/<script\b[^>]*\btype=["']module["'][^>]*>/i)?.[0] || ''
  return scriptTag.match(/\bsrc=["']([^"']+)["']/i)?.[1] || ''
}

const currentModuleEntry = () => document
  .querySelector('script[type="module"][src]')
  ?.getAttribute('src') || ''

let checking = false

export const checkForAppUpdate = async () => {
  if (checking || !['http:', 'https:'].includes(window.location.protocol)) return false
  checking = true
  try {
    const indexUrl = new URL('/', window.location.origin)
    indexUrl.searchParams.set('__app_check', Date.now().toString())
    const response = await fetch(indexUrl, { cache: 'no-store', headers: { Accept: 'text/html' } })
    if (!response.ok) return false

    const latestEntry = extractModuleEntry(await response.text())
    const activeEntry = currentModuleEntry()
    if (!latestEntry || !activeEntry || latestEntry === activeEntry) {
      sessionStorage.removeItem(REFRESH_KEY)
      return false
    }
    if (sessionStorage.getItem(REFRESH_KEY) === latestEntry) return false

    sessionStorage.setItem(REFRESH_KEY, latestEntry)
    const reloadUrl = new URL(window.location.href)
    reloadUrl.searchParams.set('__app_refresh', Date.now().toString())
    window.location.replace(reloadUrl.toString())
    return true
  } catch {
    return false
  } finally {
    checking = false
  }
}

export const startBuildFreshnessGuard = () => {
  const checkWhenVisible = () => {
    if (document.visibilityState === 'visible') void checkForAppUpdate()
  }
  window.addEventListener('pageshow', checkWhenVisible)
  window.addEventListener('focus', checkWhenVisible)
  document.addEventListener('visibilitychange', checkWhenVisible)
  window.setInterval(checkWhenVisible, 5 * 60 * 1000)
  void checkForAppUpdate()
}
