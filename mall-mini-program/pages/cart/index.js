const feedback = require('../../utils/feedback')
const cart = require('../../utils/cart')
const format = require('../../utils/format')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const catalog = require('../../utils/catalog')
const purchaseLimit = require('../../utils/purchase-limit')
const session = require('../../utils/session')

Page({
  data: { ...theme.pageData(), rows: [], total: '0.00', count: 0, allSelected: false, checking: false, checkError: '', quantityChecking: '', checkoutChecking: false },
  onShow() {
    this.inactive = false; theme.apply(this); this.refresh()
    if (cart.needsLegacyReview()) {
      const token = session.getToken()
      feedback.notice('为区分不同账号，旧版购物车商品需要重新添加。已有订单不受影响。', '购物车已按账号分开保存')
        .then(({ confirm }) => { if (confirm && token === session.getToken()) cart.acknowledgeLegacyReview() })
    }
  },
  async refresh() {
    const generation = this.generation = (this.generation || 0) + 1
    const snapshot = JSON.stringify(cart.list())
    this.renderRows(cart.list())
    feedback.update(this, { checking: true, checkError: '' })
    try {
      const rows = await catalog.refresh(cart.list())
      if (generation !== this.generation || snapshot !== JSON.stringify(cart.list())) return
      for (const row of rows) cart.update(row.key, { salePrice: row.salePrice, productName: row.productName, skuName: row.skuName, coverUrl: row.coverUrl })
      this.renderRows(rows)
    } catch (error) { if (generation === this.generation) feedback.update(this, { checkError: error.message || '商品信息校验失败，请重试' }) }
    finally { if (generation === this.generation) feedback.update(this, { checking: false }) }
  },
  onHide() { this.inactive = true; this.generation = (this.generation || 0) + 1; this.actionSequence = (this.actionSequence || 0) + 1; this.setData({ quantityChecking: '', checkoutChecking: false }) },
  onUnload() { this.onHide() },
  renderRows(source) {
    const rows = source.map((row) => ({
      ...row,
      coverUrl: format.mediaUrl(row.coverUrl),
      priceText: format.money(row.salePrice),
      lineTotal: format.money(Number(row.salePrice) * row.quantity)
    }))
    const selected = rows.filter((row) => row.selected)
    feedback.update(this, {
      rows,
      count: selected.reduce((sum, row) => sum + row.quantity, 0),
      total: format.money(selected.reduce((sum, row) => sum + Number(row.salePrice) * row.quantity, 0)),
      allSelected: rows.length > 0 && selected.length === rows.length
    })
  },
  toggle(event) {
    if (this.data.quantityChecking || this.data.checkoutChecking) return
    cart.update(event.currentTarget.dataset.key, { selected: (event.detail.value || []).includes('selected') })
    this.refresh()
  },
  async quantity(event) {
    const key = event.currentTarget.dataset.key
    const row = this.data.rows.find((item) => item.key === key)
    const delta = Number(event.currentTarget.dataset.delta)
    if (!row || ![1, -1].includes(delta) || this.data.quantityChecking || this.data.checkoutChecking || this.inactive) return
    if (delta < 0) { cart.update(key, { quantity: Math.max(1, row.quantity - 1) }); return this.refresh() }
    const sequence = this.actionSequence = (this.actionSequence || 0) + 1
    const current = () => !this.inactive && sequence === this.actionSequence
    this.generation = (this.generation || 0) + 1
    this.setData({ quantityChecking: key })
    try {
      const selection = await purchaseLimit.checkAddition(row.productId, row.skuId, 1, { isCurrent: current })
      if (!selection || !current()) return
      cart.update(key, { ...selection.item, quantity: row.quantity + 1 })
    } catch (error) { if (current()) await feedback.notice(error.message || '当前商品已达到可购买数量上限', '无法增加数量') }
    finally { if (sequence === this.actionSequence) { this.setData({ quantityChecking: '' }); if (current()) await this.refresh() } }
  },
  toggleAll(event) { if (this.data.quantityChecking || this.data.checkoutChecking) return; cart.selectAll((event.detail.value || []).includes('selected')); this.refresh() },
  remove(event) {
    if (this.data.quantityChecking || this.data.checkoutChecking) return
    const key = event.currentTarget.dataset.key
    const token = session.getToken()
    wx.showModal({
      title: '移除商品',
      content: '确定把这件商品移出购物车吗？',
      confirmText: '移除',
      confirmColor: this.data.themeColor,
      success: ({ confirm }) => {
        if (!confirm || this.inactive || token !== session.getToken()) return
        cart.remove(key)
        this.refresh()
      }
    })
  },
  openProduct(event) { wx.navigateTo({ url: `/pages/product/index?id=${event.currentTarget.dataset.id}` }) },
  async checkout() {
    if (this.data.quantityChecking || this.data.checkoutChecking || this.inactive) return
    if (this.data.checking) { feedback.toast({ title: '正在核对最新价格与库存', icon: 'none' }); return }
    if (!this.data.count) { feedback.toast({ title: '请先选择商品', icon: 'none' }); return }
    if (!auth.requireLogin('/pages/cart/index')) return
    const token = session.getToken(), snapshot = JSON.stringify(cart.selected())
    const sequence = this.actionSequence = (this.actionSequence || 0) + 1
    const current = () => !this.inactive && sequence === this.actionSequence && token === session.getToken()
    this.setData({ checkoutChecking: true })
    try {
      const rows = await catalog.refresh(cart.selected(), { checkLimits: true })
      if (!current()) return
      if (snapshot !== JSON.stringify(cart.selected())) throw new Error('已选商品发生变化，请重新结算')
      const invalid = rows.find((row) => row.unavailable)
      if (invalid) throw new Error(invalid.unavailable)
      wx.navigateTo({ url: '/pages/checkout/index' })
    } catch (error) { if (current()) await feedback.notice(error.message || '结算校验失败，请重试', '暂时无法结算') }
    finally { if (sequence === this.actionSequence) this.setData({ checkoutChecking: false }) }
  },
  goShopping() { wx.switchTab({ url: '/pages/home/index' }) }
})
