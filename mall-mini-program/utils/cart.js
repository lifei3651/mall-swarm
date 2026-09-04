const CART_KEY = 'mall_mini_cart'

function list() { return wx.getStorageSync(CART_KEY) || [] }
function save(rows) { wx.setStorageSync(CART_KEY, rows); return rows }
function add(item) {
  const rows = list()
  const key = `${item.productId}:${item.skuId || 0}`
  const existing = rows.find((row) => row.key === key)
  if (existing) existing.quantity = Math.min(99, existing.quantity + (item.quantity || 1))
  else rows.unshift({ ...item, key, quantity: item.quantity || 1, selected: true })
  return save(rows)
}
function update(key, patch) { return save(list().map((row) => row.key === key ? { ...row, ...patch } : row)) }
function remove(key) { return save(list().filter((row) => row.key !== key)) }
function selectAll(selected) { return save(list().map((row) => ({ ...row, selected: Boolean(selected) }))) }
function selectOnly(key) { return save(list().map((row) => ({ ...row, selected: row.key === key }))) }
function clearSelected() { return save(list().filter((row) => !row.selected)) }
function selected() { return list().filter((row) => row.selected) }

module.exports = { list, add, update, remove, selectAll, selectOnly, clearSelected, selected }
