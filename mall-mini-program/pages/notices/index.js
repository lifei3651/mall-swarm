const request = require('../../utils/request')
const theme = require('../../utils/theme')
const format = require('../../utils/format')
Page({
  data: { ...theme.pageData(), loading: true, error: '', rows: [], notice: null },
  onLoad(options = {}) { theme.apply(this); this.id = options.id ? format.identifier(options.id) : ''; this.invalidId = Boolean(options.id && !this.id); this.load() },
  async load() {
    if (this.invalidId) { this.setData({ loading: false, error: '公告编号不正确' }); return }
    this.setData({ loading: true, error: '' })
    try {
      const result = await request({ url: this.id ? `/shop/notices/${this.id}` : '/shop/notices' })
      this.setData(this.id ? { notice: result } : { rows: result || [] })
    } catch (error) { this.setData({ error: error.message || '公告加载失败' }) }
    finally { this.setData({ loading: false }) }
  },
  open(event) { const id = format.identifier(event.currentTarget.dataset.id); if (id) wx.navigateTo({ url: `/pages/notices/index?id=${id}` }) }
})
