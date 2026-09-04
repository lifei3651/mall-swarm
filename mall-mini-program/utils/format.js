const runtime = require('../config/runtime')

function money(value) { return Number(value || 0).toFixed(2) }

function mediaUrl(value) {
  const raw = String(value || '').trim()
  if (!raw) return ''
  if (raw.startsWith('//')) return `https:${raw}`
  if (!raw.startsWith('/api/shop/media/') && !raw.startsWith('/shop/media/')) return raw
  const originMatch = String(runtime.API_BASE_URL || '').match(/^(https:\/\/[^/]+)/i)
  if (!originMatch) return raw
  return `${originMatch[1]}${raw.startsWith('/api/') ? raw : `/api${raw}`}`
}

function product(raw = {}) {
  return {
    ...raw,
    productName: raw.productName || raw.name || '商城商品',
    subtitle: raw.subtitle || '',
    description: raw.detail || '',
    coverUrl: mediaUrl(raw.coverUrl || raw.picUrl),
    salePrice: Number(raw.salePrice || raw.price || 0),
    stock: Math.max(0, Number(raw.stock || 0)),
    priceText: money(raw.salePrice || raw.price)
  }
}

module.exports = { money, mediaUrl, product }
