/**
 * 统一将接口返回的 ISO 本地时间显示为常见的日期时间格式。
 * 这里只处理界面显示，不改变接口参数或数据库中的原始值。
 */
export function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

/**
 * Element Plus 表格列的通用时间格式化器。
 */
export function formatDateTimeCell(_row, _column, cellValue) {
  return formatDateTime(cellValue)
}
