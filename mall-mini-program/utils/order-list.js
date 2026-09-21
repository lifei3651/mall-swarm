const rules = require('./h5-rules/orderListRules')
const { identifier, afterSaleEligibility } = require('../pages/order-detail/policy')

function decorate(rows) {
  const normalized = rows.map(row => ({ ...row, order: { ...row.order, status: Number(row.order.status) },
    afterSales: (row.afterSales || []).map(sale => ({ ...sale, status: Number(sale.status) })) }))
  return normalized.map(row => {
    const active = row.afterSales.some(sale => [0, 4, 5, 6, 7, 8].includes(sale.status)) || row.order.status === 5
    const owner = rules.isTradeActionOwner(row, normalized)
    const autoReceiveText = row.order.status === 2 && row.autoReceiveEnabled && !active
      ? (row.autoReceiveDeadline ? `预计 ${String(row.autoReceiveDeadline).replace('T', ' ').slice(0, 16)} 自动确认收货` : `发货满 ${Number(row.autoReceiveDays || 15)} 天自动确认收货`) : ''
    const logisticsText = row.afterSaleText
      ? `售后进度：${row.afterSaleText}`
      : autoReceiveText || ({ 0: '付款后将安排发货', 1: '商家正在准备商品', 2: '包裹已发出，可查看物流进度', 3: '订单已完成', 4: '订单已取消' }[row.order.status] || '订单处理中')
    return { ...row,
      canCancel: Boolean(identifier(row.order.id) && row.order.status === 0 && owner),
      canPay: Boolean(identifier(row.order.id) && row.order.status === 0 && owner && ['WECHAT', 'BALANCE'].includes(row.order.payType)),
      // 列表页只保留紧凑动作名；联合订单范围在详情页和二次确认中说明，
      // 避免 320px/rpx 小屏把三个操作按钮裁切。
      payLabel: '立即支付',
      cancelLabel: '取消订单',
      canApplyAfterSale: rules.canApplyAfterSale(row) && afterSaleEligibility(row).allowed,
      autoReceiveText,
      logisticsText,
      showMissingAction: Boolean(autoReceiveText) && rules.canApplyAfterSale(row) && afterSaleEligibility(row).allowed,
      canViewLogistics: [2, 3].includes(row.order.status) && (row.shipments || []).some((shipment) => shipment.deliveryNo),
      canRebuy: row.order.status !== 0 && (row.items || []).some((item) => identifier(item.productId)),
      statusTone: row.order.status === 4 ? 'cancelled' : (row.order.status === 3 && !active && !Number(row.pendingReviewCount || 0) ? 'completed' : 'active')
    }
  })
}
module.exports = { decorate }
