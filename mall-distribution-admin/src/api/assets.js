import request from '@/utils/request'

export function listAssetAccounts(params) {
  return request({
    url: '/distribution/assets/accounts',
    method: 'get',
    params,
  })
}

export function listAssetFlows(params) {
  return request({
    url: '/distribution/assets/flows',
    method: 'get',
    params,
  })
}

export function listBalanceFlowRecords(params) {
  return request({
    url: '/distribution/assets/flow-records',
    method: 'get',
    params,
  })
}

export function issueAsset(data) {
  return request({
    url: '/distribution/assets/issue',
    method: 'post',
    data,
  })
}

export function deductAsset(data) {
  return request({
    url: '/distribution/assets/deduct',
    method: 'post',
    data,
  })
}
