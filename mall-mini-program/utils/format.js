function money(value) { return Number(value || 0).toFixed(2) }
function product(raw = {}) {
  return {
    ...raw,
    productName: raw.productName || raw.name || '商城商品',
    subtitle: raw.subtitle || '',
    description: raw.detail || '',
    coverUrl: raw.coverUrl || raw.picUrl || '',
    salePrice: Number(raw.salePrice || raw.price || 0),
    stock: Math.max(0, Number(raw.stock || 0)),
    priceText: money(raw.salePrice || raw.price)
  }
}

module.exports = { money, product }
