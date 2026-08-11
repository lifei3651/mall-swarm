import request from '@/utils/request'

export function getDashboard() {
  return request({
    url: '/distribution/dashboard',
    method: 'get',
  })
}

export function exportDashboard() {
  return request({
    url: '/distribution/dashboard/export',
    method: 'get',
    responseType: 'blob',
  })
}
