const privacy = require('./privacy')
function normalize(value) {
  const text = (value) => typeof value === 'string' ? value.trim() : ''
  const region = [value.provinceName, value.cityName, value.countyName].map(text)
  const detailAddress = text(value.detailInfo) || [text(value.streetName), text(value.detailInfoNew)].filter(Boolean).join('')
  return {
    id: null, receiverName: text(value.userName), receiverPhone: text(value.telNumber),
    region, regionText: region.filter(Boolean).join(' '), detailAddress, isDefault: false
  }
}
async function choose() {
  await privacy.requireConsent()
  return new Promise((resolve, reject) => {
    if (typeof wx.chooseAddress !== 'function') return reject(new Error('当前微信不支持地址导入，请手动填写'))
    wx.chooseAddress({
      success: (value) => resolve(normalize(value)),
      fail: (error) => reject(new Error(/cancel/i.test(error.errMsg || '')
        ? '已取消导入，原地址未改变' : '未能读取微信地址，请检查接口权限或手动填写'))
    })
  })
}
module.exports = { normalize, choose }
