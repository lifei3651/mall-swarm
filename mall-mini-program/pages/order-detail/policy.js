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

module.exports = { identifier, remainingItems, afterSaleEligibility }
