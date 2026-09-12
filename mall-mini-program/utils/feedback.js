// Explicit, opt-in feedback for pages/components. Never patch wx or Page globally.
// Errors and explicit risk/explanation notices require acknowledgement. Ordinary
// success stays inline, or uses a short non-blocking tip when no inline state exists.
const pending = []
let active = null
function drain() {
  if (active || !pending.length) return
  active = pending.shift()
  const item = active
  const finish = () => { if (active === item) { active = null; drain() } }
  if (typeof wx.hideLoading === 'function') wx.hideLoading()
  if (typeof wx.hideToast === 'function') wx.hideToast()
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
}
function success(content) {
  // Never queue a stale success behind an error, or let tip failures affect a
  // completed operation. Callers continue immediately without user confirmation.
  if (typeof content !== 'string' || !content.trim() || active || pending.length || typeof wx.showToast !== 'function') return Promise.resolve({ shown: false })
  try {
    wx.showToast({ title: content.trim(), icon: 'none', duration: 1500, mask: false })
    return Promise.resolve({ shown: true })
  } catch (_) { return Promise.resolve({ shown: false }) }
}
function toast(options = {}) {
  const result = options.icon === 'success' ? success(options.title) : notice(options.title)
  return result.then((result) => {
    if (options.success) options.success(result)
    if (options.complete) options.complete(result)
    return result
  })
}
module.exports = { notice, update, toast, success }
