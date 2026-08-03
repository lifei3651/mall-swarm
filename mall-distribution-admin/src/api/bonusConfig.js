import request from '@/utils/request'

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
