export const SHOP_LAYOUT_TEMPLATES = [
  'standard',
  'product-focus',
  'category-focus',
  'campaign-feed',
]

/**
 * 整体版型只选择视觉排版。其他装修字段必须由各自分组独立维护。
 */
export const applyVisualLayoutTemplate = (form, template) => {
  if (!form || !SHOP_LAYOUT_TEMPLATES.includes(template)) return form
  form.layoutTemplate = template
  return form
}
