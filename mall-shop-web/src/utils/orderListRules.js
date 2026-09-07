// Extracted unchanged from OrdersView: H5 remains the rule source for both clients.
export const isTradeActionOwner = (item, rows) => !item.order?.tradeId
  || rows.find((row) => String(row.order?.tradeId || '') === String(item.order.tradeId))?.order?.id === item.order.id

export const afterSaleDeadline = (item) => {
  const configured = Date.parse(String(item?.afterSaleDeadline || '').replace(' ', 'T'))
  if (Number.isFinite(configured)) return configured
  if (item?.afterSaleWindowMode === 'RECEIVED') return Number.POSITIVE_INFINITY
  const created = Date.parse(String(item?.order?.createTime || '').replace(' ', 'T'))
  return Number.isFinite(created) ? created + Number(item?.afterSaleWindowDays ?? 7) * 24 * 60 * 60 * 1000 : Number.POSITIVE_INFINITY
}
export const unavailableAfterSaleQuantity = (item) => (item.afterSales || [])
  .filter((sale) => Number(sale.applyType) === 3
    ? [0, 4, 5, 7, 8].includes(Number(sale.status))
    : [0, 1, 4, 5, 6].includes(Number(sale.status)))
  .flatMap((sale) => sale.items || [])
  .reduce((sum, line) => sum + Number(line.refundQuantity || 0), 0)
export const orderQuantity = (item) => (item.items || []).reduce((sum, line) => sum + Number(line.quantity || 0), 0)
export const canApplyAfterSale = (item, now = Date.now()) => ![0, 4].includes(item.order?.status)
  && item.afterSaleSelfServiceEnabled !== false
  && now < afterSaleDeadline(item)
  && !(item.afterSales || []).some((sale) => [0, 4, 5, 6, 7, 8].includes(sale.status))
  && unavailableAfterSaleQuantity(item) < orderQuantity(item)
