const request = require('../../utils/request')
const auth = require('../../utils/auth')

Page({
  data: {
    loading: true,
    rows: [],
    form: { receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '' },
    saving: false
  },
  onLoad(options) {
    this.selectMode = options.select === '1'
    if (auth.requireLogin('/pages/address/index')) this.load()
  },
  async load() {
    try { this.setData({ rows: await request({ url: '/shop/addresses' }) || [] }) }
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
  async save() {
    const form = this.data.form
    if (!form.receiverName.trim()) { wx.showToast({ title: '请输入收货人', icon: 'none' }); return }
    if (!/^1[3-9]\d{9}$/.test(form.receiverPhone.trim())) { wx.showToast({ title: '请输入正确手机号', icon: 'none' }); return }
    if (!form.region || form.region.length !== 3) { wx.showToast({ title: '请选择省市区', icon: 'none' }); return }
    if (!form.detailAddress.trim()) { wx.showToast({ title: '请输入详细地址', icon: 'none' }); return }
    this.setData({ saving: true })
    try {
      await request({ url: '/shop/addresses', method: 'POST', data: {
        receiverName: form.receiverName.trim(), receiverPhone: form.receiverPhone.trim(),
        province: form.region[0], city: form.region[1], district: form.region[2],
        detailAddress: form.detailAddress.trim(), isDefault: 1
      } })
      wx.showToast({ title: '地址已保存', icon: 'success' })
      if (this.selectMode) setTimeout(() => wx.navigateBack(), 500)
      else { this.setData({ form: { receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '' } }); await this.load() }
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
    try { await request({ url: `/shop/addresses/${id}`, method: 'DELETE' }); await this.load() }
    catch (error) { wx.showToast({ title: error.message || '删除失败', icon: 'none' }) }
  }
})
