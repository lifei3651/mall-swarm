const request = require('../../utils/request')
const auth = require('../../utils/auth')
const session = require('../../utils/session')
const theme = require('../../utils/theme')
const feedback = require('../../utils/feedback')
const format = require('../../utils/format')
const { identifier } = require('../order-detail/policy')
const stateLabel = state => ({ DRAFT: '草稿', UPCOMING: '待开播', CONNECTING: '视频连接中', LIVE: '直播中', ENDED: '已结束', DISABLED: '平台停用' })[state] || '状态待更新'
Page({
  data: { ...theme.pageData(), loading: true, error: '', studio: null, rooms: [], busyId: '', credential: null },
  onLoad() { theme.apply(this) },
  onShow() { this.hidden = false; theme.apply(this); if (auth.requireLogin('/pages/live-studio/index')) return this.load() },
  onHide() { this.hidden = true; this.version = (this.version || 0) + 1; this.setData({ credential: null, busyId: '', studio: null, rooms: [] }) },
  onUnload() { this.onHide(); this.disposed = true },
  current() { const token = session.getToken(), version = this.version; return () => !!token && token === session.getToken() && version === this.version && !this.hidden && !this.disposed },
  async load(keepCredential = false) {
    if (this.hidden || this.data.busyId) return
    this.version = (this.version || 0) + 1; const current = this.current()
    this.setData({ loading: true, error: '', ...(keepCredential !== true ? { credential: null } : {}) })
    try {
      const studio = await request({ url: '/shop/live-studio/me' })
      if (!current()) return
      if (!studio?.anchor?.anchor || !Array.isArray(studio.rooms)) throw new Error('当前账号尚未开通直播权限')
      this.setData({ studio, rooms: studio.rooms.filter(item => identifier(item.room?.id)).map(item => ({ ...item, key: identifier(item.room.id),
        room: { ...item.room, id: identifier(item.room.id), coverUrl: format.mediaUrl(item.room.coverUrl), status: Number(item.room.status) },
        stateLabel: stateLabel(item.roomState), scheduledText: String(item.room.scheduledStartTime || '时间待定').replace('T', ' ').slice(0, 16) })) })
    } catch (error) { if (current()) feedback.update(this, { studio: null, rooms: [], credential: null, error: error.message || '主播权限核对失败，请重试' }) }
    finally { if (current()) this.setData({ loading: false }) }
  },
  async control(event) {
    const id = identifier(event.currentTarget.dataset.id), action = event.currentTarget.dataset.action
    const current = this.current(), item = this.data.rooms.find(row => row.room.id === id)
    if (!current() || this.data.loading || this.data.busyId || !item || !['start', 'stop'].includes(action)) return
    if (action === 'start' && (!this.data.studio?.canStart || item.room.status === 2 || item.roomState === 'DISABLED')) return
    if (action === 'stop' && item.room.status !== 2) return
    this.setData({ busyId: id }); let confirmed = false
    try {
      const result = await new Promise(resolve => wx.showModal({ title: action === 'start' ? '确认开始直播' : '确认结束直播',
        content: action === 'start' ? '开启后需使用外部直播工具连接短时推流地址。本页面只管理直播间，不会自动打开手机摄像头。' : `确定结束“${item.room.title}”吗？观众将无法继续观看本场直播。`,
        confirmText: action === 'start' ? '开始直播' : '结束直播', cancelText: '取消', success: resolve, fail: () => resolve({ confirm: false }) }))
      if (!result.confirm || !current()) return
      confirmed = true
      const latest = await request({ url: '/shop/live-studio/me' })
      if (!current()) return
      const room = latest?.rooms?.find(row => identifier(row.room?.id) === id)
      if (!latest?.anchor?.anchor || !room || (action === 'start' && (latest.canStart !== true || Number(room.room.status) === 2 || room.roomState === 'DISABLED'))) throw new Error('直播权限或房间状态已变化，请刷新后操作')
      const credential = await request({ url: `/shop/live-studio/rooms/${id}/${action}`, method: 'POST' })
      if (!current()) return
      if (action === 'start') {
        if (!credential || identifier(credential.roomId) !== id) throw new Error('未取得本场推流信息，请先刷新直播状态，不要重复开播')
        this.setData({ credential: { ...credential, roomId: id, expireText: String(credential.expireTime || '').replace('T', ' ').slice(0, 16) } })
        await feedback.notice('直播间已开启，请连接推流工具，并到观众页确认画面。', '开播准备')
      } else { this.setData({ credential: null }); await feedback.success('直播已结束') }
    } catch (error) { if (current()) await feedback.notice(error.message || '操作结果暂未确认，请刷新房间状态后再操作') }
    finally { if (current()) { this.setData({ busyId: '' }); if (confirmed) await this.load(true) } }
  },
  async copyPushUrl() {
    const current = this.current(), credential = this.data.credential
    if (!current() || !credential?.pushUrl) return
    if (credential.expireTime && Date.parse(String(credential.expireTime).replace(' ', 'T')) <= Date.now()) return feedback.notice('推流地址已过期，请联系平台核对，不要继续使用旧地址')
    const result = await new Promise(resolve => wx.showModal({ title: '复制短时推流地址', content: '此地址包含本场推流凭证，仅粘贴到你信任的直播工具，请勿转发或截图。', confirmText: '确认复制', success: resolve, fail: () => resolve({ confirm: false }) }))
    if (result.confirm && current() && credential === this.data.credential) wx.setClipboardData({ data: credential.pushUrl, fail: () => feedback.notice('复制未完成，请重试') })
  },
  closeCredential() { this.setData({ credential: null }) },
  watch(event) { const id = identifier(event.currentTarget.dataset.id); if (id && this.data.rooms.some(item => item.room.id === id)) wx.navigateTo({ url: `/pages/store-content/index?type=live&id=${id}` }) },
  back() { wx.switchTab({ url: '/pages/profile/index' }) }
})
