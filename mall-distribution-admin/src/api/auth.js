import request from '@/utils/request'

export function login(data) {
  return request({
    url: '/distribution/admin-auth/login',
    method: 'post',
    data,
  })
}

export function getLoginCaptcha() {
  return request({ url: '/captcha', method: 'get', params: { scene: 'admin' } })
}

export function getMe(options = {}) {
  return request({
    url: '/distribution/admin-auth/me',
    method: 'get',
    silentError: Boolean(options.silentError),
  })
}

export function changeOwnPassword(data) {
  return request({
    url: '/distribution/admin-auth/password',
    method: 'put',
    data,
  })
}

export function logout() {
  return request({
    url: '/distribution/admin-auth/logout',
    method: 'post',
  })
}
