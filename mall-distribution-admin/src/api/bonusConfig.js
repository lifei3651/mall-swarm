import request from '@/utils/request'

// 当前客户由服务端会话确定，不能从页面传入其他 tenantId。
export function getDirectReferralConfig() {
  return request({ url: '/distribution/bonus-config/direct-referral', method: 'get', silentError: true })
}

export function saveDirectReferralConfig(data) {
  return request({ url: '/distribution/bonus-config/direct-referral', method: 'put', data, silentError: true })
}

export function getDisplayConfig(tenantId) {
  return request({
    url: `/distribution/bonus-config/display/${tenantId}`,
    method: 'get',
  })
}

export function saveDisplayConfig(tenantId, data) {
  return request({
    url: `/distribution/bonus-config/display/${tenantId}`,
    method: 'put',
    data,
  })
}

export function listProductPvConfigs(params) {
  return request({
    url: '/distribution/bonus-config/pv/products',
    method: 'get',
    params,
  })
}

export function saveProductPvConfig(data) {
  return request({
    url: '/distribution/bonus-config/pv/products',
    method: 'post',
    data,
  })
}

export function updateProductPvStatus(id, status) {
  return request({
    url: `/distribution/bonus-config/pv/products/${id}/status`,
    method: 'put',
    params: { status },
  })
}

export function deleteProductPvConfig(id) {
  return request({
    url: `/distribution/bonus-config/pv/products/${id}`,
    method: 'delete',
  })
}

export function listOrderPvDetails(orderId) {
  return request({
    url: `/distribution/bonus-config/pv/orders/${orderId}`,
    method: 'get',
  })
}

export function listCalculationSnapshots(orderId) {
  return request({
    url: `/distribution/bonus-config/snapshots/orders/${orderId}`,
    method: 'get',
  })
}

export function simulateBonus(data) {
  return request({
    url: '/distribution/bonus-config/simulate',
    method: 'post',
    data,
    silentError: true,
  })
}
