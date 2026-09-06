const request = require('./request')
const format = require('./format')
const purchaseLimit = require('./purchase-limit')
const session = require('./session')

// At most four concurrent reads and one fetch per distinct product. Never change user quantities.
async function refresh(rows, options = {}) {
  if (!Array.isArray(rows) || rows.length > 200) throw new Error('单次最多结算200项商品，请拆分处理')
  const token = session.getToken()
  const ids = [...new Set(rows.map((row) => format.identifier(row.productId)).filter(Boolean))]
  const details = new Map()
  const quantities = new Map(), limitErrors = new Map()
  rows.forEach((row) => { const id = format.identifier(row.productId); quantities.set(id, (quantities.get(id) || 0) + Number(row.quantity || 0)) })
  let cursor = 0
  await Promise.all(Array.from({ length: Math.min(4, ids.length) }, async () => {
    while (cursor < ids.length) {
      const id = ids[cursor++]
      try { details.set(id, await request({ url: `/shop/products/${id}` })) }
      catch (_) { details.set(id, null) }
      if (session.getToken() !== token) throw new Error('登录状态已变化，请重新操作')
      if (options.checkLimits && details.get(id) && details.get(id).product) {
        const product = details.get(id).product
        try {
          if (String(product.id) !== id) throw new Error('商品信息不一致，请刷新后重试')
          await purchaseLimit.checkLimit(product, quantities.get(id))
        } catch (error) { limitErrors.set(id, error.message || '未能确认商品限购，请重试') }
      }
    }
  }))
  if (session.getToken() !== token) throw new Error('登录状态已变化，请重新操作')
  return rows.map((row) => {
    const detail = details.get(format.identifier(row.productId))
    const product = detail && detail.product
    const skuId = row.skuId ? format.identifier(row.skuId) : ''
    const skus = detail && Array.isArray(detail.skus) ? detail.skus : []
    const sku = skuId && skus.find((item) => format.identifier(item.id) === skuId)
    let unavailable = ''
    if (!product) unavailable = '商品信息暂不可用，请重试'
    else if (String(product.id) !== String(row.productId)) unavailable = '商品信息不一致，请重试'
    else if (Number(product.status) !== 1) unavailable = '商品已下架'
    else if ((row.skuId && !sku) || (!row.skuId && skus.length)) unavailable = '规格已变更，请重新选择'
    else if (sku && Number(sku.status ?? 1) !== 1) unavailable = '该商品规格已下架，请重新选择'
    const stock = Number(sku ? sku.stock : product && product.stock)
    const rawPrice = sku ? sku.salePrice : product && product.salePrice
    const price = rawPrice === null || rawPrice === undefined || rawPrice === '' ? NaN : Number(rawPrice)
    const limit = Number(product && product.purchaseLimit || 0)
    if (!unavailable && (!Number.isInteger(stock) || stock < 1)) unavailable = '暂时缺货'
    if (!unavailable && (!Number.isInteger(row.quantity) || row.quantity < 1 || row.quantity > 99 || row.quantity > stock)) unavailable = '数量超过库存或购买上限，请调整'
    if (!unavailable && limit > 0 && quantities.get(format.identifier(row.productId)) > limit) unavailable = `每位会员限购${limit}件，不同规格合并计算，请调整`
    if (!unavailable && (!Number.isFinite(price) || price < 0)) unavailable = '商品价格异常，请稍后重试'
    if (!unavailable && limitErrors.has(format.identifier(row.productId))) unavailable = limitErrors.get(format.identifier(row.productId))
    return { ...row, unavailable, stock, purchaseLimit: limit,
      productName: product && product.productName || row.productName,
      skuName: sku ? sku.skuName || '' : row.skuName,
      coverUrl: format.mediaUrl(sku && sku.imageUrl || product && product.coverUrl || row.coverUrl),
      salePrice: Number.isFinite(price) && price >= 0 ? price : row.salePrice }
  })
}
module.exports = { refresh }
