import { normalizeDisplayToggle } from './displayConfig.js'

const supportedTypes = ['home', 'category', 'cart', 'orders', 'profile']
const requiredTypes = new Set(['home', 'cart', 'profile'])

export const defaultBottomNav = [
  { type: 'home', label: '首页', enabled: true, path: '/' },
  { type: 'category', label: '分类', enabled: true, path: '/category' },
  { type: 'cart', label: '购物车', enabled: true, path: '/cart' },
  { type: 'orders', label: '订单', enabled: false, path: '/orders' },
  { type: 'profile', label: '我的', enabled: true, path: '/profile' },
]

/** 所有首页版型使用同一导航解析器，版型不参与入口显隐。 */
export const resolveBottomNav = (configured, { legacyCategoryEnabled = true } = {}) => {
  const source = Array.isArray(configured) ? configured : []
  const byType = new Map()
  source.forEach((item) => {
    if (item && supportedTypes.includes(item.type) && !byType.has(item.type)) byType.set(item.type, item)
  })
  return defaultBottomNav.map((fallback) => {
    const saved = byType.get(fallback.type)
    const missingFallback = fallback.type === 'category' ? legacyCategoryEnabled : fallback.enabled
    const merged = { ...fallback, ...(saved || {}), path: fallback.path }
    merged.enabled = requiredTypes.has(merged.type)
      ? true
      : normalizeDisplayToggle(saved?.enabled, missingFallback)
    return merged
  })
}
