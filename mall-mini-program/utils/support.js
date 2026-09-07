const types = [{ key: 'CONSULTATION', label: '咨询' }, { key: 'COMPLAINT', label: '投诉' }, { key: 'AFTER_SALE_DISPUTE', label: '售后争议' }, { key: 'ACCOUNT', label: '账号问题' }, { key: 'OTHER', label: '其他' }]
const filters = [{ key: '', label: '全部' }, { key: 'OPEN', label: '待处理' }, { key: 'PROCESSING', label: '处理中' }, { key: 'WAITING_MEMBER', label: '待我补充' }, { key: 'RESOLVED', label: '已答复' }, { key: 'CLOSED', label: '已关闭' }]
const time = value => value ? String(value).replace('T', ' ').slice(0,16) : '-'
const decorate = ticket => ({ ...ticket, typeText: types.find(item => item.key === ticket.type)?.label || '其他', statusText: filters.find(item => item.key === ticket.status)?.label || '处理中', timeText: time(ticket.lastReplyTime), deadlineText: time(ticket.firstResponseDeadline) })
function keyFor(page, purpose, payload, token) {
  const snapshot = JSON.stringify([purpose, token, payload])
  if (!page.requestIdentity || page.requestIdentity.snapshot !== snapshot) page.requestIdentity = { snapshot, key: `mini-${purpose}-${Date.now()}-${Math.random().toString(36).slice(2)}` }
  return page.requestIdentity.key
}
module.exports = { types, filters, time, decorate, keyFor }
