import request from '@/utils/request'

export function migrateExternalTeam(file, anchorAgentId) {
  const formData = new FormData()
  formData.append('file', file)
  if (anchorAgentId) formData.append('anchorAgentId', anchorAgentId)
  return request({
    url: '/distribution/import/external-team/file',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 批量导入代理（Excel文件）
export function importAgentsByFile(file, operatorId, operatorName) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('operatorId', operatorId)
  formData.append('operatorName', operatorName)
  return request({
    url: '/distribution/import/agents/file',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 批量导入代理（数据列表）
export function importAgentsByList(agentList, operatorId, operatorName) {
  return request({
    url: '/distribution/import/agents/list',
    method: 'post',
    data: agentList,
    params: { operatorId, operatorName },
  })
}

// 批量导入订单（Excel文件）
export function importOrdersByFile(file, operatorId, operatorName) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('operatorId', operatorId)
  formData.append('operatorName', operatorName)
  return request({
    url: '/distribution/import/orders/file',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 批量导入订单（数据列表）
export function importOrdersByList(orderList, operatorId, operatorName) {
  return request({
    url: '/distribution/import/orders/list',
    method: 'post',
    data: orderList,
    params: { operatorId, operatorName },
  })
}

// 查询导入批次详情
export function getImportResult(batchNo) {
  return request({
    url: `/distribution/import/result/${batchNo}`,
    method: 'get',
  })
}
