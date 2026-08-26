const supportedTypes = ['home', 'category', 'cart', 'orders', 'profile']
const requiredTypes = new Set(['home', 'cart', 'profile'])

export const defaultBottomNav = () => [
  { type: 'home', label: '首页', enabled: true },
  { type: 'category', label: '分类', enabled: true },
  { type: 'cart', label: '购物车', enabled: true },
  { type: 'orders', label: '订单', enabled: false },
  { type: 'profile', label: '我的', enabled: true },
]

const isEnabled = (value, fallback) => {
  if ([false, 0, '0', 'false'].includes(value)) return false
  if ([true, 1, '1', 'true'].includes(value)) return true
  return fallback
}

/**
 * 底部导航与首页版型完全独立：固定五种受控入口的顺序，同时保留旧配置项上的未知字段。
 * 历史数据缺少分类时默认显示，缺少订单时默认隐藏，不会因升级自动多出新入口。
 */
export const normalizeBottomNav = (configured, { legacyCategoryEnabled = true } = {}) => {
  const source = Array.isArray(configured) ? configured : []
  const byType = new Map()
  source.forEach((item) => {
    if (item && supportedTypes.includes(item.type) && !byType.has(item.type)) byType.set(item.type, item)
  })
  return defaultBottomNav().map((fallback) => {
    const saved = byType.get(fallback.type)
    const missingFallback = fallback.type === 'category' ? legacyCategoryEnabled : fallback.enabled
    const merged = { ...fallback, ...(saved || {}) }
    merged.enabled = requiredTypes.has(merged.type)
      ? true
      : isEnabled(saved?.enabled, missingFallback)
    return merged
  })
}

export const isEditableBottomNav = (type) => type === 'category' || type === 'orders'
