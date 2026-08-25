const disabledValues = new Set([false, 0, '0', 'false'])
const enabledValues = new Set([true, 1, '1', 'true'])

export const normalizeDisplayToggle = (value, fallback = true) => {
  if (disabledValues.has(value)) return false
  if (enabledValues.has(value)) return true
  return fallback
}

export const readDisplayExtraConfig = (config = {}) => {
  if (config.extraConfigJson && typeof config.extraConfigJson === 'object') {
    return config.extraConfigJson
  }
  try {
    const parsed = JSON.parse(config.extraConfigJson || '{}')
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch (_) {
    return {}
  }
}

export const splitLegacyDiscoveryModule = (configured = []) => {
  if (!Array.isArray(configured)) return []
  const legacy = configured.find((module) => module?.type === 'discovery')
  if (!legacy) return configured

  const migrated = configured.filter((module) => module?.type !== 'discovery')
  const types = new Set(migrated.map((module) => module?.type))
  const legacySort = Number(legacy.sort) || 4
  if (!types.has('live')) migrated.push({ ...legacy, type: 'live', sort: legacySort })
  if (!types.has('newArrivals')) migrated.push({ ...legacy, type: 'newArrivals', sort: legacySort + 0.1 })
  return migrated
}

export const resolveHomeModules = (config = {}, defaults = []) => {
  const extra = readDisplayExtraConfig(config)
  const configured = Array.isArray(config.homeModules) && config.homeModules.length
    ? config.homeModules
    : extra.homeModules
  const configuredModules = Array.isArray(configured) && configured.length
    ? splitLegacyDiscoveryModule(configured)
    : null
  const existingTypes = new Set((configuredModules || []).map((module) => module?.type).filter(Boolean))
  // 旧客户已保存装修配置时，只补充新版本新增的模块开关，不覆盖原有选择。
  const source = configuredModules
    ? [...configuredModules, ...defaults.filter((module) => !existingTypes.has(module?.type))]
    : defaults
  const defaultOrder = new Map(defaults.map((module, index) => [module?.type, index]))
  return source
    .map((module, index) => ({
      ...module,
      enabled: normalizeDisplayToggle(module?.enabled, true),
      sort: Number(module?.sort) || index + 1,
    }))
    .sort((a, b) => (a.sort - b.sort)
      || ((defaultOrder.get(a.type) ?? Number.MAX_SAFE_INTEGER) - (defaultOrder.get(b.type) ?? Number.MAX_SAFE_INTEGER)))
}

export const resolveDisplayColors = (config = {}) => {
  if (config.colors && typeof config.colors === 'object') return config.colors
  const extra = readDisplayExtraConfig(config)
  return extra.colors && typeof extra.colors === 'object' ? extra.colors : {}
}

export const CATEGORY_GUIDE_TEMPLATES = ['directory', 'showcase', 'scenario']

export const resolveCategoryGuideConfig = (config = {}) => {
  const extra = readDisplayExtraConfig(config)
  const modules = extra.categoryGuideModules && typeof extra.categoryGuideModules === 'object'
    ? extra.categoryGuideModules
    : {}
  const templateCandidate = config.categoryGuideTemplate || extra.categoryGuideTemplate
  const template = CATEGORY_GUIDE_TEMPLATES.includes(templateCandidate) ? templateCandidate : 'directory'
  const readModule = (field, key) => normalizeDisplayToggle(config[field] ?? modules[key], true)
  return {
    template,
    modules: {
      primaryCategories: readModule('categoryGuidePrimaryCategoriesEnabled', 'primaryCategories'),
      subcategories: readModule('categoryGuideSubcategoriesEnabled', 'subcategories'),
      hotProducts: readModule('categoryGuideHotProductsEnabled', 'hotProducts'),
      heroCategories: readModule('categoryGuideHeroCategoriesEnabled', 'heroCategories'),
      shelves: readModule('categoryGuideShelvesEnabled', 'shelves'),
      recommendedProducts: readModule('categoryGuideRecommendedProductsEnabled', 'recommendedProducts'),
      scenarios: readModule('categoryGuideScenariosEnabled', 'scenarios'),
      quickEntries: readModule('categoryGuideQuickEntriesEnabled', 'quickEntries'),
      popularProducts: readModule('categoryGuidePopularProductsEnabled', 'popularProducts'),
    },
  }
}

const requiredBottomNavTypes = new Set(['cart', 'profile'])

/** 前端容错仅用于旧配置；服务端保存同样会拒绝关闭或移除这些系统必需入口。 */
export const enforceRequiredBottomNav = (items = [], defaults = []) => {
  const configured = Array.isArray(items) ? items : []
  const types = new Set(configured.map((item) => item?.type).filter(Boolean))
  const missingRequired = (Array.isArray(defaults) ? defaults : [])
    .filter((item) => requiredBottomNavTypes.has(item?.type) && !types.has(item.type))
  return [...configured, ...missingRequired].map((item) => (
    requiredBottomNavTypes.has(item?.type) ? { ...item, enabled: true, systemRequired: true } : item
  ))
}
