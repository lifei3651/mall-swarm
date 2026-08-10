const normalizeQuantity = (value) => Math.max(0, Math.floor(Number(value || 0)))

export const cartItemKey = (item = {}) => item.skuId ? `${item.id}-${item.skuId}` : `${item.id}`

export const resolveCurrentStock = (item = {}, detail = {}) => {
  const skus = Array.isArray(detail?.skus) ? detail.skus : []
  if (item.skuId) {
    const matchedSku = skus.find((sku) => Number(sku.id) === Number(item.skuId))
    return matchedSku ? normalizeQuantity(matchedSku.stock) : 0
  }
  const product = detail?.product || detail || {}
  return normalizeQuantity(product.stock ?? item.stock)
}

export const stockLimitMessage = (availableQuantity) => `库存不足，最多可购买${normalizeQuantity(availableQuantity)}件`

export const stockAdditionViolation = (stock, requestedQuantity, existingCartQuantity = 0) => {
  const remaining = Math.max(0, normalizeQuantity(stock) - normalizeQuantity(existingCartQuantity))
  return normalizeQuantity(requestedQuantity) > remaining ? stockLimitMessage(remaining) : ''
}

export const stockQuantityViolation = (stock, requestedQuantity) => (
  normalizeQuantity(requestedQuantity) > normalizeQuantity(stock)
    ? stockLimitMessage(stock)
    : ''
)
