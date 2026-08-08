import request from './request'

const idempotencyHeaders = (key) => key ? { 'X-Idempotency-Key': key } : undefined

export function register(data) {
  return request({
    url: '/shop/auth/register',
    method: 'post',
    data,
  })
}

export function login(data) {
  return request({
    url: '/shop/auth/login',
    method: 'post',
    data,
  })
}

export function getLoginCaptcha() {
  return request({ url: '/captcha', method: 'get', params: { scene: 'shop' } })
}

export function getMe() {
  return request({
    url: '/shop/auth/me',
    method: 'get',
  })
}

export function setupAccount(data) {
  return request({
    url: '/shop/auth/account',
    method: 'put',
    data,
  })
}

export function changeLoginPassword(data) {
  return request({
    url: '/shop/auth/password',
    method: 'put',
    data,
  })
}

export function updateNickname(nickname) {
  return request({
    url: '/shop/auth/nickname',
    method: 'put',
    data: { nickname },
  })
}

export function updatePhone(data) {
  return request({
    url: '/shop/auth/phone',
    method: 'put',
    data,
  })
}

export function logout() {
  return request({
    url: '/shop/auth/logout',
    method: 'post',
  })
}

export function getHome(params) {
  return request({
    url: '/shop/home',
    method: 'get',
    params,
  })
}

export function getLegalConfig() {
  return request({ url: '/shop/legal-config', method: 'get' })
}

export function listProducts(params) {
  return request({
    url: '/shop/products',
    method: 'get',
    params,
  })
}

export function getProduct(id) {
  return request({
    url: `/shop/products/${id}`,
    method: 'get',
  })
}

export function getProductReviews(productId, params) {
  return request({
    url: `/shop/products/${productId}/reviews`,
    method: 'get',
    params,
  })
}

export function submitProductReview(productId, data) {
  return request({
    url: `/shop/products/${productId}/reviews`,
    method: 'post',
    data,
  })
}

export function listAddresses() {
  return request({
    url: '/shop/addresses',
    method: 'get',
  })
}

export function saveAddress(data) {
  return request({
    url: '/shop/addresses',
    method: 'post',
    data,
  })
}

export function deleteAddress(id) {
  return request({
    url: `/shop/addresses/${id}`,
    method: 'delete',
  })
}

export function submitOrder(data, idempotencyKey) {
  return request({
    url: '/shop/orders',
    method: 'post',
    data,
    headers: idempotencyHeaders(idempotencyKey),
  })
}

export function quoteFreight(data) {
  return request({
    url: '/shop/orders/freight-quote',
    method: 'post',
    data,
  })
}

export function getOrder(id) {
  return request({
    url: `/shop/orders/${id}`,
    method: 'get',
  })
}

export function listMyOrders(params) {
  return request({
    url: '/shop/orders',
    method: 'get',
    params,
  })
}

export function cancelOrder(id) {
  return request({
    url: `/shop/orders/${id}/cancel`,
    method: 'put',
  })
}

export function confirmReceive(id) {
  return request({
    url: `/shop/orders/${id}/receive`,
    method: 'put',
  })
}

export function payOrder(id, payType = 'simulated') {
  return request({
    url: `/shop/orders/${id}/pay`,
    method: 'post',
    params: { payType },
  })
}

export function getWalletSummary() {
  return request({ url: '/shop/wallet/summary', method: 'get' })
}

export function findBalanceRecipient(phone) {
  return request({ url: '/shop/wallet/recipient', method: 'post', data: { phone } })
}

export function setPaymentPassword(data) {
  return request({ url: '/shop/wallet/payment-password', method: 'put', data })
}

export function transferBalance(data, idempotencyKey) {
  return request({ url: '/shop/wallet/transfers', method: 'post', data, headers: idempotencyHeaders(idempotencyKey) })
}

export function payOrderWithBalance(id, paymentPassword, idempotencyKey) {
  return request({
    url: `/shop/wallet/orders/${id}/pay`,
    method: 'post',
    data: { paymentPassword },
    headers: idempotencyHeaders(idempotencyKey),
  })
}

export function createAlipayOrder(orderId) {
  return request({ url: `/shop/pay/alipay/create`, method: 'post', params: { orderId } })
}

export function queryAlipayOrderStatus(orderId) {
  return request({ url: `/shop/pay/alipay/query`, method: 'get', params: { orderId } })
}

export function getPayConfig() {
  return request({ url: '/shop/pay/config', method: 'get' })
}

export function applyWithdrawal(data, idempotencyKey) {
  return request({ url: '/shop/wallet/withdrawals', method: 'post', data, headers: idempotencyHeaders(idempotencyKey) })
}

export function listMyWithdrawals() {
  return request({ url: '/shop/wallet/withdrawals', method: 'get' })
}

export function listMyBalanceFlows(params) {
  return request({ url: '/shop/wallet/flows', method: 'get', params })
}

export function applyAfterSale(data) {
  return request({
    url: '/shop/after-sales',
    method: 'post',
    data,
  })
}

export function cancelAfterSale(id) {
  return request({
    url: `/shop/after-sales/${id}/cancel`,
    method: 'put',
  })
}

export function submitAfterSaleReturnShipment(id, data) {
  return request({
    url: `/shop/after-sales/${id}/return-shipment`,
    method: 'put',
    data,
  })
}

export function getProfile(params) {
  return request({
    url: '/shop/profile',
    method: 'get',
    params,
  })
}

export function getProfileOrderSummary() {
  return request({
    url: '/shop/profile/order-summary',
    method: 'get',
  })
}

export function getProfilePerformance() {
  return request({
    url: '/shop/profile/performance',
    method: 'get',
  })
}

// 公告相关
export function listNotices(params) {
  return request({
    url: '/shop/notices',
    method: 'get',
    params,
  })
}

export function getNotice(id) {
  return request({
    url: `/shop/notices/${id}`,
    method: 'get',
  })
}

// 分类相关
export function listCategories(params) {
  return request({
    url: '/shop/categories',
    method: 'get',
    params,
  })
}

export function listCategoryProducts(params) {
  return request({
    url: '/shop/products',
    method: 'get',
    params,
  })
}

// 短信验证码
export function sendSmsCode(phone, bizType = 1) {
  return request({
    url: '/sms/send',
    method: 'post',
    data: { phone, bizType },
  })
}

// 支付密码验证码由服务端固定业务类型，避免不同版本页面传错短信类型。
export function sendPaymentPasswordSmsCode() {
  return request({
    url: '/sms/send/payment-password',
    method: 'post',
  })
}

export function verifySmsCode(phone, code, bizType = 1) {
  return request({
    url: '/sms/verify',
    method: 'post',
    data: { phone, code, bizType },
  })
}

// 支付验证
export function checkPaymentVerify(amount, tenantId = 1) {
  return request({
    url: '/payment/checkVerify',
    method: 'get',
    params: { amount, tenantId },
  })
}

// 密码重置（商城会员）
export function resetPassword(data) {
  return request({
    url: '/shop/auth/resetPassword',
    method: 'post',
    data: { phone: data.phone, smsCode: data.code, newPassword: data.newPassword },
  })
}

// 邀请信息
export function getInviteInfo() {
  return request({
    url: '/shop/invite/my',
    method: 'get',
  })
}

export function getInviterPreview(inviteCode) {
  return request({
    url: `/shop/invite/${encodeURIComponent(inviteCode)}`,
    method: 'get',
  })
}
