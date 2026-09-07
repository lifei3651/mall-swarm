const session = require('./session')

// Native counterpart of H5's visible-page 30s fallback. Never refresh a hidden page.
function start(page, refresh, busy = () => false) {
  stop(page)
  const owner = session.getToken()
  if (!owner) return
  let timer, cancelled = false
  page._stopForegroundRefresh = () => { cancelled = true; clearTimeout(timer) }
  const current = () => !cancelled && !page.disposed && session.getToken() === owner
  function schedule() {
    if (!current()) return
    timer = setTimeout(async () => {
      if (!current()) return
      try { if (!busy()) await refresh() } catch (_) { /* Page keeps its last data and exposes retry. */ }
      schedule()
    }, 30000)
    if (timer && timer.unref) timer.unref()
  }
  schedule()
}
function stop(page) {
  if (page._stopForegroundRefresh) page._stopForegroundRefresh()
  page._stopForegroundRefresh = null
}
async function visiblePages(request, options, current) {
  const list = [], pageCount = Math.max(1, options.pageCount || 1)
  let last = {}
  for (let pageNum = 1; pageNum <= pageCount; pageNum++) {
    if (!current()) return null
    last = await request({ url: options.url, params: { ...options.params, pageNum, pageSize: options.pageSize } })
    if (!current()) return null
    if (!last || !Array.isArray(last.list)) throw new Error('返回的数据不完整，请重新加载')
    list.push(...last.list)
    if (last.list.length < options.pageSize || (last.totalPage && pageNum >= last.totalPage)) break
  }
  return { ...last, list }
}
module.exports = { start, stop, visiblePages }
