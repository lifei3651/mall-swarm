const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const theme = require('../../utils/theme')
const format = require('../../utils/format')
Page({
  data: { ...theme.pageData(), loading: true, error: '', rows: [], notice: null },
  onLoad(options = {}) { theme.apply(this); this.id = options.id ? format.identifier(options.id) : ''; this.invalidId = Boolean(options.id && !this.id); this.load() },
  async load() {
    if (this.invalidId) { feedback.update(this, { loading: false, error: '公告编号不正确' }); return }
    feedback.update(this, { loading: true, error: '' })
    try {
      const result = await request({ url: this.id ? `/shop/notices/${this.id}` : '/shop/notices' })
      feedback.update(this, this.id ? { notice: result } : { rows: result || [] })
    } catch (error) { feedback.update(this, { error: error.message || '公告加载失败' }) }
    finally { feedback.update(this, { loading: false }) }
  },
  open(event) { const id = format.identifier(event.currentTarget.dataset.id); if (id) wx.navigateTo({ url: `/pages/notices/index?id=${id}` }) }
})
