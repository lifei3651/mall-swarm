// Download in the background; applying is always an explicit action on a safe page.
// Never reload checkout, account forms, live publishing, payment, or authorization.
const feedback = require('./feedback')
let manager, state = 'checking', installed = false, prompting = false
function install() {
  if (installed) return
  installed = true
  if (typeof wx.getUpdateManager !== 'function') { state = 'unsupported'; return }
  try {
    manager = wx.getUpdateManager()
    manager.onCheckForUpdate(result => { if (state !== 'ready') state = result.hasUpdate ? 'downloading' : 'current' })
    manager.onUpdateReady(() => { state = 'ready' })
    manager.onUpdateFailed(() => { state = 'failed' })
  } catch (_) { state = 'failed' }
}
function safeToApply() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  if (pages.length !== 1) return false
  const page = pages[0], data = page.data || {}
  return ['pages/home/index', 'pages/category/index', 'pages/cart/index', 'pages/profile/index'].includes(page.route)
    && !['loginVisible', 'loading', 'submitting', 'paying', 'authorizingPhone', 'busy', 'adding', 'addingId', 'checkingOut', 'checkoutChecking', 'quantityChecking', 'managing'].some(key => data[key])
    && !page.hidden && !page.disposed && !page.inactive && !page._inactive && !page.purchaseInactive
}
async function check() {
  install(); if (prompting) return
  const messages = { checking: '正在检查版本，请稍后重试。', current: '当前已是最新可用版本。', downloading: '新版本正在下载，完成后再点击检查更新。', failed: '新版下载未完成，请检查网络后重新打开小程序再试。', unsupported: '当前微信不支持在线更新，请更新微信后重新打开小程序。' }
  if (state !== 'ready') return feedback.notice(messages[state], '版本更新')
  if (!safeToApply()) return feedback.notice('新版本已准备好。请先完成当前操作并关闭登录窗口，返回“我的”后再更新。', '版本更新')
  prompting = true
  try {
    const result = await new Promise(resolve => wx.showModal({ title: '新版本已准备好', content: '现在更新将重新打开小程序。购物车会保留；请确认当前没有未完成的操作。', confirmText: '立即更新', cancelText: '稍后再说', success: resolve, fail: () => resolve({ confirm: false }) }))
    if (result.confirm && state === 'ready' && safeToApply()) manager.applyUpdate()
  } finally { prompting = false }
}
module.exports = { install, check, safeToApply }
