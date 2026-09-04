function normalizePaymentNo(value) {
  const normalized = String(value || '').trim()
  return /^[A-Za-z0-9_-]{1,64}$/.test(normalized) ? normalized : ''
}

function detailPath(paymentNo) {
  const normalized = normalizePaymentNo(paymentNo)
  return normalized ? `/pages/order-detail/index?orderNo=${encodeURIComponent(normalized)}` : ''
}

module.exports = { normalizePaymentNo, detailPath }
