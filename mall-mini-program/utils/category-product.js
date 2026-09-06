const format = require('./format')

function card(raw) {
  const product = format.product(raw)
  const [priceInteger, priceDecimal] = product.priceText.split('.')
  const sales = raw.salesCount == null ? NaN : Number(raw.salesCount)
  return { ...product, priceInteger, priceDecimal,
    salesText: Number.isSafeInteger(sales) && sales >= 0 ? `已售 ${sales} 件` : '',
    soldOut: Number(product.status ?? 1) !== 1 || product.stock <= 0 }
}

// Always use refreshed details, not a list thumbnail's cached price or stock.
function limitMessage(productName, limit, remaining) {
  const name = String(productName || '当前商品').trim() || '当前商品'
  return remaining > 0 ? `${name}每位会员限购 ${limit} 件，您还可购买 ${remaining} 件`
    : `${name}每位会员限购 ${limit} 件，您已达到限购数量，无法继续加购`
}

function purchase(detail, skuId, rows, addedQuantity = 1) {
  if (!Number.isInteger(addedQuantity) || addedQuantity < 1 || addedQuantity > 99) throw new Error('购买数量必须为1至99的整数')
  const product = format.product(detail.product)
  if (!format.identifier(product.id) || Number(product.status ?? 1) !== 1) throw new Error('商品已下架，请刷新列表')
  const skus = Array.isArray(detail.skus) ? detail.skus : []
  const sku = skus.find((item) => String(item.id) === String(skuId))
  if (skuId && !sku) throw new Error('所选规格已失效，请重新选择')
  if (skus.length && (!sku || !format.identifier(sku.id) || Number(sku.status ?? 1) !== 1)) throw new Error('请选择可购买的商品规格')
  const stock = Math.floor(Number(sku ? sku.stock : product.stock))
  const rawPrice = sku ? sku.salePrice : detail.product.salePrice
  const price = rawPrice === null || rawPrice === undefined || rawPrice === '' ? NaN : Number(rawPrice)
  if (!Number.isFinite(stock) || stock <= 0) throw new Error('该商品或规格已售罄，请选择其他商品')
  if (!Number.isFinite(price) || price < 0) throw new Error('商品价格异常，请联系商城客服')
  const sameProduct = rows.filter((item) => String(item.productId) === String(product.id))
  const quantity = (item) => Math.max(0, Math.floor(Number(item.quantity) || 0))
  const existingSku = sameProduct.filter((item) => String(item.skuId || '') === String(sku ? sku.id : '')).reduce((sum, item) => sum + quantity(item), 0)
  const existingProduct = sameProduct.reduce((sum, item) => sum + quantity(item), 0)
  const productQuantity = existingProduct + addedQuantity
  if (existingSku + addedQuantity > Math.min(99, stock)) throw new Error(`购物车数量已达可购买上限（${Math.min(99, stock)}件）`)
  if (Number(product.purchaseLimit) > 0 && productQuantity > Number(product.purchaseLimit)) throw new Error(limitMessage(product.productName, Number(product.purchaseLimit), Math.max(0, Number(product.purchaseLimit) - existingProduct)))
  return { productQuantity, item: { productId: product.id, skuId: sku ? sku.id : null,
    productName: product.productName, coverUrl: format.mediaUrl(sku && sku.imageUrl) || product.coverUrl,
    salePrice: price, skuName: sku ? (sku.skuName || sku.specName || '') : '', quantity: addedQuantity } }
}

module.exports = { card, purchase, limitMessage }
