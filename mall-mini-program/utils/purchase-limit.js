const request = require('./request')
const session = require('./session')
const format = require('./format')
const productRules = require('./category-product')

// Same cumulative-per-product policy as H5: all SKUs share a member's allowance.
async function checkLimit(product, quantity) {
  if (!Number.isInteger(quantity) || quantity < 1) throw new Error('购买数量无效，请重新选择')
  const limit = Math.max(0, Math.trunc(Number(product.purchaseLimit) || 0))
  if (limit > 0 && quantity > limit) throw new Error(productRules.limitMessage(product.productName, limit, 0))
  const token = session.getToken()
  if (!token) return
  const result = await request({ url: `/shop/products/${format.identifier(product.id)}/purchase-limit/check`, method: 'POST', params: { quantity } })
  if (session.getToken() !== token) throw new Error('登录状态已变化，请重新操作')
  if (!result || result.allowed !== true) throw new Error(result && result.message || '未能确认商品限购，请稍后重试')
}

async function checkAddition(productId, skuId, quantity = 1, options = {}) {
  const id = format.identifier(productId)
  if (!id) throw new Error('商品编号无效，请重新选择')
  const getRows = options.getRows || (() => require('./cart').list())
  const token = session.getToken(), snapshot = JSON.stringify(getRows())
  const current = () => token === session.getToken() && (!options.isCurrent || options.isCurrent())
  const detail = options.detail || await request({ url: `/shop/products/${id}` })
  if (!current()) return null
  if (!detail || String(detail.product && detail.product.id) !== id) throw new Error('商品信息不一致，请刷新后重试')
  if (snapshot !== JSON.stringify(getRows())) throw new Error('购物车数量已变化，请重新操作')
  const selection = productRules.purchase(detail, skuId, getRows(), quantity)
  await checkLimit(detail.product, selection.productQuantity)
  if (!current()) return null
  if (snapshot !== JSON.stringify(getRows())) throw new Error('购物车数量已变化，请重新操作')
  return selection
}

module.exports = { checkLimit, checkAddition }
