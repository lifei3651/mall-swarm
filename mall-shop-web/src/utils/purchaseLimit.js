import { checkPurchaseLimit } from '@/api/shop'
import { localPurchaseLimitViolation, purchaseLimitMessage } from '@/utils/purchaseLimitRules'
import { hasShopSession } from '@/utils/shopSession'

/**
 * 在加入购物车前检查当前会员的累计限购额度。
 * 未登录用户也先按购物车数量执行本地限购；登录后再由服务端核对历史订单。
 */
export const checkCartPurchaseLimit = async (product, addedQuantity = 1, existingCartQuantity = 0) => {
  const limit = Number(product?.purchaseLimit || 0)
  const localViolation = localPurchaseLimitViolation(product, addedQuantity, existingCartQuantity)
  if (localViolation) throw new Error(localViolation)
  if (!hasShopSession() || !product?.id) return { allowed: true }

  const quantity = Math.max(1, Number(existingCartQuantity || 0) + Number(addedQuantity || 0))
  const response = await checkPurchaseLimit(product.id, quantity)
  const result = response.data || {}
  if (result.allowed === false) {
    throw new Error(result.message || (Number(result.purchaseLimit || limit) > 0
      ? purchaseLimitMessage(result.productName || product.productName, result.purchaseLimit || limit, result.remainingQuantity)
      : '当前商品暂时无法继续购买'))
  }
  return result
}
