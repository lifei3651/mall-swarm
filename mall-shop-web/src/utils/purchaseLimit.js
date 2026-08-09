import { checkPurchaseLimit } from '@/api/shop'

/**
 * 在加入购物车前检查当前会员的累计限购额度。
 * 未登录用户先允许加购，结算/提交订单时仍由服务端做最终校验。
 */
export const checkCartPurchaseLimit = async (product, addedQuantity = 1, existingCartQuantity = 0) => {
  const limit = Number(product?.purchaseLimit || 0)
  if (!localStorage.getItem('shop_token') || !product?.id) return { allowed: true }

  const quantity = Math.max(1, Number(existingCartQuantity || 0) + Number(addedQuantity || 0))
  const response = await checkPurchaseLimit(product.id, quantity)
  const result = response.data || {}
  if (result.allowed === false) {
    throw new Error(result.message || (limit > 0
      ? `每位会员限购 ${limit} 件，当前加购数量已超出限购额度`
      : '当前商品暂时无法继续购买'))
  }
  return result
}
