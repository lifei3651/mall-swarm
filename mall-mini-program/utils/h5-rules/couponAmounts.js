// Generated from mall-shop-web/src/utils/couponAmounts.js. Do not edit.
// Preview only. Orders and refunds are always priced again by the server.
function couponRefundPreview(items = [], selected = [], sales = []) {
  let total = 0
  for (const choice of selected) {
    const item = items.find(i => String(i.id) === String(choice.orderItemId))
    if (!item || !choice.quantity) continue
    const quantity = Number(item.quantity), current = Number(choice.quantity)
    const returned = sales.filter(s => [1, 2].includes(Number(s.applyType)) && [1, 6].includes(Number(s.status)))
      .flatMap(s => s.items || []).filter(i => String(i.orderItemId) === String(item.id))
      .reduce((sum, i) => sum + Number(i.refundQuantity || 0), 0)
    const cents = Math.round(Number(item.totalAmount) * 100) - Math.round(Number(item.couponDiscountAmount) * 100)
    if (!Number.isSafeInteger(cents) || cents < 0 || !Number.isInteger(quantity) || quantity <= 0 || !Number.isInteger(current) || current <= 0 || !Number.isInteger(returned) || returned < 0 || current + returned > quantity) return 0
    // Quotient/remainder avoids multiplying an entire order amount by quantity.
    const cumulative = count => Math.floor(cents / quantity) * count + Math.floor((cents % quantity) * count / quantity)
    total += cumulative(returned + current) - cumulative(returned)
  }
  return total / 100
}

// Fail closed if a stale server ignores a selected coupon or returns inconsistent totals.
function couponQuoteValid(quote, claimId) {
  if (!quote) return false
  const fields = ['productAmount', 'freightAmount', 'payAmount']
  if (fields.some(key => quote[key] == null || String(quote[key]).trim() === '' || !Number.isFinite(Number(quote[key])) || Number(quote[key]) < 0)) return false
  const cents = value => Math.round(Number(value) * 100)
  const discount = quote.discountAmount == null ? 0 : Number(quote.discountAmount)
  if (!Number.isFinite(discount) || discount < 0 || cents(discount) > cents(quote.productAmount)) return false
  if (cents(quote.productAmount) + cents(quote.freightAmount) - cents(discount) !== cents(quote.payAmount)) return false
  return claimId ? String(quote.selectedCouponClaimId) === String(claimId) && discount > 0 : !quote.selectedCouponClaimId && discount === 0
}

// A received validation rejection before any uncertain attempt is safe to revise.
// Timeouts, 5xx and in-flight idempotency conflicts must keep the original request key.
function canReviseRejectedOrder(error, previousAttempt) {
  return !previousAttempt && Number(error?.httpStatus || error?.response?.status) === 400
    && !/正在提交|重复操作|提交结果|唯一编号/.test(error?.message || '')
}

module.exports = { couponRefundPreview, couponQuoteValid, canReviseRejectedOrder }
