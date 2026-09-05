// 与商城后台的 extraConfigJson 协议保持一致；装修不能改变交易权限或价格。
const object = (value) => value && typeof value === 'object' && !Array.isArray(value) ? value : {}
function extra(config = {}) {
  config = object(config)
  try { return object(typeof config.extraConfigJson === 'string' ? JSON.parse(config.extraConfigJson) : config.extraConfigJson) } catch (_) { return {} }
}
function toggle(value, fallback = true) {
  if ([false, 0, '0', 'false'].includes(value)) return false
  if ([true, 1, '1', 'true'].includes(value)) return true
  return fallback
}
function color(value, fallback) {
  const text = String(value || '').trim().toLowerCase()
  if (/^#(?:[a-f0-9]{3}|[a-f0-9]{6}|[a-f0-9]{8})$/.test(text)) return text
  const rgba = text.match(/^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*(0(?:\.\d+)?|1(?:\.0+)?))?\s*\)$/)
  if (rgba && rgba.slice(1, 4).every((n) => Number(n) <= 255) && (text.startsWith('rgba') === (rgba[4] !== undefined))) return text
  return fallback
}
const presets = {
  'retail-red': ['#e7193f', '#f5f6f7', '#ffffff', '24rpx'],
  'fresh-green': ['#0f766e', '#f1f7f5', '#f8fffc', '36rpx'],
  'premium-gold': ['#9a6a22', '#f7f4ef', '#fffcf6', '12rpx'],
  'soft-purple': ['#7c3aed', '#f7f4fb', '#fcf9ff', '40rpx']
}
function palette(brand = {}) {
  const legacy = { standard: 'retail-red', beauty: 'soft-purple', food: 'fresh-green', health: 'fresh-green', course: 'premium-gold' }
  const preset = presets[brand.productTemplate] || presets[legacy[brand.productTemplate]] || presets['retail-red']
  const primary = color(brand.themeColor, preset[0])
  const config = object(brand.displayConfig)
  const saved = object(config.colors || extra(config).colors)
  const base = { priceColor: primary, pageBg: preset[1], headerBg: preset[2], cardBg: '#ffffff', textColor: '#202735', mutedColor: '#6b7280', accentColor: primary, lineColor: '#e8ecf1', buttonBg: primary }
  return { ...Object.fromEntries(Object.entries(base).map(([key, value]) => [key, color(saved[key], value)])), primary, radius: preset[3] }
}
const moduleTypes = ['banner', 'notice', 'category', 'live', 'newArrivals', 'trust', 'products']
function home(config = {}) {
  config = object(config)
  const ext = extra(config)
  const source = Array.isArray(config.homeModules) && config.homeModules.length ? config.homeModules : ext.homeModules
  const saved = new Map()
  for (const item of Array.isArray(source) ? source : []) {
    if (!item) continue
    const items = item.type === 'discovery' ? [{ ...item, type: 'live' }, { ...item, type: 'newArrivals', sort: Number(item.sort || 4) + .1 }] : [item]
    for (const entry of items) if (moduleTypes.includes(entry.type) && !saved.has(entry.type)) saved.set(entry.type, entry)
  }
  const gates = { category: toggle(config.showHomeCategories ?? ext.showHomeCategories), trust: toggle(config.showTrustStrip ?? ext.showTrustStrip, false), live: toggle(config.liveSquareEnabled ?? ext.liveSquareEnabled), newArrivals: toggle(config.newArrivalsEnabled ?? ext.newArrivalsEnabled) }
  const modules = moduleTypes.map((type, i) => {
    const item = saved.get(type) || {}
    return { type, enabled: toggle(item.enabled) && gates[type] !== false, sort: Number.isFinite(Number(item.sort)) && Number(item.sort) > 0 ? Number(item.sort) : i + 1 }
  }).sort((a, b) => a.sort - b.sort).filter((item) => item.enabled)
  const layout = config.layoutTemplate || ext.layoutTemplate
  return { homeModules: modules, layoutTemplate: ['standard', 'product-focus', 'category-focus', 'campaign-feed'].includes(layout) ? layout : 'standard' }
}
const navDefaults = [['home', '首页', true], ['category', '分类', true], ['cart', '购物车', true], ['orders', '订单', false], ['profile', '我的', true]]
function navigation(config = {}) {
  config = object(config)
  const ext = extra(config)
  const source = Array.isArray(ext.bottomNav) ? ext.bottomNav : []
  return navDefaults.map(([type, label, enabled]) => {
    const saved = source.find((item) => item && item.type === type) || {}
    if (type === 'category' && !source.length) enabled = toggle(config.showBottomCategoryNav)
    if (type === 'category' && !Object.prototype.hasOwnProperty.call(ext, 'bottomNavIndependent') && config.layoutTemplate === 'product-focus') enabled = true
    let visible = ['home', 'cart', 'profile'].includes(type) || toggle(saved.enabled, enabled)
    if (type === 'category' && !Object.prototype.hasOwnProperty.call(ext, 'bottomNavIndependent') && config.layoutTemplate === 'product-focus' && !toggle(config.showBottomCategoryNav)) visible = true
    return { type, label: String(saved.label || label).slice(0, 12), enabled: visible, path: `/pages/${type}/index`, icon: type === 'orders' ? '' : `/assets/tabbar/${type}.png` }
  }).filter((item) => item.enabled)
}
function category(config = {}) {
  config = object(config)
  const ext = extra(config)
  const type = config.categoryGuideTemplate || ext.categoryGuideTemplate
  const guideTemplate = ['directory', 'showcase', 'scenario'].includes(type) ? type : 'directory'
  const switches = {}
  for (const name of ['primaryCategories', 'subcategories', 'hotProducts', 'heroCategories', 'shelves', 'recommendedProducts', 'scenarios', 'quickEntries', 'popularProducts']) {
    const field = `categoryGuide${name[0].toUpperCase()}${name.slice(1)}Enabled`
    switches[name] = toggle(config[field] ?? object(ext.categoryGuideModules)[name])
  }
  const keys = { directory: ['primaryCategories', 'subcategories', 'hotProducts'], showcase: ['heroCategories', 'shelves', 'recommendedProducts'], scenario: ['scenarios', 'quickEntries', 'popularProducts'] }
  return { guideEnabled: home(config).layoutTemplate === 'category-focus', guideTemplate, guide: switches, guideHasContent: keys[guideTemplate].some((key) => switches[key]) }
}
module.exports = { extra, toggle, color, palette, home, navigation, category }
