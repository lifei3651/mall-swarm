import { isNativeApp, toPublicWebUrl } from '@/utils/appEnvironment'

export const currentAndroidVersionCode = Number(import.meta.env.VITE_ANDROID_VERSION_CODE || 0)
export const currentAndroidVersionName = import.meta.env.VITE_ANDROID_VERSION_NAME || ''

const normalizeRelease = (release = {}) => ({
  versionCode: Number(release.versionCode || 0),
  versionName: String(release.versionName || ''),
  downloadUrl: new URL(release.downloadUrl || '/app-download', toPublicWebUrl('/')).toString(),
  sha256: String(release.sha256 || ''),
  required: release.required === true,
  notes: Array.isArray(release.notes) ? release.notes.filter(Boolean) : [],
  published: release.published === true,
  channel: String(release.channel || ''),
})

export const fetchAndroidRelease = async () => {
  const response = await fetch(`${toPublicWebUrl('/downloads/android-release.json')}?t=${Date.now()}`, {
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw new Error('暂时无法获取安卓版本信息')
  return normalizeRelease(await response.json())
}

export const hasAndroidUpdate = (release) => (
  isNativeApp
  && release?.published === true
  && currentAndroidVersionCode > 0
  && Number(release?.versionCode || 0) > currentAndroidVersionCode
)

export const openAndroidDownload = async (downloadUrl) => {
  const url = new URL(downloadUrl || '/app-download', toPublicWebUrl('/')).toString()
  if (isNativeApp) {
    const { Browser } = await import('@capacitor/browser')
    await Browser.open({ url })
    return
  }
  window.location.assign(url)
}
