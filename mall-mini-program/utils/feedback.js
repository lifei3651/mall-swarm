// Explicit, opt-in feedback for pages/components. Never patch wx or Page globally.
// Keep inline retry states, but operation messages must also be visible and acknowledged.
const pending = []
let active = null
function drain() {
  if (active || !pending.length) return
  active = pending.shift()
  const item = active
  const finish = () => { if (active === item) { active = null; drain() } }
  if (typeof wx.hideLoading === 'function') wx.hideLoading()
  wx.showModal({ title: item.title, content: item.content, showCancel: false, confirmText: '知道了',
    success(result) { item.resolve(result); finish() },
    fail() { item.resolve({ confirm: false }); finish() }
  })
}
function notice(content, title = '提示') {
  if (typeof content !== 'string' || !content.trim()) return Promise.resolve({ confirm: false })
  const existing = [active, ...pending].find((item) => item && item.content === content)
  if (existing) return existing.promise
  const item = { title, content }
  item.promise = new Promise((resolve) => { item.resolve = resolve })
  pending.push(item); drain()
  return item.promise
}
function update(page, patch, callback) {
  page.setData(patch, callback)
  if (page.disposed || page.hidden || page._inactive) return
  const error = Object.keys(patch).filter((key) => key === 'error' || key.endsWith('Error')).map((key) => patch[key]).find((value) => typeof value === 'string' && value.trim())
  if (error) notice(error, '请留意')
  else if (typeof patch.message === 'string' && patch.message.trim()) notice(patch.message, '操作结果')
  else if (typeof patch.loginNotice === 'string' && patch.loginNotice.trim()) notice(patch.loginNotice)
}
function toast(options = {}) {
  return notice(options.title, options.icon === 'success' ? '操作完成' : '提示').then((result) => {
    if (options.success) options.success(result)
    if (options.complete) options.complete(result)
    return result
  })
}
module.exports = { notice, update, toast }
