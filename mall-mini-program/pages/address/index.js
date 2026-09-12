const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const auth = require('../../utils/auth')
const theme = require('../../utils/theme')
const format = require('../../utils/format')
const wechatAddress = require('../../utils/wechat-address')
const session = require('../../utils/session')
const addressParser = require('../../utils/address-parser')

Page({
  data: {
    ...theme.pageData(),
    loading: true,
    loadError: '',
    rows: [],
    showForm: false,
    selectMode: false,
    form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: false },
    saving: false, importing: false, importMessage: '', pastedAddress: ''
  },
  onLoad(options = {}) {
    theme.apply(this)
    this.selectMode = options.select === '1'
    feedback.update(this, { selectMode: this.selectMode })
  },
  onShow() {
    theme.apply(this)
    const token = session.getToken()
    if (this.owner !== undefined && this.owner !== token) {
      this.loadGeneration = (this.loadGeneration || 0) + 1
      this.setData({ rows: [], saving: false, importing: false, pastedAddress: '', importMessage: '' })
      this.resetForm(false); this.returning = false
    }
    this.owner = token
    if (this.data.saving || this.data.importing || this.returning) return
    if (auth.requireLogin(`/pages/address/index${this.selectMode ? '?select=1' : ''}`)) return this.load()
    feedback.update(this, { loading: false, rows: [] })
  },
  async load(options = {}) {
    const generation = this.loadGeneration = (this.loadGeneration || 0) + 1
    const token = session.getToken()
    feedback.update(this, { loading: true, loadError: '' })
    try {
      const response = await request({ url: '/shop/addresses' }) || []
      const rows = response.filter((row) => format.identifier(row.id)).map((row) => ({ ...row, id: format.identifier(row.id), isDefault: Number(row.isDefault) }))
      if (generation !== this.loadGeneration || token !== session.getToken() || this.disposed) return
      feedback.update(this, { rows, showForm: this.data.showForm || !rows.length })
    }
    catch (error) {
      if (generation !== this.loadGeneration || this.disposed || token !== session.getToken()) return
      const message = options.savedImport ? '微信地址已保存，但列表刷新失败。请重新加载地址列表，不要重复导入。' : error.message || '地址加载失败'
      feedback.update(this, { loadError: message })
    }
    finally { if (generation === this.loadGeneration && !this.disposed && token === session.getToken()) feedback.update(this, { loading: false }) }
  },
  onUnload() { this.disposed = true; this.loadGeneration = (this.loadGeneration || 0) + 1 },
  async importWechatAddress() {
    if (this.data.saving || this.data.loading || this.data.loadError || this.data.importing || this.returning) return
    if (!auth.requireLogin(`/pages/address/index${this.selectMode ? '?select=1' : ''}`)) return
    const token = session.getToken()
    const snapshot = JSON.stringify(this.data.form)
    feedback.update(this, { importing: true, importMessage: '' })
    try {
      if (this.data.showForm && (this.data.form.receiverName || this.data.form.detailAddress)) {
        const confirmed = await new Promise((resolve) => wx.showModal({ title: '导入为新地址', content: '选择微信地址后会自动保存为新地址，当前未保存的编辑将被替换。已保存的地址和默认设置不会改变。是否继续？', success: (r) => resolve(r.confirm), fail: () => resolve(false) }))
        if (!confirmed) return
      }
      if (this.disposed || token !== session.getToken()) return
      const form = await wechatAddress.choose()
      if (this.disposed || token !== session.getToken() || snapshot !== JSON.stringify(this.data.form)) return
      this.setData({ form, showForm: false, pastedAddress: '' })
      await this.save({ fromWechat: true })
    } catch (error) {
      if (!this.disposed && token === session.getToken()) {
        this.setData({ importMessage: error.message })
        if (!/已取消导入/.test(error.message || '')) await feedback.notice(error.message || '微信地址导入失败，请重试')
      }
    }
    finally { if (!this.disposed && token === session.getToken()) feedback.update(this, { importing: false }) }
  },
  input(event) {
    const field = event.currentTarget.dataset.field
    if (['receiverName', 'receiverPhone', 'detailAddress'].includes(field) && !this.data.importing && !this.data.saving) feedback.update(this, { [`form.${field}`]: event.detail.value })
  },
  pasteInput(event) { if (!this.data.saving && !this.data.importing) this.setData({ pastedAddress: String(event.detail.value || '').slice(0,1000) }) },
  async recognizeAddress() {
    if (this.data.saving || this.data.importing) return
    if (!this.data.pastedAddress.trim()) { await feedback.notice('请先在输入框粘贴收货信息'); return }
    const parsed = addressParser.parseChineseAddress(this.data.pastedAddress)
    const form = { ...this.data.form }
    for (const key of ['receiverName', 'receiverPhone', 'detailAddress']) if (parsed[key]) form[key] = parsed[key]
    if (parsed.province && parsed.city && parsed.district) { form.region = [parsed.province, parsed.city, parsed.district]; form.regionText = form.region.join(' ') }
    this.setData({ form })
    await feedback.notice('已识别并回填，请核对姓名、电话、省市区和详细地址后保存。未识别完整的字段请手动补充。', '请核对收货信息')
  },
  region(event) {
    if (this.data.saving || this.data.importing) return
    const region = event.detail.value || []
    feedback.update(this, { 'form.region': region, 'form.regionText': region.join(' ') })
  },
  defaultChange(event) { if (!this.data.saving && !this.data.importing) feedback.update(this, { 'form.isDefault': Boolean(event.detail.value) }) },
  startAdd() {
    if (this.data.saving || this.data.loading || this.data.importing) return
    feedback.update(this, {
      showForm: true,
      form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: !this.data.rows.length }
    })
  },
  edit(event) {
    if (this.data.saving || this.data.importing) return
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
    if (this.data.saving || this.data.importing || !this.data.rows.length) return
    this.resetForm(false)
  },
  resetForm(showForm = false) {
    feedback.update(this, {
      showForm,
      form: { id: null, receiverName: '', receiverPhone: '', region: [], regionText: '', detailAddress: '', isDefault: false }
    })
  },
  async save(options = {}) {
    const fromWechat = options.fromWechat === true
    if (this.data.saving || this.data.loading || this.data.loadError || this.returning || (this.data.importing && !fromWechat)) return
    const form = this.data.form
    const invalid = message => {
      if (fromWechat) this.setData({ showForm: true })
      return feedback.toast({ title: fromWechat ? `微信地址未保存：${message}，请补充后保存` : message, icon: 'none' })
    }
    if (form.id !== null && form.id !== undefined && !format.identifier(form.id)) { await invalid('地址信息无效，请重新选择'); return }
    if (!form.receiverName.trim()) { await invalid('请输入收货人'); return }
    if (!/^1[3-9]\d{9}$/.test(form.receiverPhone.trim())) { await invalid('请输入正确手机号'); return }
    if (!form.region || form.region.length !== 3 || form.region.some(value => !String(value || '').trim())) { await invalid('请选择完整省市区'); return }
    if (!form.detailAddress.trim()) { await invalid('请输入详细地址'); return }
    const token = session.getToken()
    feedback.update(this, { saving: true })
    try {
      const saved = await request({ url: '/shop/addresses', method: 'POST', data: {
        id: form.id ? format.identifier(form.id) : undefined,
        receiverName: form.receiverName.trim(), receiverPhone: form.receiverPhone.trim(),
        province: form.region[0], city: form.region[1], district: form.region[2],
        detailAddress: form.detailAddress.trim(), isDefault: form.isDefault || !this.data.rows.length ? 1 : 0
      } })
      if (this.disposed || token !== session.getToken()) return
      if (fromWechat) {
        if (!saved || !format.identifier(saved.id)) throw new Error('地址保存结果未确认，请先返回地址列表核对，勿重复导入')
        if (this.selectMode) this.returnSelectedAddress(saved)
        else {
          this.resetForm(false)
          await this.load({ savedImport: true })
          if (this.disposed || token !== session.getToken() || this.data.loadError) return
        }
        return
      }
      if (this.selectMode) this.returnSelectedAddress(saved)
      else { this.resetForm(false); await this.load() }
    } catch (error) {
      if (!this.disposed && token === session.getToken()) {
        if (fromWechat) this.setData({ showForm: true })
        await feedback.toast({ title: error.message || (fromWechat ? '微信地址保存失败，请稍后重试' : '保存失败'), icon: 'none' })
      }
    }
    finally { if (!this.disposed && (token === session.getToken() || !session.getToken())) feedback.update(this, { saving: false }) }
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
    if (this.data.saving || this.data.loading || this.data.importing || this.returning || this.data.loadError) return
    const id = format.identifier(event.currentTarget.dataset.id)
    const row = id && this.data.rows.find((item) => format.identifier(item.id) === id)
    if (!row) return
    if (this.selectMode) { this.returnSelectedAddress(row); return }
  },
  async makeDefault(event) {
    if (this.data.saving || this.data.loading || this.data.importing || this.returning || this.data.loadError) return
    const id = format.identifier(event.currentTarget.dataset.id)
    const row = id && this.data.rows.find(item => format.identifier(item.id) === id)
    if (!row || Number(row.isDefault) === 1) return
    const token = session.getToken()
    feedback.update(this, { saving: true })
    try {
      await request({ url: '/shop/addresses', method: 'POST', data: {
        id: row.id, receiverName: row.receiverName, receiverPhone: row.receiverPhone,
        province: row.province, city: row.city, district: row.district,
        detailAddress: row.detailAddress, isDefault: 1
      } })
      if (this.disposed || token !== session.getToken()) return
      await this.load()
    } catch (error) { if (!this.disposed && token === session.getToken()) feedback.toast({ title: error.message || '设置默认地址失败', icon: 'none' }) }
    finally { if (!this.disposed && (token === session.getToken() || !session.getToken())) feedback.update(this, { saving: false }) }
  },
  async remove(event) {
    if (this.data.saving || this.data.loading || this.data.importing || this.returning) return
    const candidateId = format.identifier(event.currentTarget.dataset.id)
    const row = candidateId && this.data.rows.find((item) => format.identifier(item.id) === candidateId)
    if (!row) return
    const id = row.id
    const token = session.getToken()
    wx.showModal({
      title: '删除收货地址',
      content: '删除后无法恢复，确定继续吗？',
      confirmText: '删除',
      confirmColor: this.data.themeColor,
      success: async ({ confirm }) => {
        if (!confirm || this.data.saving || this.data.importing || this.disposed || token !== session.getToken()) return
        feedback.update(this, { saving: true })
        try {
          await request({ url: `/shop/addresses/${id}`, method: 'DELETE' })
          if (this.disposed || token !== session.getToken()) return
          if (String(this.data.form.id) === String(id)) this.resetForm(false)
          await this.load()
        } catch (error) { if (!this.disposed && token === session.getToken()) feedback.toast({ title: error.message || '删除失败', icon: 'none' }) }
        finally { if (!this.disposed && (token === session.getToken() || !session.getToken())) feedback.update(this, { saving: false }) }
      }
    })
  }
})
