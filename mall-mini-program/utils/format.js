const runtime = require('../config/runtime')

function money(value) { return Number(value || 0).toFixed(2) }

// Identifiers are opaque decimal strings. Never recover an already rounded Number.
function identifier(value) {
  if (typeof value === 'number' && (!Number.isSafeInteger(value) || value <= 0)) return ''
  const text = String(value ?? '').trim()
  return /^[1-9]\d{0,18}$/.test(text) ? text : ''
}

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
    description: raw.detail || raw.description || '',
    coverUrl: mediaUrl(raw.coverUrl || raw.picUrl),
    imageFailed: false,
    salePrice: Number(raw.salePrice || raw.price || 0),
    stock: Math.max(0, Number(raw.stock || 0)),
    priceText: money(raw.salePrice || raw.price)
  }
}

module.exports = { money, mediaUrl, product, identifier }
