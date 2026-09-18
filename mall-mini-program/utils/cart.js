const LEGACY_KEY = 'mall_mini_cart'
const LEGACY_NOTICE_KEY = 'mall_mini_cart_v2_legacy_notice'
const session = require('./session')
const { identifier } = require('./format')
const quantities = require('./quantity')
let direct = null

function ownerKey() {
  const member = session.getMember()
  const id = session.getToken() && member && identifier(member.id)
  return id ? `mall_mini_cart_v2:member:${id}` : ''
}
function list() {
  const key = ownerKey()
  const rows = key && wx.getStorageSync(key)
  return Array.isArray(rows) ? rows : []
}
function save(rows) {
  const key = ownerKey()
  if (!key) throw new Error('登录信息不完整，请重新登录后操作购物车')
  wx.setStorageSync(key, rows)
  return rows
}
// The old shared cache has no trustworthy owner. Preserve it, but never assign it to a member.
function needsLegacyReview() {
  const rows = wx.getStorageSync(LEGACY_KEY)
  return Boolean(ownerKey() && Array.isArray(rows) && rows.length && !wx.getStorageSync(LEGACY_NOTICE_KEY))
}
function acknowledgeLegacyReview() { if (ownerKey()) wx.setStorageSync(LEGACY_NOTICE_KEY, true) }
function add(item) {
  if (!quantities.valid(item.quantity || 1)) throw new Error('购买数量无效')
  const rows = list()
  const key = `${item.productId}:${item.skuId || 0}`
  const existing = rows.find((row) => row.key === key)
  if (existing) {
    const quantity = existing.quantity + (item.quantity || 1)
    if (!quantities.valid(quantity)) throw new Error('购买数量超出支持范围')
    // The caller has just checked current details: do not retain a stale unit price or name.
    Object.assign(existing, item, { key, quantity, selected: existing.selected })
  }
  else rows.push({ ...item, key, quantity: item.quantity || 1, selected: true })
  return save(rows)
}
function update(key, patch) { return save(list().map((row) => row.key === key ? { ...row, ...patch } : row)) }
function remove(key) { return save(list().filter((row) => row.key !== key)) }
function productQuantity(productId) {
  const id = identifier(productId)
  return id ? list().reduce((sum, row) => identifier(row.productId) === id && quantities.valid(row.quantity) ? sum + row.quantity : sum, 0) : 0
}
function decrementProduct(productId) {
  const id = identifier(productId)
  if (!id) return list()
  const rows = list()
  let index = -1
  for (let i = rows.length - 1; i >= 0; i--) {
    if (identifier(rows[i].productId) === id) { index = i; break }
  }
  if (index < 0) return rows
  if (rows[index].quantity <= 1) rows.splice(index, 1)
  else rows[index] = { ...rows[index], quantity: rows[index].quantity - 1 }
  return save(rows)
}
function setProductQuantity(productId, target) {
  const id = identifier(productId)
  const next = Number(target)
  if (!id || !quantities.valid(next)) throw new Error('购买数量无效')
  const rows = list()
  const current = rows.reduce((sum, row) => identifier(row.productId) === id ? sum + Number(row.quantity || 0) : sum, 0)
  if (next >= current) return rows
  let excess = current - next
  for (let index = rows.length - 1; index >= 0 && excess > 0; index--) {
    if (identifier(rows[index].productId) !== id) continue
    const amount = Number(rows[index].quantity || 0)
    if (amount <= excess) { rows.splice(index, 1); excess -= amount }
    else { rows[index] = { ...rows[index], quantity: amount - excess }; excess = 0 }
  }
  return save(rows)
}
function removeMany(keys) { const targets = new Set(keys); return save(list().filter(row => !targets.has(row.key))) }
function clear() { direct = null; return save([]) }
function count() { return list().reduce((sum, row) => sum + (quantities.valid(row.quantity) ? row.quantity : 0), 0) }
function selectAll(selected) { return save(list().map((row) => ({ ...row, selected: Boolean(selected) }))) }
function selectOnly(key) { return save(list().map((row) => ({ ...row, selected: row.key === key }))) }
function clearSelected() { return save(list().filter((row) => !row.selected)) }
function selected() { return list().filter((row) => row.selected) }
function beginDirectCheckout(item) {
  const token = session.getToken()
  const owner = ownerKey()
  if (!token || !owner) return false
  direct = { token, owner, item: { ...item, key: `${item.productId}:${item.skuId || 0}`, selected: true } }
  return true
}
function directItems() {
  if (!direct || !session.getToken() || direct.token !== session.getToken() || direct.owner !== ownerKey()) { direct = null; return [] }
  return [{ ...direct.item }]
}
function clearDirectCheckout() { direct = null }

module.exports = { list, add, update, remove, removeMany, clear, count, productQuantity, decrementProduct, setProductQuantity, selectAll, selectOnly, clearSelected, selected, beginDirectCheckout, directItems, clearDirectCheckout, needsLegacyReview, acknowledgeLegacyReview }
