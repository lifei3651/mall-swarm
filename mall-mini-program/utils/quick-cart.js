const feedback = require('./feedback')
const request = require('./request')
const format = require('./format')
const cart = require('./cart')
const session = require('./session')
const auth = require('./auth')
const purchaseLimit = require('./purchase-limit')
const { resolveQuickCartItem } = require('./h5-rules/quickCart')

// H5 lists add the first in-stock SKU; explicit selection belongs to details.
function firstAvailableSku(detail) {
  const skus = Array.isArray(detail.skus) ? detail.skus : []
  if (!skus.length) return null
  // Preserve native fail-closed handling of explicitly disabled SKUs.
  const available = skus.filter(item => Number(item.status ?? 1) === 1)
  if (!available.length) throw new Error('该商品暂时缺货')
  const selection = resolveQuickCartItem(detail.product, { ...detail, skus: available })
  if (!selection) throw new Error('该商品暂时缺货')
  return selection.skuId
}
// An in-flight request is a logic lock, not a visual disabled state.
const data = {}
function show(page) { page._inactive = false }
function hide(page) {
  page._inactive = true; page.addSequence = (page.addSequence || 0) + 1
  page.addingId = ''
}
const methods = {
  async quickAdd(event) {
    const id = format.identifier(event.currentTarget.dataset.id)
    if (!id || this.addingId || this._inactive) return
    if (!auth.requireLogin(this.quickCartRoute || '/pages/category/index')) return
    const sequence = this.addSequence = (this.addSequence || 0) + 1
    const token = session.getToken()
    const current = () => !this._inactive && sequence === this.addSequence && token === session.getToken()
    this.addingId = id
    try {
      const detail = await request({ url: `/shop/products/${id}` })
      if (!current()) return
      if (!detail || String(detail.product && detail.product.id) !== id) throw new Error('商品信息不一致，请刷新后重试')
      const selection = await purchaseLimit.checkAddition(id, firstAvailableSku(detail), 1, { detail, isCurrent: current })
      if (!selection || !current()) return
      cart.add(selection.item)
      const tab = this.getTabBar && this.getTabBar()
      if (tab && tab.refreshCartCount) tab.refreshCartCount()
    } catch (error) { if (current()) await feedback.notice(error.message || '加购失败，请稍后重试', '未能加入购物车') }
    finally { if (sequence === this.addSequence) this.addingId = '' }
  }
}
module.exports = { data, methods, show, hide, firstAvailableSku }
