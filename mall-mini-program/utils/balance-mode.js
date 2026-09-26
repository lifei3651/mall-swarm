function balanceTransactionsEnabled(config) {
  if (!config) return false
  // 兼容尚未回传该字段的旧接口；支付服务端仍会做最终校验。
  return config.balanceTransactionsEnabled == null || Number(config.balanceTransactionsEnabled) === 1
}

module.exports = { balanceTransactionsEnabled }
