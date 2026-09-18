const feedback = require('./feedback')
const request = require('./request')
const format = require('./format')
const cart = require('./cart')
const session = require('./session')
const auth = require('./auth')
const purchaseLimit = require('./purchase-limit')
const quantities = require('./quantity')
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
function quantity(productId) {
  try {
    if (typeof cart.productQuantity === 'function') return cart.productQuantity(productId)
    const id = format.identifier(productId)
    return (typeof cart.list === 'function' ? cart.list() : []).reduce((sum, row) => format.identifier(row.productId) === id ? sum + Number(row.quantity || 0) : sum, 0)
  } catch (_) { return 0 }
}
function decorate(rows) { return (rows || []).map(row => { const next = quantity(row.id); return { ...row, cartQuantity: next, cartQuantityInput: String(next) } }) }
function sync(page, productId) {
  const id = format.identifier(productId)
  if (!id || !page || !page.data || typeof page.setData !== 'function') return
  const next = quantity(id)
  const patch = {}
  for (const name of ['products', 'hotProducts']) {
    const rows = page.data[name]
    if (!Array.isArray(rows)) continue
    rows.forEach((row, index) => {
      if (format.identifier(row.id) !== id) return
      if (Number(row.cartQuantity || 0) !== next) patch[`${name}[${index}].cartQuantity`] = next
      if (String(row.cartQuantityInput ?? '') !== String(next)) patch[`${name}[${index}].cartQuantityInput`] = String(next)
    })
  }
  if (Object.keys(patch).length) page.setData(patch)
}
function syncAll(page) {
  if (!page || !page.data || typeof page.setData !== 'function') return
  const patch = {}
  for (const name of ['products', 'hotProducts']) {
    const rows = page.data[name]
    if (!Array.isArray(rows)) continue
    rows.forEach((row, index) => {
      const next = quantity(row.id)
      if (Number(row.cartQuantity || 0) !== next) patch[`${name}[${index}].cartQuantity`] = next
      if (String(row.cartQuantityInput ?? '') !== String(next)) patch[`${name}[${index}].cartQuantityInput`] = String(next)
    })
  }
  if (Object.keys(patch).length) page.setData(patch)
}
function show(page) { page._inactive = false; syncAll(page) }
function hide(page) {
  page._inactive = true; page.addSequence = (page.addSequence || 0) + 1
  page.addingId = ''
}
const methods = {
  noop() {},
  quickQuantityChanged(event) {
    const id = format.identifier(event.currentTarget.dataset.id)
    const value = quantities.sanitize(event.detail.value)
    const patch = {}
    for (const name of ['products', 'hotProducts']) {
      const rows = this.data[name]
      if (!Array.isArray(rows)) continue
      rows.forEach((row, index) => {
        if (format.identifier(row.id) === id) patch[`${name}[${index}].cartQuantityInput`] = value
      })
    }
    if (Object.keys(patch).length) this.setData(patch)
    return value
  },
  async quickQuantityCommit(event) {
    const id = format.identifier(event.currentTarget.dataset.id)
    const currentQuantity = quantity(id)
    if (!id || !currentQuantity || this.addingId || this._inactive) { sync(this, id); return }
    if (!auth.requireLogin(this.quickCartRoute || '/pages/category/index')) { sync(this, id); return }
    const sanitized = quantities.sanitize(event.detail.value)
    if (!sanitized) { sync(this, id); return }
    const target = quantities.resolve(sanitized, quantities.MAX_QUANTITY)
    if (target === currentQuantity) { sync(this, id); return }
    if (target < currentQuantity) {
      cart.setProductQuantity(id, target)
      sync(this, id)
      const tab = this.getTabBar && this.getTabBar()
      if (tab && tab.refreshCartCount) tab.refreshCartCount()
      return
    }
    const sequence = this.addSequence = (this.addSequence || 0) + 1
    const token = session.getToken()
    const current = () => !this._inactive && sequence === this.addSequence && token === session.getToken()
    this.addingId = id
    try {
      const detail = await request({ url: `/shop/products/${id}` })
      if (!current()) return
      if (!detail || String(detail.product && detail.product.id) !== id) throw new Error('商品信息不一致，请刷新后重试')
      const selection = await purchaseLimit.checkAddition(id, firstAvailableSku(detail), target - currentQuantity, { detail, isCurrent: current })
      if (!selection || !current()) return
      cart.add(selection.item)
      sync(this, id)
      const tab = this.getTabBar && this.getTabBar()
      if (tab && tab.refreshCartCount) tab.refreshCartCount()
    } catch (error) {
      if (current()) { sync(this, id); await feedback.notice(error.message || '数量修改失败，请稍后重试', '无法修改数量') }
    } finally { if (sequence === this.addSequence) this.addingId = '' }
  },
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
      sync(this, id)
      const tab = this.getTabBar && this.getTabBar()
      if (tab && tab.refreshCartCount) tab.refreshCartCount()
    } catch (error) { if (current()) await feedback.notice(error.message || '加购失败，请稍后重试', '未能加入购物车') }
    finally { if (sequence === this.addSequence) this.addingId = '' }
  },
  quickDecrease(event) {
    const id = format.identifier(event.currentTarget.dataset.id)
    if (!id || this.addingId || this._inactive) return
    if (!auth.requireLogin(this.quickCartRoute || '/pages/category/index')) return
    cart.decrementProduct(id)
    sync(this, id)
    const tab = this.getTabBar && this.getTabBar()
    if (tab && tab.refreshCartCount) tab.refreshCartCount()
  }
}
module.exports = { data, methods, show, hide, firstAvailableSku, decorate, sync, syncAll }
