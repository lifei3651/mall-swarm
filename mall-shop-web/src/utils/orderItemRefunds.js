// Use the original order-item ID, not the product name: an order can contain
// identical products on separate lines. Pending refunds and exchanges are not paid refunds.
export function refundedQuantity(item, sales = []) {
  const itemId = String(item?.id ?? '')
  const ordered = Number(item?.quantity)
  if (!/^[1-9]\d{0,18}$/.test(itemId) || !Number.isSafeInteger(ordered) || ordered <= 0) return 0
  let refunded = 0
  for (const sale of sales) {
    if (![1, 2, 4].includes(Number(sale.applyType)) || Number(sale.status) !== 1) continue
    for (const line of sale.items || []) {
      const quantity = Number(line.refundQuantity)
      if (String(line.orderItemId) === itemId && Number.isSafeInteger(quantity) && quantity > 0) {
        refunded = Math.min(ordered, refunded + quantity)
      }
    }
  }
  return refunded
}

export function partialRefundSummary(order, items = [], sales = []) {
  const status = Number(order?.status)
  if (![1, 2, 3].includes(status)) return ''
  const total = items.reduce((sum, item) => sum + Math.max(0, Number(item.quantity) || 0), 0)
  const refunded = items.reduce((sum, item) => sum + refundedQuantity(item, sales), 0)
  const remaining = total - refunded
  if (!refunded || remaining <= 0) return ''
  const state = { 1: '待发货', 2: '待收货', 3: '已完成' }[status]
  return `已退款 ${refunded} 件，剩余 ${remaining} 件${state}`
}

// A partial refund does not close the remaining fulfillment. Quantities are item units.
export function partialRefundTitle(order, items = [], sales = []) {
  if (![1, 2, 3].includes(Number(order?.status))) return ''
  const remaining = items.reduce((sum, item) => sum + Math.max(0, Number(item.quantity) - refundedQuantity(item, sales)), 0)
  const refunded = items.some(item => refundedQuantity(item, sales) > 0)
  return refunded && remaining > 0
    ? `部分退款完成，剩余${remaining}件${{ 1: '待发货', 2: '待收货', 3: '已完成' }[Number(order.status)]}` : ''
}
