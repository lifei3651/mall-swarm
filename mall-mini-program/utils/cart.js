const LEGACY_KEY = 'mall_mini_cart'
const LEGACY_NOTICE_KEY = 'mall_mini_cart_v2_legacy_notice'
const session = require('./session')
const { identifier } = require('./format')
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
  const rows = list()
  const key = `${item.productId}:${item.skuId || 0}`
  const existing = rows.find((row) => row.key === key)
  if (existing) {
    const quantity = Math.min(99, existing.quantity + (item.quantity || 1))
    // The caller has just checked current details: do not retain a stale unit price or name.
    Object.assign(existing, item, { key, quantity, selected: existing.selected })
  }
  else rows.unshift({ ...item, key, quantity: item.quantity || 1, selected: true })
  return save(rows)
}
function update(key, patch) { return save(list().map((row) => row.key === key ? { ...row, ...patch } : row)) }
function remove(key) { return save(list().filter((row) => row.key !== key)) }
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

module.exports = { list, add, update, remove, selectAll, selectOnly, clearSelected, selected, beginDirectCheckout, directItems, clearDirectCheckout, needsLegacyReview, acknowledgeLegacyReview }
