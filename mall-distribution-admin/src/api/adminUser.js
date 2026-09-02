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

export function issueAdminTemporaryCredential(id, data) {
  return request({
    url: `/distribution/admin-users/${id}/temporary-credential`,
    method: 'post',
    data,
  })
}

export function updateAdminStatus(id, status) {
  return request({
    url: `/distribution/admin-users/${id}/status`,
    method: 'put',
    params: { status },
    adminStepUp: { message: '启用或停用管理员会立即改变其后台访问权限。' },
  })
}

export function unlockAdminUser(id) {
  return request({ url: `/distribution/admin-users/${id}/unlock`, method: 'put', adminStepUp: { message: '解除锁定会允许该管理员重新尝试登录。' } })
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
