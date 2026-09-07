// Keep database identifiers opaque: converting a Snowflake ID to Number loses digits.
function identifier(value) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return ''
  const id = String(value == null ? '' : value)
  return /^[1-9]\d{0,18}$/.test(id) ? id : ''
}

function remainingItems(detail) {
  const sales = detail.afterSales || []
  return (detail.items || []).map((item) => {
    const used = sales.filter((sale) => Number(sale.applyType) === 3
      ? [0, 4, 5, 7, 8].includes(Number(sale.status))
      : [0, 1, 4, 5, 6].includes(Number(sale.status)))
      .reduce((sum, sale) => sum + (sale.items || [])
        .filter((line) => String(line.orderItemId) === String(item.id))
        .reduce((quantity, line) => quantity + Number(line.refundQuantity || 0), 0), 0)
    return { ...item, id: identifier(item.id), remaining: Math.max(0, Number(item.quantity || 0) - used) }
  })
}

function afterSaleEligibility(detail, now = Date.now()) {
  const order = detail.order || {}
  let reason = ''
  let deadline = Date.parse(String(detail.afterSaleDeadline || '').replace(' ', 'T'))
  if (!Number.isFinite(deadline) && detail.afterSaleWindowMode !== 'RECEIVED') {
    const created = Date.parse(String(order.createTime || '').replace(' ', 'T'))
    if (Number.isFinite(created)) deadline = created + Number(detail.afterSaleWindowDays == null ? 7 : detail.afterSaleWindowDays) * 86400000
  }
  if (!identifier(order.id)) reason = '订单编号不正确，请重新打开订单'
  else if (detail.afterSaleSelfServiceEnabled === false) reason = '当前订单暂不支持自助售后，请联系商城客服'
  else if ([0, 4].includes(Number(order.status))) reason = '当前订单状态不能申请售后'
  else if (Number.isFinite(deadline) && now >= deadline) reason = '已超过自助售后期限，请联系商城客服'
  else if ((detail.afterSales || []).some((sale) => [0, 4, 5, 6, 7, 8].includes(Number(sale.status)))) reason = '该订单已有处理中售后，请查看售后进度'
  else if (!remainingItems(detail).some((item) => item.id && item.remaining > 0)) reason = '该订单已无可申请售后的商品数量'
  return { allowed: !reason, reason, canExchange: [2, 3].includes(Number(order.status)) }
}

function amountLabel(order = {}) {
  const status = Number(order.status)
  if (status === 0) return '待付金额'
  if ([1, 2, 3, 5].includes(status)) return '实付金额'
  return '订单金额'
}

// A child ID is resolved to its original parent trade by the payment service.
// Only offer payment after the complete group has been loaded from the server.
function paymentSummary(rows = []) {
  const orders = rows.map((row) => row.order || {})
  const pending = orders.length > 0 && orders.every((order) => Number(order.status) === 0)
  const paid = orders.length > 0 && orders.every((order) => [1, 2, 3, 5].includes(Number(order.status)))
  const ids = orders.map((order) => identifier(order.id))
  const first = orders[0] || {}
  const sameTrade = orders.every((order) => String(order.tradeId || '') === String(first.tradeId || ''))
  const validGroup = sameTrade && (orders.length === 1 || Boolean(identifier(first.tradeId)))
  const totalFen = orders.reduce((sum, order) => sum + Math.round(Number(order.payAmount == null ? order.totalAmount : order.payAmount) * 100), 0)
  const canPay = pending && validGroup && ids.every(Boolean) && new Set(ids).size === ids.length
    && orders.every((order) => Number(order.payAmount == null ? order.totalAmount : order.payAmount) >= 0)
    && orders.every((order) => String(order.payType || '').toUpperCase() === 'WECHAT')
    && Number.isSafeInteger(totalFen) && totalFen > 0
  return {
    summaryLabel: pending ? '待付金额' : paid ? '实付金额' : '订单金额',
    summaryMeta: orders.length > 1 ? `合并交易 · ${orders.length} 个订单，分别发货和售后` : first.tradeId ? '合并交易中的当前子订单' : '商品及运费金额以订单明细为准',
    totalText: Number.isFinite(totalFen) ? (totalFen / 100).toFixed(2) : '--',
    payOrderId: canPay ? ids[0] : '',
    paymentHint: pending && !canPay ? '当前订单暂不能在此付款，请核对原支付方式或联系商城客服。' : ''
  }
}

module.exports = { identifier, remainingItems, afterSaleEligibility, amountLabel, paymentSummary }
