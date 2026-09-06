// Mirrored by the admin preview; the parity test must pass when this policy changes.
function identifier(value) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return ''
  const text = String(value ?? '')
  return /^[1-9]\d{0,18}$/.test(text) ? text : ''
}

function campaignIndex(rows, now = Date.now()) {
  const result = new Map()
  for (const row of Array.isArray(rows) ? rows : []) {
    const activity = row && row.activity, product = row && row.product
    if (!activity || !product || !['ACTIVE', 'UPCOMING'].includes(row.activityState)) continue
    const id = identifier(activity.id), productId = identifier(activity.productId)
    const start = Date.parse(String(activity.startTime || '').replace(' ', 'T'))
    const end = Date.parse(String(activity.endTime || '').replace(' ', 'T'))
    const price = activity.flashPrice
    if (!id || !productId || productId !== identifier(product.id) || Number(activity.status) !== 1 || Number(product.status) !== 1) continue
    if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start || end <= now) continue
    if (price === null || price === undefined || price === '' || !Number.isFinite(Number(price)) || Number(price) < 0) continue
    if (Number(activity.availableStock) <= 0 || !Number.isFinite(Number(activity.availableStock))) continue
    const active = now >= start
    const remaining = Math.max(0, Math.floor(((active ? end : start) - now) / 1000))
    const days = Math.floor(remaining / 86400)
    const time = [Math.floor(remaining % 86400 / 3600), Math.floor(remaining % 3600 / 60), remaining % 60]
      .map(value => String(value).padStart(2, '0')).join(':')
    const campaign = { id, productId, activityName: String(activity.activityName || ''),
      activityState: active ? 'ACTIVE' : 'UPCOMING', priceText: Number(price).toFixed(2),
      label: active ? '限时活动进行中' : '限时活动即将开始', actionLabel: active ? '去抢购' : '去看看',
      countdown: `${active ? '距结束' : '距开始'} ${days ? `${days}天 ` : ''}${time}` }
    const current = result.get(productId)
    // Same choice as H5: first eligible activity, with active taking priority over upcoming.
    if (!current || (current.activityState !== 'ACTIVE' && active)) result.set(productId, campaign)
  }
  return result
}

function decorateCampaignProducts(products, rows, layout, now = Date.now()) {
  const index = layout === 'campaign-feed' ? campaignIndex(rows, now) : new Map()
  return (Array.isArray(products) ? products : []).map(product => {
    const campaign = Number(product.status) === 1 ? index.get(identifier(product.id)) || null : null
    return { ...product, campaign, priceText: campaign ? campaign.priceText : Number(product.salePrice || 0).toFixed(2) }
  })
}

export { campaignIndex, decorateCampaignProducts }
