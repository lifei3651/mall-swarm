// Pure shared presentation protocol. No prices, permissions, inventory or feature gates.
export const PAGE_LAYOUT_OPTIONS = {
  home: ['standard', 'product-focus', 'category-focus', 'campaign-feed'],
  category: ['list', 'directory', 'showcase', 'scenario'],
  product: ['standard', 'inset'],
}
export const LAYOUT_PLATFORMS = ['h5', 'mini', 'app']
const object = value => value && typeof value === 'object' && !Array.isArray(value) ? value : {}
const valid = (page, value) => PAGE_LAYOUT_OPTIONS[page]?.includes(value)
const extra = config => {
  try { return object(typeof config.extraConfigJson === 'string' ? JSON.parse(config.extraConfigJson) : config.extraConfigJson) } catch (_) { return {} }
}

export const normalizePageLayouts = (config = {}) => {
  config = object(config)
  const ext = extra(config)
  const raw = object(config.pageLayouts || ext.pageLayouts)
  const supported = raw.version === 1
  const home = valid('home', config.layoutTemplate || ext.layoutTemplate) ? config.layoutTemplate || ext.layoutTemplate : 'standard'
  const guide = config.categoryGuideTemplate || ext.categoryGuideTemplate
  const legacy = { home, category: home === 'category-focus' ? (valid('category', guide) && guide !== 'list' ? guide : 'directory') : 'list', product: 'standard' }
  const shared = Object.fromEntries(Object.keys(PAGE_LAYOUT_OPTIONS).map(page => [page,
    supported && valid(page, object(raw.shared)[page]) ? raw.shared[page] : legacy[page],
  ]))
  const platforms = Object.fromEntries(LAYOUT_PLATFORMS.map(platform => [platform,
    Object.fromEntries(Object.entries(supported ? object(object(raw.platforms)[platform]) : {})
      .filter(([page, value]) => Object.prototype.hasOwnProperty.call(PAGE_LAYOUT_OPTIONS, page) && valid(page, value))),
  ]))
  return { version: 1, shared, platforms }
}

export const resolvePageLayouts = (config = {}, platform = 'h5') => {
  const normalized = normalizePageLayouts(config)
  return { ...normalized.shared, ...(LAYOUT_PLATFORMS.includes(platform) ? normalized.platforms[platform] : {}) }
}

// Every target is checked, not only the platform currently visible in the editor.
export const hasEmptyDirectoryLayout = (config = {}) => {
  const ext = extra(config)
  const enabled = value => ![false, 0, '0', 'false'].includes(value)
  const empty = ['primaryCategories', 'subcategories', 'hotProducts'].every(key => {
    const field = `categoryGuide${key[0].toUpperCase()}${key.slice(1)}Enabled`
    return !enabled(config[field] ?? object(ext.categoryGuideModules)[key])
  })
  return empty && ['shared', ...LAYOUT_PLATFORMS].some(platform => resolvePageLayouts(config, platform).category === 'directory')
}
