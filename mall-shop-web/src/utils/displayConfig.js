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

export const resolveHomeModules = (config = {}, defaults = []) => {
  const extra = readDisplayExtraConfig(config)
  const configured = Array.isArray(config.homeModules) && config.homeModules.length
    ? config.homeModules
    : extra.homeModules
  const configuredModules = Array.isArray(configured) && configured.length ? configured : null
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
