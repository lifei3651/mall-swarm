export const DISPLAY_COLOR_KEYS = [
  'priceColor',
  'pageBg',
  'headerBg',
  'cardBg',
  'textColor',
  'mutedColor',
  'accentColor',
  'lineColor',
  'buttonBg',
]

export const SHOP_THEME_OPTIONS = [
  {
    value: 'retail-red',
    label: '热卖红',
    color: '#e7193f',
    radius: '12px',
    description: '醒目促销、适合大众零售',
    surfaces: { pageBg: '#f5f6f7', headerBg: '#ffffff' },
  },
  {
    value: 'fresh-green',
    label: '清新绿',
    color: '#0f766e',
    radius: '18px',
    description: '自然清爽、适合健康生活',
    surfaces: { pageBg: '#f1f7f5', headerBg: '#f8fffc' },
  },
  {
    value: 'premium-gold',
    label: '轻奢金',
    color: '#9a6a22',
    radius: '6px',
    description: '稳重精致、适合高端商品',
    surfaces: { pageBg: '#f7f4ef', headerBg: '#fffcf6' },
  },
  {
    value: 'soft-purple',
    label: '雅致紫',
    color: '#7c3aed',
    radius: '20px',
    description: '柔和现代、适合美妆精品',
    surfaces: { pageBg: '#f7f4fb', headerBg: '#fcf9ff' },
  },
]

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

const colorEquals = (left, right) => String(left || '').trim().toLowerCase() === String(right || '').trim().toLowerCase()
const validColorValue = (value) => typeof value === 'string' && value.trim().length > 0

export const themePalette = (theme, mainColor = theme?.color) => {
  const primary = validColorValue(mainColor) ? mainColor.trim() : '#e7193f'
  return {
    priceColor: primary,
    pageBg: theme?.surfaces?.pageBg || '#f5f6f7',
    headerBg: theme?.surfaces?.headerBg || '#ffffff',
    cardBg: '#ffffff',
    textColor: '#202735',
    mutedColor: '#6b7280',
    accentColor: primary,
    lineColor: '#e8ecf1',
    buttonBg: primary,
  }
}

export const isLegacyCategoryPalette = (colors = {}) => DISPLAY_COLOR_KEYS
  .every((key) => colorEquals(colors?.[key], LEGACY_CATEGORY_COLORS[key]))

export const hydrateThemeColors = (theme, themeColor, storedColors = {}) => {
  const base = themePalette(theme, themeColor)
  // 1.0.89 前选择分类导购版会无条件写入这套蓝色。仅识别完整旧指纹，不会清理用户的任意自定义颜色。
  if (isLegacyCategoryPalette(storedColors) && !colorEquals(themeColor, LEGACY_CATEGORY_COLORS.accentColor)) return base
  const custom = Object.fromEntries(DISPLAY_COLOR_KEYS
    .filter((key) => validColorValue(storedColors?.[key]))
    .map((key) => [key, storedColors[key].trim()]))
  return { ...base, ...custom }
}

export const applyThemePresetToForm = (form, theme) => {
  form.productTemplate = theme.value
  form.themeColor = theme.color
  form.colors = themePalette(theme)
  return form
}

export const isThemePresetActive = (form, theme) => {
  if (form?.productTemplate !== theme.value || !colorEquals(form?.themeColor, theme.color)) return false
  const palette = themePalette(theme)
  return DISPLAY_COLOR_KEYS.every((key) => colorEquals(form?.colors?.[key], palette[key]))
}

export const themePreviewVariables = (form = {}, fallbackColor = '#e7193f') => {
  const primary = validColorValue(form.themeColor) ? form.themeColor : fallbackColor
  const colors = form.colors || {}
  return {
    '--preview-color': primary,
    '--preview-page-bg': colors.pageBg || '#f5f6f8',
    '--preview-header-bg': colors.headerBg || primary,
    '--preview-card-bg': colors.cardBg || '#ffffff',
    '--preview-text': colors.textColor || '#202735',
    '--preview-muted': colors.mutedColor || '#98a2b3',
    '--preview-price': colors.priceColor || primary,
    '--preview-accent': colors.accentColor || primary,
    '--preview-line': colors.lineColor || '#e5e7eb',
    '--preview-button': colors.buttonBg || primary,
  }
}
