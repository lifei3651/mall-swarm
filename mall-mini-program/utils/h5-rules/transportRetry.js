// Generated from mall-shop-web/src/utils/transportRetry.js. Do not edit.
// Existing H5 policy; platform adapters normalize errors without changing the policy.
const RETRYABLE_METHODS = new Set(['get', 'head', 'options'])
const isTransientTransportError = (error) => {
  if (error?.response) return false
  return ['ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)
    || error?.message === 'Network Error'
}
const isGatewayRecoveryError = (error) => new Set([502, 503, 504]).has(Number(error?.response?.status))
const retryDelay = (method, error, retryCount = 0) => RETRYABLE_METHODS.has(String(method || 'get').toLowerCase())
  && retryCount < 1 && (isTransientTransportError(error) || isGatewayRecoveryError(error))
  ? (isGatewayRecoveryError(error) ? 600 : 250) : null

module.exports = { RETRYABLE_METHODS, isTransientTransportError, isGatewayRecoveryError, retryDelay }
