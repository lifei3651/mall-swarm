export const DIRECT_REFERRAL_POLICY = 'DIRECT_REFERRAL_V1'
export const DISABLED_BONUS_POLICY = 'CUSTOMER_BONUS_DISABLED'

export function directReferralForm(config = {}) {
  return {
    enabled: config.enabled === true,
    commissionPercent: Math.round(Number(config.commissionRate || 0) * 10000) / 100,
    purchaseScope: config.purchaseScope || 'ALL_ORDERS',
    settlementDelayDays: config.settlementDelayDays ?? 7,
  }
}

export function directReferralValidation(form) {
  const rate = Number(form.commissionPercent)
  const days = Number(form.settlementDelayDays)
  if (form.commissionPercent === null || form.commissionPercent === '' || !Number.isFinite(rate)
      || rate < 0 || rate > 100 || Math.abs(rate * 100 - Math.round(rate * 100)) > 0.000001) {
    return '佣金比例须为 0～100%，最多两位小数'
  }
  if (form.enabled && rate <= 0) return '开启佣金前请设置大于 0 的佣金比例'
  if (!['ALL_ORDERS', 'FIRST_PAID_ORDER'].includes(form.purchaseScope)) return '请选择有效的购买范围'
  if (form.settlementDelayDays === null || form.settlementDelayDays === ''
      || !Number.isInteger(days) || days < 0 || days > 365) return '结算保护期须为 0～365 天整数'
  return ''
}

export function directReferralPayload(form, config, confirmPolicySwitch = false) {
  return {
    enabled: form.enabled === true,
    commissionRate: Math.round(Number(form.commissionPercent) * 100) / 10000,
    purchaseScope: form.purchaseScope,
    settlementDelayDays: Number(form.settlementDelayDays),
    expectedVersionId: config.versionId ?? null,
    confirmPolicySwitch,
  }
}
