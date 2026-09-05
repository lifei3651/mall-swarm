const request = require('../../utils/request')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const format = require('../../utils/format')

Page({
  data: {
    ...theme.pageData(),
    loading: true,
    loadError: '',
    rows: [],
    showForm: false,
    selectMode: false,
    form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: false },
    saving: false
  },
  onLoad(options = {}) {
    theme.apply(this)
    this.selectMode = options.select === '1'
    this.setData({ selectMode: this.selectMode })
  },
  onShow() {
    theme.sync(this)
    if (this.data.saving || this.returning) return
    if (auth.requireLogin(`/pages/address/index${this.selectMode ? '?select=1' : ''}`)) return this.load()
    this.setData({ loading: false, rows: [] })
  },
  async load() {
    const generation = this.loadGeneration = (this.loadGeneration || 0) + 1
    this.setData({ loading: true, loadError: '' })
    try {
      const response = await request({ url: '/shop/addresses' }) || []
      const rows = response.filter((row) => format.identifier(row.id)).map((row) => ({ ...row, id: format.identifier(row.id), isDefault: Number(row.isDefault) }))
      if (generation !== this.loadGeneration) return
      this.setData({ rows, showForm: this.data.showForm || !rows.length })
    }
    catch (error) {
      if (generation !== this.loadGeneration) return
      this.setData({ loadError: error.message || '地址加载失败' })
      wx.showToast({ title: error.message || '地址加载失败', icon: 'none' })
    }
    finally { if (generation === this.loadGeneration) this.setData({ loading: false }) }
  },
  onUnload() { this.loadGeneration = (this.loadGeneration || 0) + 1 },
  input(event) {
    this.setData({ [`form.${event.currentTarget.dataset.field}`]: event.detail.value })
  },
  region(event) {
    const region = event.detail.value || []
    this.setData({ 'form.region': region, 'form.regionText': region.join(' ') })
  },
  defaultChange(event) { this.setData({ 'form.isDefault': Boolean(event.detail.value) }) },
  startAdd() {
    if (this.data.saving || this.data.loading) return
    this.setData({
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
    this.setData({ showForm: true, form: {
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
    this.setData({
      showForm,
      form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: false }
    })
  },
  async save() {
    if (this.data.saving || this.data.loading || this.data.loadError || this.returning) return
    const form = this.data.form
    if (form.id !== null && form.id !== undefined && !format.identifier(form.id)) { wx.showToast({ title: '地址信息无效，请重新选择', icon: 'none' }); return }
    if (!form.receiverName.trim()) { wx.showToast({ title: '请输入收货人', icon: 'none' }); return }
    if (!/^1[3-9]\d{9}$/.test(form.receiverPhone.trim())) { wx.showToast({ title: '请输入正确手机号', icon: 'none' }); return }
    if (!form.region || form.region.length !== 3) { wx.showToast({ title: '请选择省市区', icon: 'none' }); return }
    if (!form.detailAddress.trim()) { wx.showToast({ title: '请输入详细地址', icon: 'none' }); return }
    this.setData({ saving: true })
    try {
      const saved = await request({ url: '/shop/addresses', method: 'POST', data: {
        id: form.id ? format.identifier(form.id) : undefined,
        receiverName: form.receiverName.trim(), receiverPhone: form.receiverPhone.trim(),
        province: form.region[0], city: form.region[1], district: form.region[2],
        detailAddress: form.detailAddress.trim(), isDefault: form.isDefault || !this.data.rows.length ? 1 : 0
      } })
      wx.showToast({ title: form.id ? '地址已更新' : '地址已保存', icon: 'success' })
      if (this.selectMode) this.returnSelectedAddress(saved)
      else { this.resetForm(false); await this.load() }
    } catch (error) { wx.showToast({ title: error.message || '保存失败', icon: 'none' }) }
    finally { this.setData({ saving: false }) }
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
      fail: () => { this.returning = false; wx.showToast({ title: '请返回结算页重新选择地址', icon: 'none' }) } })
  },
  async choose(event) {
    if (this.data.saving || this.data.loading || this.returning || this.data.loadError) return
    const id = format.identifier(event.currentTarget.dataset.id)
    const row = id && this.data.rows.find((item) => format.identifier(item.id) === id)
    if (!row) return
    if (this.selectMode) { this.returnSelectedAddress(row); return }
    this.setData({ saving: true })
    try {
      await request({ url: '/shop/addresses', method: 'POST', data: {
        id: row.id, receiverName: row.receiverName, receiverPhone: row.receiverPhone,
        province: row.province, city: row.city, district: row.district,
        detailAddress: row.detailAddress, isDefault: 1
      } })
      await this.load()
    } catch (error) { wx.showToast({ title: error.message || '选择失败', icon: 'none' }) }
    finally { this.setData({ saving: false }) }
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
        this.setData({ saving: true })
        try {
          await request({ url: `/shop/addresses/${id}`, method: 'DELETE' })
          if (String(this.data.form.id) === String(id)) this.resetForm(false)
          await this.load()
        } catch (error) { wx.showToast({ title: error.message || '删除失败', icon: 'none' }) }
        finally { this.setData({ saving: false }) }
      }
    })
  }
})
