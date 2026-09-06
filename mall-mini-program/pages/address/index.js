const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const format = require('../../utils/format')
const wechatAddress = require('../../utils/wechat-address')
const session = require('../../utils/session')

Page({
  data: {
    ...theme.pageData(),
    loading: true,
    loadError: '',
    rows: [],
    showForm: false,
    selectMode: false,
    form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: false },
    saving: false, importing: false, importMessage: ''
  },
  onLoad(options = {}) {
    theme.apply(this)
    this.selectMode = options.select === '1'
    feedback.update(this, { selectMode: this.selectMode })
  },
  onShow() {
    theme.apply(this)
    if (this.data.saving || this.data.importing || this.returning) return
    if (auth.requireLogin(`/pages/address/index${this.selectMode ? '?select=1' : ''}`)) return this.load()
    feedback.update(this, { loading: false, rows: [] })
  },
  async load() {
    const generation = this.loadGeneration = (this.loadGeneration || 0) + 1
    feedback.update(this, { loading: true, loadError: '' })
    try {
      const response = await request({ url: '/shop/addresses' }) || []
      const rows = response.filter((row) => format.identifier(row.id)).map((row) => ({ ...row, id: format.identifier(row.id), isDefault: Number(row.isDefault) }))
      if (generation !== this.loadGeneration) return
      feedback.update(this, { rows, showForm: this.data.showForm || !rows.length })
    }
    catch (error) {
      if (generation !== this.loadGeneration) return
      feedback.update(this, { loadError: error.message || '地址加载失败' })
      feedback.toast({ title: error.message || '地址加载失败', icon: 'none' })
    }
    finally { if (generation === this.loadGeneration) feedback.update(this, { loading: false }) }
  },
  onUnload() { this.disposed = true; this.loadGeneration = (this.loadGeneration || 0) + 1 },
  async importWechatAddress() {
    if (this.data.saving || this.data.loading || this.data.importing || this.returning) return
    if (!auth.requireLogin('/pages/address/index')) return
    const token = session.getToken()
    const snapshot = JSON.stringify(this.data.form)
    feedback.update(this, { importing: true, importMessage: '' })
    try {
      if (this.data.showForm && (this.data.form.receiverName || this.data.form.detailAddress)) {
        const confirmed = await new Promise((resolve) => wx.showModal({ title: '导入为新地址', content: '当前未保存的编辑将被替换，已保存的地址和默认设置不会改变。是否继续？', success: (r) => resolve(r.confirm), fail: () => resolve(false) }))
        if (!confirmed) return
      }
      const form = await wechatAddress.choose()
      if (this.disposed || token !== session.getToken() || snapshot !== JSON.stringify(this.data.form)) return
      feedback.update(this, { form, showForm: true, importMessage: '已回填微信地址，请核对后保存。尚未提交或修改默认地址。' })
    } catch (error) { if (!this.disposed && token === session.getToken()) feedback.update(this, { importMessage: error.message }) }
    finally { if (!this.disposed) feedback.update(this, { importing: false }) }
  },
  input(event) {
    const field = event.currentTarget.dataset.field
    if (['receiverName', 'receiverPhone', 'detailAddress'].includes(field) && !this.data.importing) feedback.update(this, { [`form.${field}`]: event.detail.value })
  },
  region(event) {
    const region = event.detail.value || []
    feedback.update(this, { 'form.region': region, 'form.regionText': region.join(' ') })
  },
  defaultChange(event) { feedback.update(this, { 'form.isDefault': Boolean(event.detail.value) }) },
  startAdd() {
    if (this.data.saving || this.data.loading) return
    feedback.update(this, {
      showForm: true,
      form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: !this.data.rows.length }
    })
  },
  edit(event) {
    if (this.data.saving) return
    const id = format.identifier(event.currentTarget.dataset.id)
    const row = id && this.data.rows.find((item) => format.identifier(item.id) === id)
    if (!row) return
    const region = [row.province, row.city, row.district].filter(Boolean)
    feedback.update(this, { showForm: true, form: {
      id: row.id,
      receiverName: row.receiverName || '',
      receiverPhone: row.receiverPhone || '',
      region,
      regionText: region.join(' '),
      detailAddress: row.detailAddress || '',
      isDefault: Number(row.isDefault) === 1
    } })
  },
  cancelEdit() {
    if (this.data.saving || !this.data.rows.length) return
    this.resetForm(false)
  },
  resetForm(showForm = false) {
    feedback.update(this, {
      showForm,
      form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: false }
    })
  },
  async save() {
    if (this.data.saving || this.data.loading || this.data.loadError || this.returning) return
    const form = this.data.form
    if (form.id !== null && form.id !== undefined && !format.identifier(form.id)) { feedback.toast({ title: '地址信息无效，请重新选择', icon: 'none' }); return }
    if (!form.receiverName.trim()) { feedback.toast({ title: '请输入收货人', icon: 'none' }); return }
    if (!/^1[3-9]\d{9}$/.test(form.receiverPhone.trim())) { feedback.toast({ title: '请输入正确手机号', icon: 'none' }); return }
    if (!form.region || form.region.length !== 3) { feedback.toast({ title: '请选择省市区', icon: 'none' }); return }
    if (!form.detailAddress.trim()) { feedback.toast({ title: '请输入详细地址', icon: 'none' }); return }
    feedback.update(this, { saving: true })
    try {
      const saved = await request({ url: '/shop/addresses', method: 'POST', data: {
        id: form.id ? format.identifier(form.id) : undefined,
        receiverName: form.receiverName.trim(), receiverPhone: form.receiverPhone.trim(),
        province: form.region[0], city: form.region[1], district: form.region[2],
        detailAddress: form.detailAddress.trim(), isDefault: form.isDefault || !this.data.rows.length ? 1 : 0
      } })
      await feedback.toast({ title: form.id ? '地址已更新' : '地址已保存', icon: 'success' })
      if (this.selectMode) this.returnSelectedAddress(saved)
      else { this.resetForm(false); await this.load() }
    } catch (error) { feedback.toast({ title: error.message || '保存失败', icon: 'none' }) }
    finally { feedback.update(this, { saving: false }) }
  },
  returnSelectedAddress(address) {
    const id = address && format.identifier(address.id)
    if (!id) throw new Error('地址已保存，请返回地址列表重新选择')
    this.returning = true
    const channel = this.getOpenerEventChannel && this.getOpenerEventChannel()
    if (channel && channel.emit) channel.emit('addressSelected', { id })
    // 登录回跳会重建地址页，此时事件通道可能不存在；仍只回传 ID，让结算页重新读取本人地址。
    const pages = getCurrentPages()
    let checkoutIndex = -1
    for (let index = pages.length - 2; index >= 0; index--) {
      if (pages[index].route === 'pages/checkout/index' && typeof pages[index].acceptSelectedAddress === 'function') {
        checkoutIndex = index
        pages[index].acceptSelectedAddress({ id })
        break
      }
    }
    wx.navigateBack({ delta: checkoutIndex >= 0 ? pages.length - 1 - checkoutIndex : 1,
      fail: () => { this.returning = false; feedback.toast({ title: '请返回结算页重新选择地址', icon: 'none' }) } })
  },
  async choose(event) {
    if (this.data.saving || this.data.loading || this.returning || this.data.loadError) return
    const id = format.identifier(event.currentTarget.dataset.id)
    const row = id && this.data.rows.find((item) => format.identifier(item.id) === id)
    if (!row) return
    if (this.selectMode) { this.returnSelectedAddress(row); return }
    feedback.update(this, { saving: true })
    try {
      await request({ url: '/shop/addresses', method: 'POST', data: {
        id: row.id, receiverName: row.receiverName, receiverPhone: row.receiverPhone,
        province: row.province, city: row.city, district: row.district,
        detailAddress: row.detailAddress, isDefault: 1
      } })
      await this.load()
    } catch (error) { feedback.toast({ title: error.message || '选择失败', icon: 'none' }) }
    finally { feedback.update(this, { saving: false }) }
  },
  async remove(event) {
    if (this.data.saving || this.data.loading || this.returning) return
    const candidateId = format.identifier(event.currentTarget.dataset.id)
    const row = candidateId && this.data.rows.find((item) => format.identifier(item.id) === candidateId)
    if (!row) return
    const id = row.id
    wx.showModal({
      title: '删除收货地址',
      content: '删除后无法恢复，确定继续吗？',
      confirmText: '删除',
      confirmColor: this.data.themeColor,
      success: async ({ confirm }) => {
        if (!confirm || this.data.saving) return
        feedback.update(this, { saving: true })
        try {
          await request({ url: `/shop/addresses/${id}`, method: 'DELETE' })
          if (String(this.data.form.id) === String(id)) this.resetForm(false)
          await this.load()
        } catch (error) { feedback.toast({ title: error.message || '删除失败', icon: 'none' }) }
        finally { feedback.update(this, { saving: false }) }
      }
    })
  }
})
