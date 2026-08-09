const positiveInteger = (value) => Math.max(0, Math.trunc(Number(value || 0)))

export const purchaseLimitMessage = (productName, limit, remainingQuantity) => {
  const name = String(productName || '当前商品').trim() || '当前商品'
  const normalizedLimit = positiveInteger(limit)
  const remaining = positiveInteger(remainingQuantity)
  if (remaining <= 0) {
    return `${name}每位会员限购 ${normalizedLimit} 件，您已达到限购数量，无法继续加购`
  }
  return `${name}每位会员限购 ${normalizedLimit} 件，您还可购买 ${remaining} 件`
}

/**
 * 先使用商品详情中的实时限购值拦截明显超限，再由服务端核对历史订单。
 * 返回空字符串表示本地未发现超限，不能替代服务端最终校验。
 */
export const localPurchaseLimitViolation = (product, addedQuantity = 1, existingCartQuantity = 0) => {
  const limit = positiveInteger(product?.purchaseLimit)
  if (limit <= 0) return ''
  const existing = positiveInteger(existingCartQuantity)
  const added = positiveInteger(addedQuantity)
  const remaining = Math.max(0, limit - existing)
  if (existing + added <= limit) return ''
  return purchaseLimitMessage(product?.productName, limit, remaining)
}
