// 保留自营商品明确选定的不计佣状态，以及历史 STANDARD 记录；商户商品沿用不计佣边界。
export function normalizeProductBonusMode(merchantId, mode) {
  if (merchantId) return 'NONE'
  return ['INHERIT', 'NONE', 'STANDARD'].includes(mode) ? mode : 'INHERIT'
}
