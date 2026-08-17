import { isTeamSurface } from '@/utils/appSurface'

const isNativeContainer = () => Boolean(window.Capacitor?.isNativePlatform?.())

export const registerPwa = () => {
  if (isTeamSurface || !import.meta.env.PROD || !('serviceWorker' in navigator) || isNativeContainer()) return
  const entry = document.querySelector('script[type="module"][src]')?.getAttribute('src') || 'app'
  const buildId = encodeURIComponent(entry.split('/').pop() || 'app')
  window.addEventListener('load', async () => {
    try {
      const registration = await navigator.serviceWorker.register(`/service-worker.js?build=${buildId}`, { updateViaCache: 'none' })
      await registration.update()
    } catch (error) {
      if (import.meta.env.DEV) console.warn('PWA registration failed', error)
    }
  }, { once: true })
}
