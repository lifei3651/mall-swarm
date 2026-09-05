const request = require('./request')
const display = require('./display-config')

const STORAGE_KEY = 'mall_mini_theme_color'
const DEFAULT_COLOR = '#e7193f'
let brandPromise = null
let refreshedAt = 0
let revision = 0

function opaqueColor(value, fallback) {
  const valid = display.color(value, '')
  if (!valid) return fallback
  let channels, alpha = 1
  if (valid.startsWith('#')) {
    const hex = valid.length === 4 ? valid.slice(1).split('').map((part) => part + part).join('') : valid.slice(1)
    channels = [0, 2, 4].map((index) => parseInt(hex.slice(index, index + 2), 16))
    if (hex.length === 8) alpha = parseInt(hex.slice(6), 16) / 255
  } else {
    const parts = valid.slice(valid.indexOf('(') + 1, -1).split(',').map(Number)
    channels = parts.slice(0, 3); alpha = parts.length === 4 ? parts[3] : 1
  }
  return '#' + channels.map((channel) => Math.round(channel * alpha + 255 * (1 - alpha)).toString(16).padStart(2, '0')).join('')
}
function normalizeColor(value) { return opaqueColor(value, DEFAULT_COLOR) }

function softColor(value) {
  const color = normalizeColor(value)
  const channels = [1, 3, 5].map((index) => Number.parseInt(color.slice(index, index + 2), 16))
  return `#${channels.map((channel) => Math.round(channel * 0.1 + 255 * 0.9).toString(16).padStart(2, '0')).join('')}`
}

function nativeColor(value) {
  return opaqueColor(value, '#ffffff')
}

function currentColor() {
  try {
    const app = getApp()
    const globalColor = app && app.globalData && app.globalData.brand && app.globalData.brand.themeColor
    if (globalColor) return normalizeColor(globalColor)
    return normalizeColor(wx.getStorageSync(STORAGE_KEY))
  } catch (_) {
    return DEFAULT_COLOR
  }
}

function currentBrand() {
  try { return getApp().globalData.brand || { themeColor: currentColor() } } catch (_) { return { themeColor: currentColor() } }
}

function pageData(value) {
  const brand = typeof value === 'object' && value ? value : { ...currentBrand(), ...(value ? { themeColor: value } : {}) }
  const colors = display.palette(brand)
  const themeColor = normalizeColor(colors.primary)
  const themeSoftColor = softColor(themeColor)
  const variables = { brand: themeColor, 'brand-soft': themeSoftColor, canvas: colors.pageBg, header: colors.headerBg, paper: colors.cardBg, ink: colors.textColor, muted: colors.mutedColor, price: colors.priceColor, accent: colors.accentColor, line: colors.lineColor, button: colors.buttonBg, radius: colors.radius }
  return { themeColor, themeSoftColor, themeStyle: Object.entries(variables).map(([key, val]) => `--${key}: ${val}`).join(';') + `;background:${colors.pageBg};color:${colors.textColor}`, bottomNav: display.navigation(brand.displayConfig || {}), ...display.category(brand.displayConfig || {}) }
}

function remember(brand) {
  revision++
  const palette = pageData(brand || {})
  refreshedAt = Date.now()
  try {
    const app = getApp()
    if (app && app.globalData) app.globalData.brand = brand || { themeColor: palette.themeColor }
    wx.setStorageSync(STORAGE_KEY, palette.themeColor)
    if (wx.setTabBarStyle) wx.setTabBarStyle({ selectedColor: normalizeColor(palette.themeColor) })
    if (typeof getCurrentPages === 'function') getCurrentPages().forEach((page) => sync(page, palette))
  } catch (_) {}
  return palette
}

function sync(page, palette = pageData()) {
  if (!page || typeof page.setData !== 'function') return palette
  const data = page.data || {}
  if (data.themeStyle !== palette.themeStyle || JSON.stringify(data.bottomNav) !== JSON.stringify(palette.bottomNav) || JSON.stringify(data.guide) !== JSON.stringify(palette.guide) || data.guideTemplate !== palette.guideTemplate || data.guideEnabled !== palette.guideEnabled) page.setData(palette)
  const tab = typeof page.getTabBar === 'function' && page.getTabBar()
  if (tab && typeof tab.refresh === 'function') tab.refresh(palette)
  const inlineTab = typeof page.selectComponent === 'function' && page.selectComponent('#decoration-nav')
  if (inlineTab && typeof inlineTab.refresh === 'function') inlineTab.refresh(palette)
  try {
    const pages = getCurrentPages()
    if (pages[pages.length - 1] === page && wx.setNavigationBarColor) {
      const header = nativeColor(display.palette(currentBrand()).headerBg)
      const channels = [1, 3, 5].map((index) => parseInt(header.slice(index, index + 2), 16))
      wx.setNavigationBarColor({ backgroundColor: header, frontColor: channels[0] * .299 + channels[1] * .587 + channels[2] * .114 < 140 ? '#ffffff' : '#000000' })
    }
  } catch (_) {}
  return palette
}

function apply(page) {
  const initial = sync(page)
  try {
    const app = getApp()
    if (app && app.globalData && app.globalData.brand && Date.now() - refreshedAt < 10000) return Promise.resolve(initial)
  } catch (_) {
    return Promise.resolve(initial)
  }
  if (!brandPromise) {
    const requestRevision = revision
    brandPromise = request({ url: '/shop/home' })
      .then((brand) => requestRevision === revision ? remember(brand) : pageData())
      .catch(() => initial)
      .finally(() => { brandPromise = null })
  }
  return brandPromise.then((palette) => sync(page, palette))
}

module.exports = { apply, currentColor, normalizeColor, pageData, remember, softColor, sync }
