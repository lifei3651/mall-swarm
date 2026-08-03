/**
 * 金额格式化：保留两位小数
 * 后端已返回元为单位的金额，前端直接格式化
 * @param {number|string} val - 金额（元）
 * @returns {string}
 */
export function money(val) {
  if (val == null) return '0.00'
  const num = Number(val)
  if (isNaN(num)) return '0.00'
  return num.toFixed(2)
}

/**
 * 将接口返回的 ISO 本地时间显示为常见的日期时间格式。
 */
export function dateTime(val) {
  if (!val) return '-'
  return String(val).replace('T', ' ').slice(0, 19)
}

/**
 * 订单状态映射
 */
export const ORDER_STATUS_MAP = {
  0: '待付款',
  1: '待发货',
  2: '已发货',
  3: '已完成',
  4: '已关闭',
  5: '售后中',
}

/**
 * 获取订单状态名称
 * @param {number} status
 * @returns {string}
 */
export function statusName(status) {
  return ORDER_STATUS_MAP[status] || '未知'
}

/**
 * 拼接地址
 * @param {object} addr - 地址对象
 * @returns {string}
 */
export function joinAddress(addr) {
  if (!addr) return ''
  const parts = [addr.province, addr.city, addr.district, addr.detailAddress].filter(Boolean)
  return parts.join(' ')
}
