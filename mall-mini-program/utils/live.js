const request = require('./request')
const session = require('./session')
const auth = require('./auth')
const feedback = require('./feedback')
const format = require('./format')
const share = require('./share')

const heat = value => { const n = Math.max(0, Number(value) || 0); return n >= 10000 ? `${(n / 10000).toFixed(n >= 100000 ? 0 : 1)}万` : String(n) }
function room(item) {
  if (!item || !item.room || !format.identifier(item.room.id)) return null
  return { ...item, room: { ...item.room, id: format.identifier(item.room.id), coverUrl: format.mediaUrl(item.room.coverUrl), viewerText: heat(item.room.viewerCount), heatText: heat(item.room.heatCount), scheduleText: item.room.scheduledStartTime ? `${String(item.room.scheduledStartTime).replace('T', ' ').slice(0, 16)} 开播` : '开播时间待定' }, products: (item.products || []).map(format.product) }
}
function current(page, token, sequence) { return !page.hidden && !page.disposed && token === session.getToken() && (sequence === undefined || sequence === page.sequence) }
function renderRooms(page) {
  page.setData({ filteredRooms: page.data.rooms.filter(item => page.data.liveTab === 'upcoming' ? item.roomState === 'UPCOMING' : ['LIVE', 'CONNECTING'].includes(item.roomState)).map(item => ({ ...item, reserved: (page.data.reservedIds || []).includes(String(item.room.id)) })) })
}
async function reservations(page) {
  const token = session.getToken(), sequence = page.sequence
  page.setData({ reservedIds: [], reservationReady: !token })
  if (!token) return renderRooms(page)
  try {
    const ids = await request({ url: '/shop/live-reservations' })
    if (!Array.isArray(ids)) throw new Error('直播预约状态暂不可用')
    if (current(page, token, sequence)) { page.setData({ reservedIds: ids.map(format.identifier).filter(Boolean), reservationReady: true }); renderRooms(page) }
  } catch (_) { if (current(page, token, sequence)) page.setData({ reservationReady: false }) }
}
async function toggleReservation(page, id) {
  id = format.identifier(id)
  if (!id || page.data.reservingId || page.hidden || !page.data.rooms.some(item => item.room.id === id && item.roomState === 'UPCOMING') || !auth.requireLogin('/pages/store-content/index?type=live&tab=upcoming')) return
  const token = session.getToken(), sequence = page.sequence
  page.setData({ reservingId: id })
  try {
    if (!page.data.reservationReady) await reservations(page)
    if (!current(page, token, sequence)) return
    if (!page.data.reservationReady) throw new Error('暂时无法确认预约状态，请重新加载后再试')
    const reserved = page.data.reservedIds.includes(id)
    await request({ url: `/shop/live-rooms/${id}/reservation`, method: reserved ? 'DELETE' : 'POST' })
    if (!current(page, token, sequence)) return
    page.setData({ reservedIds: reserved ? page.data.reservedIds.filter(value => value !== id) : page.data.reservedIds.concat(id) }); renderRooms(page)
    await feedback.notice(reserved ? '已取消直播预约' : '预约成功，可随时回来查看', '预约结果')
  } catch (error) { if (current(page, token, sequence)) { page.setData({ reservationReady: false }); await feedback.notice(error.message || '预约结果暂未确认，请重新加载查看') } }
  finally { if (!page.disposed) page.setData({ reservingId: '' }) }
}
function visitor(page) {
  if (page.liveVisitor) return page.liveVisitor
  const key = 'mall_mini_live_visitor_id', saved = wx.getStorageSync(key)
  // An anonymous analytics label, never a login/session/authorization credential.
  const id = /^[0-9a-f-]{36}$/i.test(String(saved || '')) ? saved : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => { const n = Math.floor(Math.random() * 16); return (c === 'x' ? n : (n & 3) | 8).toString(16) })
  wx.setStorageSync(key, id); page.liveVisitor = id; return id
}
async function event(page, eventType, productId = null) {
  if (!page.roomId || !page.data.room || page.liveToken !== session.getToken() || !['ENTER', 'HEARTBEAT', 'LEAVE', 'SHARE', 'PRODUCT_CLICK'].includes(eventType)) return
  try { await request({ url: `/shop/live-rooms/${page.roomId}/engagement`, method: 'POST', data: { visitorId: visitor(page), eventType, productId, durationSeconds: Math.max(0, Math.min(86400, Math.floor((Date.now() - page.enteredAt) / 1000))) } }) } catch (_) { /* Analytics must not block viewing or purchasing. */ }
}
async function comments(page) {
  if (!page.roomId || !page.data.room || page.hidden || page.commentsFetching) return
  const token = session.getToken(), sequence = page.sequence
  page.commentsFetching = true
  try {
    const list = await request({ url: `/shop/live-rooms/${page.roomId}/comments`, params: { limit: 80 } })
    if (!Array.isArray(list)) throw new Error('评论数据暂不可用')
    if (current(page, token, sequence)) page.setData({ comments: list.slice().reverse(), commentError: '', commentScroll: Date.now() })
  } catch (_) { if (current(page, token, sequence)) page.setData({ commentError: '评论暂时加载失败，点击重试；不影响观看' }) }
  finally { page.commentsFetching = false }
}
async function sendComment(page) {
  const content = String(page.data.commentText || '').trim()
  if (!content || content.length > 300 || page.data.commentSaving || page.hidden || !page.data.room || page.data.room.roomState !== 'LIVE' || page.data.room.room.commentEnabled !== 1 || !auth.requireLogin(`/pages/store-content/index?type=live&id=${page.roomId}`)) return
  const token = session.getToken(), sequence = page.sequence
  page.setData({ commentSaving: true })
  try {
    const fresh = await request({ url: `/shop/live-rooms/${page.roomId}` })
    if (!current(page, token, sequence)) return
    if (!fresh || !fresh.room || format.identifier(fresh.room.id) !== page.roomId || fresh.roomState !== 'LIVE' || fresh.room.commentEnabled !== 1) throw new Error('本场直播已结束或评论已关闭，请刷新页面')
    await request({ url: `/shop/live-rooms/${page.roomId}/comments`, method: 'POST', data: { content, visitorId: visitor(page) } })
    if (!current(page, token, sequence)) return
    page.setData({ commentText: '' }); await comments(page)
  } catch (error) { if (current(page, token, sequence)) await feedback.notice(error.message || '评论未能确认发送，请先查看评论列表，避免重复发送') }
  finally { if (!page.disposed) page.setData({ commentSaving: false }) }
}
function stop(page) {
  clearTimeout(page.commentTimer); clearTimeout(page.heartbeatTimer)
  page.commentTimer = null; page.heartbeatTimer = null
  if (page.liveEntered) { page.liveEntered = false; event(page, 'LEAVE') }
  share.hide(page)
}
function start(page) {
  stop(page)
  if (page.hidden || !page.data.room || !page.roomId) return
  page.enteredAt = Date.now(); page.liveToken = session.getToken(); page.liveEntered = true
  event(page, 'ENTER'); comments(page)
  if (page.data.room.room.shareEnabled === 1) share.prepare(page)
  const poll = () => { page.commentTimer = setTimeout(async () => { if (!current(page, page.liveToken)) return; await comments(page); if (current(page, page.liveToken)) poll() }, 4000) }
  const heartbeat = () => { page.heartbeatTimer = setTimeout(async () => { if (!current(page, page.liveToken)) return; await event(page, 'HEARTBEAT'); if (current(page, page.liveToken)) heartbeat() }, 30000) }
  poll(); heartbeat()
}
module.exports = { room, renderRooms, reservations, toggleReservation, comments, sendComment, start, stop, event }
