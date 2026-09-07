const feedback = require('../../utils/feedback')
const request = require('../../utils/request')
const theme = require('../../utils/theme')
const format = require('../../utils/format')
const types = [{ id: '', label: '全部' }, { id: '1', label: '系统公告' }, { id: '2', label: '活动公告' }, { id: '3', label: '物流公告' }]
function decorate(row) { return { ...row, typeLabel: (types.find(type => type.id === String(row.noticeType)) || types[1]).label, dateText: String(row.createTime || '').slice(0,10).replace(/-/g,'/') } }
Page({
  data: { ...theme.pageData(), loading: true, error: '', rows: [], filteredRows: [], notice: null, types, filterType: '', detail: false },
  onLoad(options = {}) { theme.apply(this); this.id = options.id ? format.identifier(options.id) : ''; this.invalidId = Boolean(options.id && !this.id); this.setData({ detail: Boolean(options.id) }); this.load() },
  onShow() { this.inactive = false; if (this.reloadNeeded) { this.reloadNeeded = false; this.load() } },
  onHide() { this.inactive = true; this.generation = (this.generation || 0) + 1; this.reloadNeeded = true },
  onUnload() { this.onHide() },
  async load() {
    if (this.invalidId) { feedback.update(this, { loading: false, error: '公告编号不正确' }); return }
    const generation = this.generation = (this.generation || 0) + 1
    const current = () => !this.inactive && generation === this.generation
    feedback.update(this, { loading: true, error: '' })
    try {
      const result = await request({ url: this.id ? `/shop/notices/${this.id}` : '/shop/notices', ...(this.id ? {} : { params: { status: 1, pageSize: 50 } }) })
      if (!current()) return
      if (this.id) {
        const notice = result && (result.notice || result)
        if (!notice || format.identifier(notice.id) !== this.id) throw new Error('公告不存在或已下线')
        this.setData({ notice: decorate(notice) })
      } else {
        const rows = result && (result.list || result)
        if (!Array.isArray(rows)) throw new Error('公告数据暂不可用，请重试')
        this.setData({ rows: rows.map(decorate) }); this.applyFilter()
      }
    } catch (error) { if (current()) feedback.update(this, { error: error.message || '公告加载失败' }) }
    finally { if (current()) this.setData({ loading: false }) }
  },
  filter(event) { const value = String(event.currentTarget.dataset.type || ''); if (!types.some(type => type.id === value)) return; this.setData({ filterType: value }); this.applyFilter() },
  applyFilter() { this.setData({ filteredRows: this.data.rows.filter(row => !this.data.filterType || String(row.noticeType) === this.data.filterType) }) },
  backToList() { wx.redirectTo({ url: '/pages/notices/index' }) },
  open(event) { const id = format.identifier(event.currentTarget.dataset.id); if (id) wx.navigateTo({ url: `/pages/notices/index?id=${id}` }) }
})
