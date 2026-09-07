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
  const guarantees = {
    '七天无理由': '符合平台规则且商品完好的，可在商城当前配置的售后期限内申请无理由退货。',
    '正品保障': '严控商品来源与质量，为消费者提供品质保障。',
    '极速退款': '售后审核通过后，平台将尽快完成退款处理。',
    '破损包赔': '商品运输途中发生破损，可凭有效凭证申请售后处理。',
    '运费险': '符合条件的退货订单可按保险规则获得退货运费补偿。'
  }
  const parse = (value) => { try { const rows = typeof value === 'string' ? JSON.parse(value) : value; return Array.isArray(rows) ? rows : [] } catch (_) { return [] } }
  const gallery = [raw.coverUrl || raw.picUrl, ...parse(raw.galleryUrls)].map(mediaUrl).filter(Boolean)
  return {
    ...raw,
    productName: raw.productName || raw.name || '商城商品',
    subtitle: raw.subtitle || '',
    description: raw.detail || raw.description || '',
    afterSalePolicy: String(raw.afterSalePolicy || '').trim() || '1. 签收商品时请先检查外包装和商品状态，如有破损、错发或漏发，请及时联系客服。\n2. 商品售后申请须符合商城交易与售后规则，并提供必要的订单信息和凭证。\n3. 退款金额以订单实际支付金额和审核结果为准，处理进度可在订单详情中查看。\n4. 退货运费承担方式以售后审核结果和商品页面说明为准。\n5. 不同商品可能存在特殊保存、使用或售后要求，请以商品详情和客服说明为准。',
    coverUrl: mediaUrl(raw.coverUrl || raw.picUrl),
    imageFailed: false,
    gallery: [...new Set(gallery)],
    detailImages: parse(raw.detailImages).map(mediaUrl).filter(Boolean),
    serviceTags: parse(raw.serviceTags).map((item) => typeof item === 'string' ? { title: item, description: guarantees[item] || '以商城售后规则及商品实际情况为准。', enabled: true } : item)
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
