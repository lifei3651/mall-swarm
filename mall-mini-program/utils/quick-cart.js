const feedback = require('./feedback')
const request = require('./request')
const format = require('./format')
const categoryProduct = require('./category-product')
const cart = require('./cart')
const session = require('./session')
const auth = require('./auth')
const purchaseLimit = require('./purchase-limit')

const data = { addingId: '', skuVisible: false, skuProduct: {}, skuOptions: [], selectedSkuId: '', selectedPrice: '', confirming: false }
function show(page) { page._inactive = false }
function hide(page) {
  page._inactive = true; page.addSequence = (page.addSequence || 0) + 1
  page.closeSku(); page.setData({ addingId: '', confirming: false })
}
const methods = {
  async quickAdd(event) {
    const id = format.identifier(event.currentTarget.dataset.id)
    if (!id || this.data.addingId || this.data.skuVisible || this._inactive) return
    if (!auth.requireLogin(this.quickCartRoute || '/pages/category/index')) return
    const sequence = this.addSequence = (this.addSequence || 0) + 1
    const token = session.getToken()
    const current = () => !this._inactive && sequence === this.addSequence && token === session.getToken()
    this.setData({ addingId: id })
    try {
      const detail = await request({ url: `/shop/products/${id}` })
      if (!current()) return
      if (String(detail.product && detail.product.id) !== id) throw new Error('商品信息不一致，请刷新后重试')
      if (Number(detail.product.status ?? 1) !== 1) throw new Error('该商品已下架，请刷新列表')
      const skus = (detail.skus || []).filter((item) => Number(item.status ?? 1) === 1)
      if (skus.length) {
        if (!skus.some((item) => Number(item.stock) > 0)) throw new Error('该商品所有规格均已售罄')
        this.skuDetail = detail
        this.setData({ skuVisible: true, skuProduct: categoryProduct.card(detail.product),
          skuOptions: skus.map(format.sku), selectedSkuId: '', selectedPrice: '', addingId: '' })
        this.setTabHidden(true)
      } else await this.addCurrentItem(detail, null, current)
    } catch (error) { if (current()) await feedback.notice(error.message || '加购失败，请稍后重试', '未能加入购物车') }
    finally { if (sequence === this.addSequence) this.setData({ addingId: '' }) }
  },
  setTabHidden(hidden) { const tab = this.getTabBar && this.getTabBar(); if (tab) tab.setData({ hidden }) },
  closeSku() {
    if (this.data.confirming && !this._inactive) return
    this.skuDetail = null
    this.setData({ skuVisible: false, selectedSkuId: '', selectedPrice: '' }); this.setTabHidden(false)
  },
  stopTap() {},
  selectCartSku(event) {
    if (this.data.confirming) return
    const sku = this.data.skuOptions.find((item) => String(item.id) === String(event.currentTarget.dataset.id))
    if (sku && Number(sku.stock) > 0) this.setData({ selectedSkuId: String(sku.id), selectedPrice: sku.priceText })
  },
  async confirmSku() {
    if (this.data.confirming || !this.skuDetail) return
    if (!this.data.selectedSkuId) { await feedback.notice('请先选择商品规格'); return }
    const id = format.identifier(this.skuDetail.product.id), skuId = this.data.selectedSkuId
    const sequence = this.addSequence = (this.addSequence || 0) + 1
    const token = session.getToken()
    const current = () => !this._inactive && sequence === this.addSequence && token === session.getToken()
    this.setData({ confirming: true })
    try {
      const detail = await request({ url: `/shop/products/${id}` })
      if (!current()) return
      if (String(detail.product && detail.product.id) !== id) throw new Error('商品信息不一致，请重新选择')
      await this.addCurrentItem(detail, skuId, current)
      if (current()) { this.setData({ confirming: false }); this.closeSku() }
    } catch (error) { if (current()) await feedback.notice(error.message || '加购失败，请重新选择', '未能加入购物车') }
    finally { if (sequence === this.addSequence) this.setData({ confirming: false }) }
  },
  async addCurrentItem(detail, skuId, current) {
    const selection = await purchaseLimit.checkAddition(detail.product.id, skuId, 1, { detail, isCurrent: current })
    if (!selection || !current()) return
    cart.add(selection.item)
    await feedback.notice('已加入购物车，数量 +1', '操作完成')
  }
}
module.exports = { data, methods, show, hide }
