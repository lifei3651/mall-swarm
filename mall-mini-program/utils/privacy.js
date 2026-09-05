let installed = false

function install() {
  if (installed || typeof wx.onNeedPrivacyAuthorization !== 'function') return
  installed = true
  wx.onNeedPrivacyAuthorization((resolve) => {
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    const page = pages[pages.length - 1]
    const panel = page && page.selectComponent && page.selectComponent('#privacy-consent')
    if (panel && panel.open) panel.open(resolve)
    else resolve({ event: 'disagree' })
  })
}

function requireConsent() {
  install()
  return new Promise((resolve, reject) => {
    if (typeof wx.requirePrivacyAuthorize !== 'function') {
      reject(new Error('当前微信版本不支持隐私授权，请更新微信；也可继续手动填写'))
      return
    }
    wx.requirePrivacyAuthorize({
      success: resolve,
      fail: () => reject(new Error('未获得授权，未读取微信资料；仍可手动填写'))
    })
  })
}

module.exports = { install, requireConsent }
