const request = require('../../utils/request')
const auth = require('../../utils/auth')

Page({
  data: {
    loading: true,
    rows: [],
    showForm: false,
    selectMode: false,
    form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: false },
    saving: false
  },
  onLoad(options) {
    this.selectMode = options.select === '1'
    this.setData({ selectMode: this.selectMode })
    if (auth.requireLogin('/pages/address/index')) this.load()
  },
  async load() {
    try {
      const rows = await request({ url: '/shop/addresses' }) || []
      this.setData({ rows, showForm: this.data.showForm || !rows.length })
    }
    catch (error) { wx.showToast({ title: error.message || '地址加载失败', icon: 'none' }) }
    finally { this.setData({ loading: false }) }
  },
  input(event) {
    this.setData({ [`form.${event.currentTarget.dataset.field}`]: event.detail.value })
  },
  region(event) {
    const region = event.detail.value || []
    this.setData({ 'form.region': region, 'form.regionText': region.join(' ') })
  },
  defaultChange(event) { this.setData({ 'form.isDefault': Boolean(event.detail.value) }) },
  startAdd() {
    this.setData({
      showForm: true,
      form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: !this.data.rows.length }
    })
  },
  edit(event) {
    const row = this.data.rows.find((item) => item.id === Number(event.currentTarget.dataset.id))
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
    if (!this.data.rows.length) return
    this.resetForm(false)
  },
  resetForm(showForm = false) {
    this.setData({
      showForm,
      form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: false }
    })
  },
  async save() {
    const form = this.data.form
    if (!form.receiverName.trim()) { wx.showToast({ title: '请输入收货人', icon: 'none' }); return }
    if (!/^1[3-9]\d{9}$/.test(form.receiverPhone.trim())) { wx.showToast({ title: '请输入正确手机号', icon: 'none' }); return }
    if (!form.region || form.region.length !== 3) { wx.showToast({ title: '请选择省市区', icon: 'none' }); return }
    if (!form.detailAddress.trim()) { wx.showToast({ title: '请输入详细地址', icon: 'none' }); return }
    this.setData({ saving: true })
    try {
      await request({ url: '/shop/addresses', method: 'POST', data: {
        id: form.id || undefined,
        receiverName: form.receiverName.trim(), receiverPhone: form.receiverPhone.trim(),
        province: form.region[0], city: form.region[1], district: form.region[2],
        detailAddress: form.detailAddress.trim(), isDefault: form.isDefault || !this.data.rows.length ? 1 : 0
      } })
      wx.showToast({ title: form.id ? '地址已更新' : '地址已保存', icon: 'success' })
      if (this.selectMode) setTimeout(() => wx.navigateBack(), 500)
      else { this.resetForm(false); await this.load() }
    } catch (error) { wx.showToast({ title: error.message || '保存失败', icon: 'none' }) }
    finally { this.setData({ saving: false }) }
  },
  async choose(event) {
    const row = this.data.rows.find((item) => item.id === Number(event.currentTarget.dataset.id))
    if (!row) return
    try {
      await request({ url: '/shop/addresses', method: 'POST', data: {
        id: row.id, receiverName: row.receiverName, receiverPhone: row.receiverPhone,
        province: row.province, city: row.city, district: row.district,
        detailAddress: row.detailAddress, isDefault: 1
      } })
      if (this.selectMode) wx.navigateBack()
      else await this.load()
    } catch (error) { wx.showToast({ title: error.message || '选择失败', icon: 'none' }) }
  },
  async remove(event) {
    const id = Number(event.currentTarget.dataset.id)
    wx.showModal({
      title: '删除收货地址',
      content: '删除后无法恢复，确定继续吗？',
      confirmText: '删除',
      confirmColor: '#e7193f',
      success: async ({ confirm }) => {
        if (!confirm) return
        try {
          await request({ url: `/shop/addresses/${id}`, method: 'DELETE' })
          if (this.data.form.id === id) this.resetForm(false)
          await this.load()
        } catch (error) { wx.showToast({ title: error.message || '删除失败', icon: 'none' }) }
      }
    })
  }
})
