const checkoutBusinessTypes = new Set(['NORMAL', 'FLASH_SALE'])
export const couponBusinessLabel = (types = []) => types.includes('NORMAL') ? '普通商城' : '其他专区专用（当前商城不可用）'

export const validateCheckoutBusinessType = (businessType) => checkoutBusinessTypes.has(businessType)
  ? ''
  : '当前商品不属于公开商城可结算范围，请返回商城重新选择'

export const mixedBusinessError = '普通商品和活动商品不能混合下单'

export const resolveBusinessEntries = (config = {}) => {
  const entries = []
  if (Number(config.flashSaleEnabled) === 1) {
    entries.push({ path: '/flash-sale', kind: 'flash', title: '限时秒杀', description: '到点开抢，抢完即止' })
  }
  return entries
}
