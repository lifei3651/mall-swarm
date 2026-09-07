const rules = require('./h5-rules/orderListRules')
const { identifier, afterSaleEligibility } = require('../pages/order-detail/policy')

function decorate(rows) {
  const normalized = rows.map(row => ({ ...row, order: { ...row.order, status: Number(row.order.status) },
    afterSales: (row.afterSales || []).map(sale => ({ ...sale, status: Number(sale.status) })) }))
  return normalized.map(row => {
    const active = row.afterSales.some(sale => [0, 4, 5, 6, 7, 8].includes(sale.status)) || row.order.status === 5
    const owner = rules.isTradeActionOwner(row, normalized)
    return { ...row,
      canCancel: Boolean(identifier(row.order.id) && row.order.status === 0 && owner),
      canPay: Boolean(identifier(row.order.id) && row.order.status === 0 && owner && ['WECHAT', 'BALANCE'].includes(row.order.payType)),
      payLabel: row.order.tradeId ? '支付全部子单' : '立即支付',
      cancelLabel: row.order.tradeId ? '取消联合订单' : '取消订单',
      canApplyAfterSale: rules.canApplyAfterSale(row) && afterSaleEligibility(row).allowed,
      autoReceiveText: row.order.status === 2 && row.autoReceiveEnabled && !active
        ? (row.autoReceiveDeadline ? `预计 ${String(row.autoReceiveDeadline).replace('T', ' ').slice(0, 16)} 自动确认收货` : `发货满 ${Number(row.autoReceiveDays || 15)} 天自动确认收货`) : ''
    }
  })
}
module.exports = { decorate }
