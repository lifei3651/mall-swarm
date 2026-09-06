const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const session = require('../../utils/session')
const runtime = require('../../config/runtime')
const format = require('../../utils/format')
const theme = require('../../utils/theme')
const { identifier, remainingItems, afterSaleEligibility } = require('../order-detail/policy')

Page({
  data: { ...theme.pageData(), loading: true, error: '', submitError: '', submitting: false,
    orderId: '', orderNo: '', items: [], allowed: false, unavailableReason: '', canExchange: false,
    applyType: 1, reason: '', proofs: [], selectingProof: false, submitted: false },
  onLoad(options = {}) {
    theme.apply(this)
    const orderId = identifier(options.orderId)
    feedback.update(this, { orderId })
    if (!orderId) feedback.update(this, { loading: false, error: '订单编号不正确' })
  },
  onShow() {
    theme.apply(this)
    if (!this.data.orderId) return
    if (!auth.requireLogin(`/pages/after-sale/index?orderId=${this.data.orderId}`)) return
    // Choosing an image also triggers onShow; do not wipe the user's draft.
    if (!this.initialized && !this.loadingRequest) return this.load()
  },
  onUnload() { this.disposed = true },
  async load() {
    if (!this.data.orderId || this.loadingRequest) return
    this.loadingRequest = true
    feedback.update(this, { loading: true, error: '' })
    try {
      const detail = await request({ url: `/shop/orders/${this.data.orderId}` })
      if (this.disposed) return
      const eligibility = afterSaleEligibility(detail)
      this.initialized = true
      feedback.update(this, { orderNo: detail.order.orderNo || '', allowed: eligibility.allowed,
        unavailableReason: eligibility.reason, canExchange: eligibility.canExchange,
        items: remainingItems(detail).map((item) => ({ ...item, selectedQuantity: item.remaining,
          productCover: format.mediaUrl(item.productCover) })) })
    } catch (error) { if (!this.disposed) feedback.update(this, { error: error.message || '售后信息加载失败' }) }
    finally { this.loadingRequest = false; if (!this.disposed) feedback.update(this, { loading: false }) }
  },
  selectType(event) {
    const applyType = Number(event.currentTarget.dataset.type)
    if (this.data.submitting || ![1, 2, 3].includes(applyType) || (applyType === 3 && !this.data.canExchange)) return
    feedback.update(this, { applyType, submitError: '' })
  },
  changeQuantity(event) {
    if (this.data.submitting) return
    const id = identifier(event.currentTarget.dataset.id)
    const delta = Number(event.currentTarget.dataset.delta)
    if (![1, -1].includes(delta)) return
    feedback.update(this, { items: this.data.items.map((item) => item.id === id
      ? { ...item, selectedQuantity: Math.max(0, Math.min(item.remaining, item.selectedQuantity + delta)) } : item), submitError: '' })
  },
  reasonInput(event) { if (!this.data.submitting) feedback.update(this, { reason: event.detail.value, submitError: '' }) },
  chooseProof() {
    if (this.data.submitting || this.data.selectingProof || this.data.proofs.length >= 6) return
    if (!wx.chooseMedia) { feedback.update(this, { submitError: '当前微信不支持选图，请升级微信；也可以不上传凭证直接申请' }); return }
    feedback.update(this, { selectingProof: true, submitError: '' })
    wx.chooseMedia({ count: 6 - this.data.proofs.length, mediaType: ['image'], sourceType: ['album', 'camera'], sizeType: ['compressed'],
      success: ({ tempFiles }) => {
        if (this.disposed) return
        const files = (tempFiles || []).slice(0, 6 - this.data.proofs.length)
        const selectedPaths = new Set(this.data.proofs.map((proof) => proof.path))
        const valid = files.filter((file) => {
          if (!file.tempFilePath || Number(file.size) <= 0 || !Number.isFinite(Number(file.size)) || Number(file.size) > 5 * 1024 * 1024 || selectedPaths.has(file.tempFilePath)) return false
          selectedPaths.add(file.tempFilePath)
          return true
        })
        feedback.update(this, { proofs: this.data.proofs.concat(valid.map((file) => ({ path: file.tempFilePath, filename: '' }))),
          submitError: valid.length !== files.length ? '单张图片不能超过5MB，重复、过大或无效图片未添加' : '' })
      },
      fail: (error) => { if (!this.disposed && !/cancel/i.test(error.errMsg || '')) feedback.update(this, { submitError: '未能选取图片，请检查微信权限；也可以不上传凭证直接申请' }) },
      complete: () => { if (!this.disposed) feedback.update(this, { selectingProof: false }) }
    })
  },
  removeProof(event) {
    if (this.data.submitting) return
    const index = Number(event.currentTarget.dataset.index)
    if (Number.isInteger(index) && index >= 0 && index < this.data.proofs.length) feedback.update(this, { proofs: this.data.proofs.filter((_, position) => position !== index) })
  },
  uploadProof(proof) {
    const token = session.getToken()
    if (!token) return Promise.reject(new Error('登录已失效，请重新登录'))
    return new Promise((resolve, reject) => {
      wx.uploadFile({ url: `${runtime.API_BASE_URL}/shop/media/after-sale-proofs?orderId=${encodeURIComponent(this.data.orderId)}`,
        filePath: proof.path, name: 'file', timeout: 30000,
        header: { Authorization: `Bearer ${token}`, 'X-Shop-Client': 'wechat-mini-program', 'X-Shop-Surface': 'mini-program' },
        success: (response) => {
          if (response.statusCode === 401) session.clearSession()
          let result
          try { result = JSON.parse(response.data) } catch (_) { reject(new Error('凭证上传返回异常，请重试')); return }
          if (response.statusCode < 200 || response.statusCode >= 300 || result.code !== 200 || typeof result.data !== 'string' || !result.data) {
            reject(new Error(result.message || '凭证上传失败')); return
          }
          resolve(result.data)
        },
        fail: () => reject(new Error('凭证上传失败，请检查网络后重试'))
      })
    })
  },
  async submit() {
    if (this.data.submitting || this.data.submitted || this.data.selectingProof || !this.data.allowed) return
    const items = this.data.items.filter((item) => item.id && item.selectedQuantity > 0)
      .map((item) => ({ orderItemId: item.id, quantity: item.selectedQuantity }))
    const reason = this.data.reason.trim()
    if (!items.length) { feedback.update(this, { submitError: '请至少选择1件需要售后的商品' }); return }
    if (!reason || reason.length > 170) { feedback.update(this, { submitError: '请填写1至170字的申请原因' }); return }
    if (this.data.applyType === 3 && !this.data.canExchange) return
    feedback.update(this, { submitting: true, submitError: '' })
    try {
      for (let index = 0; index < this.data.proofs.length; index++) {
        const proof = this.data.proofs[index]
        if (!proof.filename) {
          const filename = await this.uploadProof(proof)
          feedback.update(this, { proofs: this.data.proofs.map((item, position) => position === index ? { ...item, filename } : item) })
        }
      }
      const filenames = this.data.proofs.map((proof) => proof.filename)
      await request({ url: '/shop/after-sales', method: 'POST', data: {
        orderId: this.data.orderId, applyType: this.data.applyType, reason, items,
        proofImages: filenames.length ? JSON.stringify(filenames) : null
      } })
      feedback.update(this, { submitted: true })
      await feedback.toast({ title: '售后申请已提交', icon: 'success' })
      this.openOrder()
    } catch (error) { feedback.update(this, { submitError: error.message || '申请未确认成功，请查看订单售后进度后再重试' }) }
    finally { feedback.update(this, { submitting: false }) }
  },
  openOrder() { wx.redirectTo({ url: `/pages/order-detail/index?id=${this.data.orderId}` }) }
})
