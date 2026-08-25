import { resolveDisplayColors } from './displayConfig.js'

const DEFAULT_BRAND_NAME = '商城'
const DEFAULT_THEME_COLOR = '#e7193f'
const BRAND_LOGO_STORAGE_KEY = 'shop_logo_url'

export const themePresets = {
  'retail-red': {
    radius: '13px',
    pageBackground: '#f5f6f7',
    headerBackground: 'rgba(255,255,255,.97)',
    shadow: '0 10px 26px rgba(29,37,43,.08)',
  },
  'fresh-green': {
    radius: '18px',
    pageBackground: '#f1f7f5',
    headerBackground: 'rgba(248,255,252,.97)',
    shadow: '0 12px 28px rgba(15,118,110,.10)',
  },
  'premium-gold': {
    radius: '7px',
    pageBackground: '#f7f4ef',
    headerBackground: 'rgba(255,252,246,.97)',
    shadow: '0 9px 24px rgba(85,60,25,.12)',
  },
  'soft-purple': {
    radius: '21px',
    pageBackground: '#f7f4fb',
    headerBackground: 'rgba(252,249,255,.97)',
    shadow: '0 12px 30px rgba(124,58,237,.11)',
  },
}

const legacyThemes = {
  standard: 'retail-red',
  beauty: 'soft-purple',
  food: 'fresh-green',
  health: 'fresh-green',
  course: 'premium-gold',
}

const pageLabels = {
  Category: '商品分类',
  NoticeList: '商城公告',
  NoticeDetail: '公告详情',
  ProductDetail: '商品详情',
  Cart: '购物车',
  Checkout: '确认订单',
  Login: '登录注册',
  ForgotPassword: '找回密码',
  Profile: '个人中心',
  ProfileWallet: '余额',
  ProfileSecurity: '支付安全',
  ProfileAddresses: '收货地址',
  Orders: '我的订单',
  OrderDetail: '订单详情',
  NewArrivals: '新品速递',
  BrandCulture: '品牌文化',
}

export const normalizeThemeKey = (value) => {
  const key = legacyThemes[value] || value
  return themePresets[key] ? key : 'retail-red'
}

const normalizeColor = (value) => /^#[0-9a-f]{6}$/i.test(value || '') ? value.toLowerCase() : DEFAULT_THEME_COLOR

const LEGACY_CATEGORY_COLORS = {
  priceColor: '#e5484d',
  pageBg: '#f6f7f9',
  headerBg: '#ffffff',
  cardBg: '#ffffff',
  textColor: '#1b2430',
  mutedColor: '#6b7280',
  accentColor: '#1556a3',
  lineColor: '#e8ecf1',
  buttonBg: '#1556a3',
}
const DISPLAY_COLOR_KEYS = Object.keys(LEGACY_CATEGORY_COLORS)
const colorEquals = (left, right) => String(left || '').trim().toLowerCase() === String(right || '').trim().toLowerCase()
const isLegacyCategoryPalette = (colors) => DISPLAY_COLOR_KEYS.every((key) => colorEquals(colors?.[key], LEGACY_CATEGORY_COLORS[key]))

export const resolveBrandCssVariables = (config = {}) => {
  const themeColor = normalizeColor(config.themeColor)
  const productTemplate = normalizeThemeKey(config.productTemplate)
  const preset = themePresets[productTemplate]
  const storedColors = resolveDisplayColors(config.displayConfig || config)
  // 旧分类导购版会强制保存一套蓝色。只对完整旧指纹做兼容，其他自定义色保持原样。
  const colors = isLegacyCategoryPalette(storedColors) && !colorEquals(themeColor, LEGACY_CATEGORY_COLORS.accentColor)
    ? {}
    : storedColors
  return {
    '--brand-primary': themeColor,
    '--brand-primary-dark': mixHex(themeColor, [0, 0, 0], 0.18),
    '--brand-primary-soft': mixHex(themeColor, [255, 255, 255], 0.91),
    '--teal': themeColor,
    '--accent': colors.accentColor || themeColor,
    '--shop-card-radius': preset.radius,
    '--shop-page-bg': colors.pageBg || preset.pageBackground,
    '--shop-header-bg': colors.headerBg || preset.headerBackground,
    '--shop-card-shadow': preset.shadow,
    '--price-color': colors.priceColor || themeColor,
    '--card-bg': colors.cardBg || '#ffffff',
    '--card': colors.cardBg || '#ffffff',
    '--text-color': colors.textColor || '#202735',
    '--text': colors.textColor || '#202735',
    '--ink': colors.textColor || '#202735',
    '--muted-color': colors.mutedColor || '#6b7280',
    '--muted': colors.mutedColor || '#6b7280',
    '--line': colors.lineColor || '#e8ecf1',
    '--shop-button-bg': colors.buttonBg || themeColor,
  }
}

const mixHex = (hex, target, weight) => {
  const source = hex.slice(1).match(/../g).map((part) => Number.parseInt(part, 16))
  const mixed = source.map((value, index) => Math.round(value * (1 - weight) + target[index] * weight))
  return `#${mixed.map((value) => value.toString(16).padStart(2, '0')).join('')}`
}

export const currentBrandName = () => localStorage.getItem('shop_brand_name') || DEFAULT_BRAND_NAME
export const currentBrandLogo = () => localStorage.getItem(BRAND_LOGO_STORAGE_KEY) || ''

export const updatePageTitle = (routeName, brandName = currentBrandName()) => {
  const safeBrandName = brandName?.trim() || DEFAULT_BRAND_NAME
  const label = pageLabels[routeName]
  document.title = routeName === 'Home' || !label ? safeBrandName : `${label} - ${safeBrandName}`
}

const updateBrowserLogo = (logoUrl) => {
  let favicon = document.head.querySelector('link[rel="icon"]')
  if (!favicon) {
    favicon = document.createElement('link')
    favicon.rel = 'icon'
    document.head.appendChild(favicon)
  }
  favicon.href = logoUrl || '/lingqi-logo-mark.png'

  let appleTouchIcon = document.head.querySelector('link[rel="apple-touch-icon"]')
  if (!appleTouchIcon) {
    appleTouchIcon = document.createElement('link')
    appleTouchIcon.rel = 'apple-touch-icon'
    document.head.appendChild(appleTouchIcon)
  }
  appleTouchIcon.href = logoUrl || '/lingqi-logo-mark.png'
}

export const applyBrandConfig = (config = {}) => {
  const brandName = config.brandName?.trim() || DEFAULT_BRAND_NAME
  const logoUrl = config.logoUrl || ''
  const themeColor = normalizeColor(config.themeColor)
  const productTemplate = normalizeThemeKey(config.productTemplate)
  const root = document.documentElement

  localStorage.setItem('shop_brand_name', brandName)
  localStorage.setItem(BRAND_LOGO_STORAGE_KEY, logoUrl)
  root.dataset.shopTheme = productTemplate
  Object.entries(resolveBrandCssVariables(config)).forEach(([property, value]) => root.style.setProperty(property, value))
  updateBrowserLogo(logoUrl)

  const brand = { brandName, logoUrl, themeColor, productTemplate, brandCultureEnabled: config.brandCultureEnabled === true }
  window.dispatchEvent(new CustomEvent('shop-brand-updated', { detail: brand }))
  return brand
}
