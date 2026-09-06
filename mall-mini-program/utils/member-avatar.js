const session = require('./session')
const { API_BASE_URL } = require('../config/runtime')
const { transportError, statusError } = require('./transport-error')
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
      if (response.statusCode === 401) session.clearSession()
      if (response.statusCode !== 200) return reject(new Error(statusError(response.statusCode, '头像上传')))
      let result
      try { result = JSON.parse(response.data) } catch (_) { return reject(new Error('头像服务响应异常')) }
      if (response.statusCode !== 200 || Number(result.code) !== 200) return reject(new Error(result.message || '头像保存失败'))
      if (!/^\/api\/shop\/media\/member-avatar\/\d+\/avatar\.(jpg|png)$/.test(result.data || '')) return reject(new Error('头像地址无效'))
      resolve(result.data)
    },
    fail: (error) => reject(new Error(transportError(error, '头像上传', 'uploadFile')))
  }))
}
function load(url, strict = false) {
  const token = session.getToken()
  if (!token || !/^\/api\/shop\/media\/member-avatar\/\d+\/avatar\.(jpg|png)$/.test(url || '')) return Promise.resolve(fallback)
  return new Promise((resolve, reject) => wx.downloadFile({
    url: `${checkedBase()}${url.slice(4)}`, timeout: 15000, header: headers(token),
    success(response) {
      if (session.getToken() !== token || response.statusCode !== 200) {
        release(response.tempFilePath)
        if (strict) reject(new Error('头像已保存，但预览未能加载。请稍后返回个人中心刷新，不必重复上传。'))
        else resolve(fallback)
        return
      }
      resolve(response.tempFilePath || fallback)
    }, fail: (error) => strict ? reject(new Error(`头像已保存，但预览失败。${transportError(error, '头像预览', 'downloadFile')}`)) : resolve(fallback)
  }))
}
module.exports = { fallback, upload, load, release }
