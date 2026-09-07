// H5 quantityInput semantics; the backend accepts a positive Java Integer.
const MAX_QUANTITY = 2147483647
const { sanitizePositiveIntegerInput: sanitize, resolvePositiveIntegerQuantity } = require('./h5-rules/quantityInput')
function maximum(stock, limit) {
  const available = Math.max(1, Math.min(MAX_QUANTITY, Math.floor(Number(stock) || 1)))
  return Number(limit) > 0 ? Math.min(available, Math.floor(Number(limit))) : available
}
function resolve(value, max) {
  return resolvePositiveIntegerQuantity(value, maximum(max))
}
function valid(value) { return Number.isInteger(value) && value > 0 && value <= MAX_QUANTITY }
module.exports = { MAX_QUANTITY, sanitize, maximum, resolve, valid }
