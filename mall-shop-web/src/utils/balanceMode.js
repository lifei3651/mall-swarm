export function balanceTransactionsEnabled(config) {
  if (!config) return false
  // 旧版接口尚未回传开关时保留既有支付入口；服务端仍负责最终门禁。
  return config.balanceTransactionsEnabled == null || Number(config.balanceTransactionsEnabled) === 1
}

export function availablePaymentType(current, options) {
  return options.some((option) => option.value === current) ? current : (options[0]?.value || '')
}
