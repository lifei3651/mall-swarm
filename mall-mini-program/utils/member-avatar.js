const session = require('./session')
const { API_BASE_URL } = require('../config/runtime')
const fallback = '/assets/profile/user-round.png'
function checkedBase() {
  if (!/^https:\/\/[^/?#]+(?:\/[^?#]*)?$/.test(API_BASE_URL)) throw new Error('头像服务必须使用HTTPS')
  return API_BASE_URL.replace(/\/$/, '')
}
function headers(token) { return { Authorization: `Bearer ${token}`, 'X-Shop-Client': 'wechat-mini-program', 'X-Shop-Surface': 'mini-program' } }
function release(path) {
  if (!path || path === fallback || !/^(wxfile:|http:\/\/tmp\/|https:\/\/tmp\/)/.test(path)) return
  try { wx.getFileSystemManager().unlink({ filePath: path, fail() {} }) } catch (_) {}
}
function upload(filePath) {
  const token = session.getToken()
  if (!token) return Promise.reject(new Error('请重新登录'))
  if (!filePath || /^https?:\/\/(?!tmp\/)/i.test(filePath)) return Promise.reject(new Error('请选择本地头像'))
  return new Promise((resolve, reject) => wx.uploadFile({
    url: `${checkedBase()}/shop/media/member-avatar`, filePath, name: 'file', timeout: 30000, header: headers(token),
    success(response) {
      if (session.getToken() !== token) return reject(new Error('账号已变化，请重新选择头像'))
      let result
      try { result = JSON.parse(response.data) } catch (_) { return reject(new Error('头像服务响应异常')) }
      if (response.statusCode !== 200 || Number(result.code) !== 200) return reject(new Error(result.message || '头像保存失败'))
      if (!/^\/api\/shop\/media\/member-avatar\/\d+\/avatar\.(jpg|png)$/.test(result.data || '')) return reject(new Error('头像地址无效'))
      resolve(result.data)
    },
    fail: () => reject(new Error('头像上传失败，请检查网络及上传域名配置'))
  }))
}
function load(url) {
  const token = session.getToken()
  if (!token || !/^\/api\/shop\/media\/member-avatar\/\d+\/avatar\.(jpg|png)$/.test(url || '')) return Promise.resolve(fallback)
  return new Promise((resolve) => wx.downloadFile({
    url: `${checkedBase()}${url.slice(4)}`, timeout: 15000, header: headers(token),
    success(response) {
      if (session.getToken() !== token || response.statusCode !== 200) { release(response.tempFilePath); resolve(fallback); return }
      resolve(response.tempFilePath || fallback)
    }, fail: () => resolve(fallback)
  }))
}
module.exports = { fallback, upload, load, release }
