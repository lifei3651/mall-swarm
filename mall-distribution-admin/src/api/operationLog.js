import request from '@/utils/request'

export function listOperationLogs(params) {
  return request({
    url: '/distribution/operation-logs',
    method: 'get',
    params,
  })
}
