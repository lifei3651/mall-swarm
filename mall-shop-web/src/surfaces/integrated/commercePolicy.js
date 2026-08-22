const checkoutBusinessTypes = new Set(['NORMAL', 'FLASH_SALE', 'REPURCHASE'])

export const validateCheckoutBusinessType = (businessType) => checkoutBusinessTypes.has(businessType)
  ? ''
  : '当前商品不属于一体化商城可结算范围，请返回商城重新选择'

export const mixedBusinessError = '普通商品、秒杀商品和复购商品不能混合下单'

export const resolveBusinessEntries = (config = {}) => {
  const entries = []
  if (Number(config.flashSaleEnabled) === 1) {
    entries.push({ path: '/flash-sale', kind: 'flash', title: '限时秒杀', description: '到点开抢，抢完即止' })
  }
  if (Number(config.repurchaseMallEnabled) === 1) {
    entries.push({ path: '/repurchase', kind: 'member-zone', title: '会员复购', description: '复购商品独立规则与结算' })
  }
  return entries
}
