// H5 quantityInput semantics; the backend accepts a positive Java Integer.
const MAX_QUANTITY = 2147483647
function sanitize(value, maxLength = 10) {
  const normalized = String(value ?? '').replace(/[０-９]/g, digit => String(digit.charCodeAt(0) - '０'.charCodeAt(0))).trimStart()
  const leading = (normalized.match(/^\d+/) || [''])[0]
  return leading.replace(/^0+(?=\d)/, '').slice(0, Math.max(1, Number(maxLength) || 1))
}
function maximum(stock, limit) {
  const available = Math.max(1, Math.min(MAX_QUANTITY, Math.floor(Number(stock) || 1)))
  return Number(limit) > 0 ? Math.min(available, Math.floor(Number(limit))) : available
}
function resolve(value, max) {
  const parsed = Number(sanitize(value))
  if (!Number.isSafeInteger(parsed) || parsed <= 0) return 1
  return Math.min(parsed, maximum(max))
}
function valid(value) { return Number.isInteger(value) && value > 0 && value <= MAX_QUANTITY }
module.exports = { MAX_QUANTITY, sanitize, maximum, resolve, valid }
