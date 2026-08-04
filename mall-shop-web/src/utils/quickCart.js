const boundedPv = (pv, salePrice) => Math.min(
  Math.max(0, Number(pv || 0)),
  Math.max(0, Number(salePrice || 0)),
)

export const resolveQuickCartItem = (listProduct, detail = {}) => {
  const product = detail.product || listProduct
  const skus = Array.isArray(detail.skus) ? detail.skus : []
  const sku = skus.find((item) => Number(item.stock || 0) > 0) || null

  if (skus.length && !sku) return null
  if (!sku) {
    const salePrice = Number(product.salePrice || listProduct.salePrice || 0)
    return {
      ...listProduct,
      ...product,
      salePrice,
      pvValue: boundedPv(product.pvValue ?? listProduct.pvValue, salePrice),
      stock: Math.max(0, Number(product.stock ?? listProduct.stock ?? 0)),
    }
  }

  const salePrice = Number(sku.salePrice || 0)
  return {
    ...listProduct,
    ...product,
    skuId: sku.id,
    skuName: sku.skuName || '',
    skuAttrs: sku.attrsJson || '',
    coverUrl: sku.imageUrl || product.coverUrl || listProduct.coverUrl || '',
    salePrice,
    marketPrice: Number(sku.marketPrice || 0),
    costAmount: Number(sku.costAmount || 0),
    pvValue: boundedPv(Number(sku.pvValue || 0) > 0 ? sku.pvValue : product.pvValue, salePrice),
    stock: Math.max(0, Number(sku.stock || 0)),
  }
}
