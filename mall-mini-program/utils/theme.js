const request = require('./request')

const STORAGE_KEY = 'mall_mini_theme_color'
const DEFAULT_COLOR = '#e7193f'
let brandPromise = null

function normalizeColor(value) {
  return /^#[0-9a-fA-F]{6}$/.test(String(value || '')) ? String(value).toLowerCase() : DEFAULT_COLOR
}

function softColor(value) {
  const color = normalizeColor(value)
  const channels = [1, 3, 5].map((index) => Number.parseInt(color.slice(index, index + 2), 16))
  return `#${channels.map((channel) => Math.round(channel * 0.1 + 255 * 0.9).toString(16).padStart(2, '0')).join('')}`
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

function pageData(value) {
  const themeColor = normalizeColor(value || currentColor())
  return { themeColor, themeSoftColor: softColor(themeColor) }
}

function remember(brand) {
  const palette = pageData(brand && brand.themeColor)
  try {
    const app = getApp()
    if (app && app.globalData) app.globalData.brand = brand || { themeColor: palette.themeColor }
    wx.setStorageSync(STORAGE_KEY, palette.themeColor)
    wx.setTabBarStyle({ selectedColor: palette.themeColor })
  } catch (_) {}
  return palette
}

function sync(page, palette = pageData()) {
  if (!page || typeof page.setData !== 'function') return palette
  const data = page.data || {}
  if (data.themeColor !== palette.themeColor || data.themeSoftColor !== palette.themeSoftColor) page.setData(palette)
  return palette
}

function apply(page) {
  const initial = sync(page)
  try {
    const app = getApp()
    if (app && app.globalData && app.globalData.brand) return Promise.resolve(initial)
  } catch (_) {
    return Promise.resolve(initial)
  }
  if (!brandPromise) {
    brandPromise = request({ url: '/shop/home' })
      .then((brand) => remember(brand))
      .catch(() => initial)
      .finally(() => { brandPromise = null })
  }
  return brandPromise.then((palette) => sync(page, palette))
}

module.exports = { apply, currentColor, normalizeColor, pageData, remember, softColor, sync }
