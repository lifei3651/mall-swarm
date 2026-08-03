import request from '@/utils/request'

export function getDashboard() {
  return request({
    url: '/distribution/dashboard',
    method: 'get',
  })
}
