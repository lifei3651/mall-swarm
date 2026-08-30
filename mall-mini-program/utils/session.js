const TOKEN_KEY = 'mall_mini_access_token'
const MEMBER_KEY = 'mall_mini_member'

function getToken() { return wx.getStorageSync(TOKEN_KEY) || '' }
function getMember() { return wx.getStorageSync(MEMBER_KEY) || null }
function saveSession(data) {
  if (!data || !data.accessToken) return false
  wx.setStorageSync(TOKEN_KEY, data.accessToken)
  wx.setStorageSync(MEMBER_KEY, data.member || null)
  return true
}
function clearSession() {
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync(MEMBER_KEY)
}

module.exports = { getToken, getMember, saveSession, clearSession }
