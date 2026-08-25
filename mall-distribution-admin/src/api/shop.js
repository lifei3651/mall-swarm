import request from '@/utils/request'

export function listShopProducts(params) {
  return request({
    url: '/shop/admin/products',
    method: 'get',
    params,
  })
}

export function createShopProduct(data) {
  return request({
    url: '/shop/admin/products',
    method: 'post',
    data,
  })
}

export function updateShopProduct(id, data) {
  return request({
    url: `/shop/admin/products/${id}`,
    method: 'put',
    data,
  })
}

export function publishShopProduct(id, data) {
  return request({
    url: id ? `/shop/admin/products/${id}/publish` : '/shop/admin/products/publish',
    method: id ? 'put' : 'post',
    data,
  })
}

export function updateShopProductStatus(id, status) {
  return request({
    url: `/shop/admin/products/${id}/status`,
    method: 'put',
    params: { status },
  })
}

export function updateProductNewArrival(id, data) {
  return request({ url: `/shop/admin/products/${id}/new-arrival`, method: 'put', data })
}

export function submitMerchantProductReview(id) {
  return request({ url: `/shop/admin/products/${id}/submit-review`, method: 'post' })
}

export function listMerchantProductReviews(params) {
  return request({ url: '/shop/admin/merchant-product-reviews', method: 'get', params })
}

export function decideMerchantProductReview(id, data) {
  return request({ url: `/shop/admin/merchant-product-reviews/${id}/decision`, method: 'put', data })
}

export function listFlashSales(params) {
  return request({ url: '/shop/admin/flash-sales', method: 'get', params })
}

export function saveFlashSale(id, data) {
  return request({ url: id ? `/shop/admin/flash-sales/${id}` : '/shop/admin/flash-sales', method: id ? 'put' : 'post', data })
}

export function updateFlashSaleStatus(id, status) {
  return request({ url: `/shop/admin/flash-sales/${id}/status`, method: 'put', params: { status } })
}

export function listLiveRooms(params) {
  return request({ url: '/shop/admin/live-rooms', method: 'get', params })
}

export function saveLiveRoom(id, data) {
  return request({ url: id ? `/shop/admin/live-rooms/${id}` : '/shop/admin/live-rooms', method: id ? 'put' : 'post', data })
}

export function updateLiveRoomStatus(id, status) {
  return request({ url: `/shop/admin/live-rooms/${id}/status`, method: 'put', params: { status } })
}

export function listLiveAnchors(params) {
  return request({ url: '/shop/admin/live-anchors', method: 'get', params })
}

export function saveLiveAnchor(id, data) {
  return request({ url: id ? `/shop/admin/live-anchors/${id}` : '/shop/admin/live-anchors', method: id ? 'put' : 'post', data })
}

export function updateLiveAnchorStatus(id, status) {
  return request({
    url: `/shop/admin/live-anchors/${id}/status`, method: 'put', params: { status },
    adminStepUp: { message: status === 1 ? '恢复后该账号可以立即开播，请再次验证。' : '暂停或收回权限会立即停止该主播正在进行的直播，请再次验证。' },
  })
}

export function forceStopLiveRoom(id, reason) {
  return request({
    url: `/shop/admin/live-rooms/${id}/force-stop`, method: 'post', params: { reason },
    adminStepUp: { message: '强制停播会立即中断观众观看，请确认违规或运营原因后再次验证。' },
  })
}

export function getLiveRoomAnalytics(id) {
  return request({ url: `/shop/admin/live-rooms/${id}/analytics`, method: 'get' })
}

export function listAdminLiveComments(id, params) {
  return request({ url: `/shop/admin/live-rooms/${id}/comments`, method: 'get', params })
}

export function updateLiveCommentStatus(id, status) {
  return request({ url: `/shop/admin/live-comments/${id}/status`, method: 'put', params: { status } })
}

export function listShopServiceAddresses(params) {
  return request({ url: '/shop/admin/service-addresses', method: 'get', params })
}

export function saveShopServiceAddress(data) {
  return request({ url: '/shop/admin/service-addresses', method: 'post', data })
}

export function updateShopServiceAddressStatus(id, status, tenantId = 1) {
  return request({ url: `/shop/admin/service-addresses/${id}/status`, method: 'put', params: { status, tenantId } })
}

export function listShopCategories(params) {
  return request({ url: '/shop/admin/categories', method: 'get', params })
}

export function createShopCategory(data) {
  return request({ url: '/shop/admin/categories', method: 'post', data })
}

export function updateShopCategory(id, data) {
  return request({ url: `/shop/admin/categories/${id}`, method: 'put', data })
}

export function deleteShopCategory(id) {
  return request({ url: `/shop/admin/categories/${id}`, method: 'delete' })
}

export function updateShopCategoryStatus(id, status) {
  return request({ url: `/shop/admin/categories/${id}/status`, method: 'put', params: { status } })
}

export function updateCategoryShowOnHome(id, showOnHome) {
  return request({ url: `/shop/admin/categories/${id}/show-on-home`, method: 'put', params: { showOnHome } })
}

export function listShopBanners(params) {
  return request({ url: '/shop/admin/banners', method: 'get', params })
}

export function createShopBanner(data) {
  return request({ url: '/shop/admin/banners', method: 'post', data })
}

export function updateShopBanner(id, data) {
  return request({ url: `/shop/admin/banners/${id}`, method: 'put', data })
}

export function updateShopBannerStatus(id, status) {
  return request({ url: `/shop/admin/banners/${id}/status`, method: 'put', params: { status } })
}

export function listShopNotices(params) {
  return request({ url: '/shop/admin/notices', method: 'get', params })
}

export function createShopNotice(data) {
  return request({ url: '/shop/admin/notices', method: 'post', data })
}

export function updateShopNotice(id, data) {
  return request({ url: `/shop/admin/notices/${id}`, method: 'put', data })
}

export function updateShopNoticeStatus(id, status) {
  return request({ url: `/shop/admin/notices/${id}/status`, method: 'put', params: { status } })
}

export function deleteShopNotice(id) {
  return request({ url: `/shop/admin/notices/${id}`, method: 'delete' })
}

export function uploadShopImage(file) {
  const data = new FormData()
  data.append('file', file)
  // 不手动设置 Content-Type，让浏览器自动补齐 multipart boundary。
  // 部分浏览器在手动指定 multipart/form-data 时会丢失 boundary，导致后端无法读取文件。
  return request({ url: '/shop/admin/media/images', method: 'post', data })
}

export function getProductSettings() {
  return request({ url: '/shop/admin/product-settings', method: 'get', params: { _t: Date.now() }, headers: { 'Cache-Control': 'no-cache' } })
}

export function updateProductPvSetting(enabled) {
  return request({ url: '/shop/admin/product-settings/pv', method: 'put', params: { enabled } })
}

export function listProductReviews(params) {
  return request({ url: '/shop/admin/reviews', method: 'get', params })
}

export function updateProductReviewStatus(id, data) {
  return request({ url: `/shop/admin/reviews/${id}/status`, method: 'put', data })
}

export function listShopSkus(productId, params) {
  return request({
    url: `/shop/admin/products/${productId}/skus`,
    method: 'get',
    params,
  })
}

export function createShopSku(data) {
  return request({
    url: '/shop/admin/skus',
    method: 'post',
    data,
  })
}

export function updateShopSku(id, data) {
  return request({
    url: `/shop/admin/skus/${id}`,
    method: 'put',
    data,
  })
}

export function updateShopSkuStatus(id, status) {
  return request({
    url: `/shop/admin/skus/${id}/status`,
    method: 'put',
    params: { status },
  })
}

export function listFreightTemplates(params) {
  return request({ url: '/shop/admin/freight-templates', method: 'get', params })
}

export function createFreightTemplate(data) {
  return request({ url: '/shop/admin/freight-templates', method: 'post', data })
}

export function updateFreightTemplate(id, data) {
  return request({ url: `/shop/admin/freight-templates/${id}`, method: 'put', data })
}

export function listShopOrders(params) {
  return request({
    url: '/shop/admin/orders',
    method: 'get',
    params,
  })
}

export function getAdminOrderTracking(id) {
  return request({ url: `/shop/admin/orders/${id}/tracking`, method: 'get' })
}

export function updateShopOrderServiceRemark(id, serviceRemark) {
  return request({
    url: `/shop/admin/orders/${id}/service-remark`,
    method: 'put',
    data: { serviceRemark },
  })
}

export function getAdminOrderWorkSummary() {
  return request({
    url: '/shop/admin/orders/work-summary',
    method: 'get',
    silentError: true,
  })
}

export function getShopTradeDetail(tradeId) {
  return request({
    url: `/shop/admin/trades/${tradeId}`,
    method: 'get',
  })
}

export function exportShopOrders(params) {
  return request({
    url: '/shop/admin/orders/export',
    method: 'get',
    params,
    responseType: 'blob',
    timeout: 60000,
  })
}

export function downloadOrderShipmentTemplate(params) {
  return request({
    url: '/shop/admin/orders/shipment-template',
    method: 'get',
    params,
    responseType: 'blob',
    timeout: 60000,
  })
}

export function downloadOrderShipmentImportTemplate() {
  return request({
    url: '/shop/admin/orders/shipments/import-template',
    method: 'get',
    responseType: 'blob',
    timeout: 60000,
  })
}

export function importOrderShipments(file) {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: '/shop/admin/orders/shipments/import',
    method: 'post',
    data,
    timeout: 60000,
  })
}

export function listShopMembers(params) {
  return request({
    url: '/shop/admin/members',
    method: 'get',
    params,
  })
}

export function createShopMember(data) {
  return request({
    url: '/shop/admin/members',
    method: 'post',
    data,
  })
}

export function getShopMemberProfile(id) {
  return request({
    url: `/shop/admin/members/${id}/profile`,
    method: 'get',
  })
}

export function updateShopMemberStatus(id, status) {
  return request({
    url: `/shop/admin/members/${id}/status`,
    method: 'put',
    params: { status },
    adminStepUp: { message: '启用或停用会员会立即改变其登录状态。' },
  })
}

export function unlockShopMember(id) {
  return request({
    url: `/shop/admin/members/${id}/unlock`,
    method: 'put',
    adminStepUp: { message: '解除锁定会允许该会员重新尝试登录。' },
  })
}

export function unlockShopMemberPaymentPassword(id) {
  return request({
    url: `/shop/admin/members/${id}/payment-password/unlock`,
    method: 'put',
    adminStepUp: { message: '解除支付密码锁定属于资金安全操作。' },
  })
}

export function updateShopMemberPhone(id, data) {
  return request({
    url: `/shop/admin/members/${id}/phone`,
    method: 'put',
    data,
  })
}

export function resetShopMemberLoginPassword(id, data) {
  return request({
    url: `/shop/admin/members/${id}/login-password`,
    method: 'put',
    data,
  })
}

export function updateShopMemberLevel(id, data) {
  return request({
    url: `/shop/admin/members/${id}/level`,
    method: 'put',
    data,
    adminStepUp: { message: '调整会员卡级会影响其权益和奖金计算。' },
  })
}

export function shipShopOrder(id, data) {
  return request({
    url: `/shop/admin/orders/${id}/ship`,
    method: 'put',
    data,
  })
}

export function cancelShopOrder(id) {
  return request({
    url: `/shop/admin/orders/${id}/cancel`,
    method: 'put',
    adminStepUp: { message: '取消已付款订单可能触发退款和库存回补。' },
  })
}

export function manualRefundShopOrder(id, data) {
  return request({
    url: `/shop/admin/orders/${id}/refund`,
    method: 'post',
    data,
    adminStepUp: { message: '手工退款将改变订单资金状态，请核对退款金额。' },
  })
}

export function listShopAfterSales(params) {
  return request({
    url: '/shop/admin/after-sales',
    method: 'get',
    params,
  })
}

export function auditShopAfterSale(id, data) {
  return request({
    url: `/shop/admin/after-sales/${id}/audit`,
    method: 'put',
    data,
    adminStepUp: { message: '售后审核结果可能触发退款或关闭售后。' },
  })
}

export function confirmShopAfterSaleReturnReceived(id, data) {
  return request({
    url: `/shop/admin/after-sales/${id}/return-received`,
    method: 'put',
    data,
    adminStepUp: { message: '确认退货收货可能触发最终退款。' },
  })
}
