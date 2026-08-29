import request from './request'

const idempotencyHeaders = (key) => key ? { 'X-Idempotency-Key': key } : undefined

export function register(data) {
  return request({
    url: '/shop/auth/register',
    method: 'post',
    data,
  })
}

export function registerPublic(data) {
  return request({
    url: '/shop/public/auth/register',
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

export function getBusinessConfig() {
  return request({ url: '/shop/business-config', method: 'get' })
}

export function listFlashSales() {
  return request({ url: '/shop/flash-sales', method: 'get' })
}

export function listLiveRooms(params) {
  return request({ url: '/shop/live-rooms', method: 'get', params })
}

export function getLiveRoom(id) {
  return request({ url: `/shop/live-rooms/${id}`, method: 'get' })
}

export function listLiveComments(id, params) {
  return request({ url: `/shop/live-rooms/${id}/comments`, method: 'get', params })
}

export function submitLiveComment(id, data) {
  return request({ url: `/shop/live-rooms/${id}/comments`, method: 'post', data })
}

export function recordLiveEngagement(id, data) {
  return request({ url: `/shop/live-rooms/${id}/engagement`, method: 'post', data })
}

export function listLiveReservations() {
  return request({ url: '/shop/live-reservations', method: 'get' })
}

export function reserveLiveRoom(id) {
  return request({ url: `/shop/live-rooms/${id}/reservation`, method: 'post' })
}

export function cancelLiveReservation(id) {
  return request({ url: `/shop/live-rooms/${id}/reservation`, method: 'delete' })
}

export function getLiveStudio() {
  return request({ url: '/shop/live-studio/me', method: 'get' })
}

export function startLiveRoom(id) {
  return request({ url: `/shop/live-studio/rooms/${id}/start`, method: 'post' })
}

export function stopLiveRoom(id) {
  return request({ url: `/shop/live-studio/rooms/${id}/stop`, method: 'post' })
}

export function listNewArrivals(params) {
  return request({ url: '/shop/new-arrivals', method: 'get', params })
}

export function getBrandCulture() {
  return request({ url: '/shop/brand-culture', method: 'get' })
}

export function submitFlashSaleOrder(activityId, data, idempotencyKey) {
  return request({
    url: `/shop/flash-sales/${activityId}/orders`,
    method: 'post', data, headers: idempotencyHeaders(idempotencyKey),
  })
}

export function listRepurchaseProducts(params) {
  return request({ url: '/shop/repurchase/products', method: 'get', params })
}

export function getRepurchaseProduct(id) {
  return request({ url: `/shop/repurchase/products/${id}`, method: 'get' })
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

export function checkPurchaseLimit(productId, quantity = 1) {
  return request({
    url: `/shop/products/${productId}/purchase-limit/check`,
    method: 'post',
    params: { quantity },
  })
}

export function getOrder(id) {
  return request({
    url: `/shop/orders/${id}`,
    method: 'get',
  })
}

export function getOrderTracking(id) {
  return request({
    url: `/shop/orders/${id}/tracking`,
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

export function listServiceTickets(params) {
  return request({ url: '/shop/service-tickets', method: 'get', params })
}

export function createServiceTicket(data, idempotencyKey) {
  return request({ url: '/shop/service-tickets', method: 'post', data, headers: idempotencyHeaders(idempotencyKey) })
}

export function getServiceTicket(id) {
  return request({ url: `/shop/service-tickets/${id}`, method: 'get' })
}

export function replyServiceTicket(id, data, idempotencyKey) {
  return request({ url: `/shop/service-tickets/${id}/replies`, method: 'post', data, headers: idempotencyHeaders(idempotencyKey) })
}

export function closeServiceTicket(id) {
  return request({ url: `/shop/service-tickets/${id}/close`, method: 'put' })
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

export function getRealNameStatus() {
  return request({ url: '/shop/real-name/status', method: 'get' })
}

export function verifyRealName(data) {
  return request({ url: '/shop/real-name/verify', method: 'post', data })
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

export function uploadAfterSaleProof(orderId, file) {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: '/shop/media/after-sale-proofs',
    method: 'post',
    data,
    params: { orderId },
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

export function confirmAfterSaleExchangeReceived(id) {
  return request({
    url: `/shop/after-sales/${id}/exchange-received`,
    method: 'put',
  })
}

export function getProfile(params) {
  return request({
    url: '/shop/profile',
    method: 'get',
    params,
  })
}

export function getPublicProfile() {
  return request({ url: '/shop/public/profile', method: 'get' })
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
export function sendSmsCode(phone, bizType = 1, captcha = {}) {
  return request({
    url: '/sms/send',
    method: 'post',
    data: { phone, bizType, captchaId: captcha.captchaId, captchaCode: captcha.captchaCode },
  })
}

// 验证码登录使用服务端固定业务类型，避免不同页面或缓存版本传错类型。
export function sendLoginSmsCode(phone) {
  return request({
    url: '/sms/send/login',
    method: 'post',
    data: { phone },
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
    url: `/shop/public/inviter-preview/${encodeURIComponent(inviteCode)}`,
    method: 'get',
  })
}

export function bindTeamInvitation(inviteCode) {
  return request({ url: '/shop/team/invitation', method: 'post', data: { inviteCode } })
}

export function listMemberMessages(params) { return request({ url: '/shop/messages', method: 'get', params }) }
export function getMemberMessage(id) { return request({ url: `/shop/messages/${id}`, method: 'get' }) }
export function getMessageUnread() { return request({ url: '/shop/messages/unread', method: 'get' }) }
export function markMessageRead(id) { return request({ url: `/shop/messages/${id}/read`, method: 'put' }) }
export function markMessageCategoryRead(category) { return request({ url: '/shop/messages/read-category', method: 'put', params: { category } }) }
export function markAllMessagesRead() { return request({ url: '/shop/messages/read-all', method: 'put' }) }
export function getServiceSmsPreference() { return request({ url: '/shop/messages/preferences/sms', method: 'get' }) }
export function updateServiceSmsPreference(data) { return request({ url: '/shop/messages/preferences/sms', method: 'put', data }) }
