const request = require('./request')

function normalizeParameters(value) {
  if (!value || !value.timeStamp || !value.nonceStr || !value.packageValue || !value.paySign) {
    throw new Error('微信支付参数不完整')
  }
  return {
    timeStamp: String(value.timeStamp),
    nonceStr: String(value.nonceStr),
    package: String(value.packageValue),
    signType: value.signType || 'RSA',
    paySign: String(value.paySign)
  }
}

function requestPayment(parameters) {
  const options = normalizeParameters(parameters)
  return new Promise((resolve, reject) => {
    wx.requestPayment({ ...options, success: resolve, fail: reject })
  })
}

function isUserCancel(error) {
  return Boolean(error && /cancel/i.test(error.errMsg || error.message || ''))
}

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

function requireCurrent(canContinue) {
  if (!canContinue()) throw new Error('登录状态或页面已变化，请重新打开订单')
}

async function queryUntilConfirmed(orderId, attempts = 5, canContinue = () => true) {
  for (let index = 0; index < attempts; index += 1) {
    requireCurrent(canContinue)
    const paid = await request({ url: '/shop/pay/wechat/query', params: { orderId } })
    requireCurrent(canContinue)
    if (paid) return true
    if (index < attempts - 1) await delay(800 + index * 400)
  }
  return false
}

async function payOrder(orderId, canContinue = () => true) {
  requireCurrent(canContinue)
  const parameters = await request({
    url: '/shop/pay/wechat/create',
    method: 'POST',
    params: { orderId }
  })
  requireCurrent(canContinue)
  await requestPayment(parameters)
  requireCurrent(canContinue)
  return queryUntilConfirmed(orderId, 5, canContinue)
}

module.exports = { normalizeParameters, requestPayment, isUserCancel, queryUntilConfirmed, payOrder }
