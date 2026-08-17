import request from '@/utils/request'

export function listAdminUsers(params) {
  return request({
    url: '/distribution/admin-users',
    method: 'get',
    params,
  })
}

export function saveAdminUser(data) {
  return request({
    url: '/distribution/admin-users',
    method: 'post',
    data,
  })
}

export function updateAdminUser(id, data) {
  return request({
    url: `/distribution/admin-users/${id}`,
    method: 'put',
    data,
  })
}

export function updateAdminPassword(id, data) {
  return request({
    url: `/distribution/admin-users/${id}/password`,
    method: 'put',
    data,
  })
}

export function updateAdminStatus(id, status) {
  return request({
    url: `/distribution/admin-users/${id}/status`,
    method: 'put',
    params: { status },
  })
}

export function unlockAdminUser(id) {
  return request({ url: `/distribution/admin-users/${id}/unlock`, method: 'put' })
}

export function listPermissionOptions() {
  return request({
    url: '/distribution/admin-users/permission-options',
    method: 'get',
  })
}

export function listAdminMerchantOptions() {
  return request({ url: '/distribution/admin-users/merchant-options', method: 'get' })
}
