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
  if (!raw.startsWith('/api/shop/media/') && !raw.startsWith('/shop/media/')) return /^https:\/\/[^\s]+$/i.test(raw) || /^\/assets\/[\w/.-]+$/.test(raw) ? raw : ''
  const originMatch = String(runtime.API_BASE_URL || '').match(/^(https:\/\/[^/]+)/i)
  if (!originMatch) return raw
  return `${originMatch[1]}${raw.startsWith('/api/') ? raw : `/api${raw}`}`
}

function product(raw = {}) {
  const parse = (value) => { try { const rows = typeof value === 'string' ? JSON.parse(value) : value; return Array.isArray(rows) ? rows : [] } catch (_) { return [] } }
  const gallery = [raw.coverUrl || raw.picUrl, ...parse(raw.galleryUrls)].map(mediaUrl).filter(Boolean)
  return {
    ...raw,
    productName: raw.productName || raw.name || '商城商品',
    subtitle: raw.subtitle || '',
    description: raw.detail || raw.description || '',
    coverUrl: mediaUrl(raw.coverUrl || raw.picUrl),
    imageFailed: false,
    gallery: [...new Set(gallery)],
    detailImages: parse(raw.detailImages).map(mediaUrl).filter(Boolean),
    serviceTags: parse(raw.serviceTags).map((item) => typeof item === 'string' ? { title: item, enabled: true } : item)
      .filter((item) => item && item.enabled !== false && typeof item.title === 'string' && item.title.trim()),
    salePrice: Number(raw.salePrice ?? raw.price ?? 0),
    stock: Math.max(0, Number(raw.stock || 0)),
    priceText: money(raw.salePrice ?? raw.price)
  }
}

function sku(raw = {}) {
  let attrs = {}
  try { const parsed = typeof raw.attrsJson === 'string' ? JSON.parse(raw.attrsJson) : raw.attrsJson; if (parsed && !Array.isArray(parsed) && typeof parsed === 'object') attrs = parsed } catch (_) {}
  return { ...raw, imageUrl: mediaUrl(raw.imageUrl), priceText: money(raw.salePrice),
    attributes: Object.entries(attrs).filter(([, value]) => ['string', 'number'].includes(typeof value)).map(([name, value]) => ({ name, value: String(value) })) }
}
module.exports = { money, mediaUrl, product, identifier, sku }
