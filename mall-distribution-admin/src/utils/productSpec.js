const GENERIC_SPEC_NAMES = new Set(['默认规格', '默认sku', '默认 SKU', '标准规格'])

const parseAttributes = (value) => {
  if (!value) return []
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') return []
    return Object.entries(parsed)
      .map(([name, content]) => ({ name: String(name || '').trim(), value: String(content ?? '').trim() }))
      .filter((item) => item.name && item.value)
  } catch {
    return []
  }
}

/**
 * 订单优先展示下单时冻结的 SKU 属性；旧订单没有属性快照时再回退到 SKU 名称。
 * 单规格商品不虚构“默认规格”，明确显示为“单规格”。
 */
export const formatProductSpec = (item = {}) => {
  const attributes = parseAttributes(item.skuAttrs ?? item.attrsJson)
  if (attributes.length) return attributes.map(({ name, value }) => `${name}：${value}`).join(' / ')
  const skuName = String(item.skuName || '').trim()
  if (skuName && !GENERIC_SPEC_NAMES.has(skuName)) return skuName
  return '单规格'
}
