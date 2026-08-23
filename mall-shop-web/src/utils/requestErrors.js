const GATEWAY_RECOVERY_STATUSES = new Set([502, 503, 504])

export const isGatewayRecoveryError = (error) => GATEWAY_RECOVERY_STATUSES.has(Number(error?.response?.status))

export const resolveRequestErrorMessage = (error) => {
  const status = Number(error?.response?.status || 0)
  if (GATEWAY_RECOVERY_STATUSES.has(status)) return '系统正在更新或连接正在恢复，请稍后重试'
  if (status >= 500) return '系统服务暂时异常，请稍后重试'
  const serverMessage = error?.response?.data?.message
  if (serverMessage) return serverMessage
  const transportError = !error?.response && (
    ['ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)
      || error?.message === 'Network Error'
  )
  if (transportError) return '网络暂时不可用，请检查网络后重试'
  return error?.message || '请求失败，请稍后重试'
}
