import request from '@/utils/request'

// 查询异步计算任务
export function listCalculationTasks(params) {
  return request({
    url: '/distribution/commission/calculation-tasks',
    method: 'get',
    params,
  })
}

// 手动处理一批异步计算任务
export function processCalculationTasks(limit = 20) {
  return request({
    url: '/distribution/commission/calculation-tasks/process',
    method: 'post',
    params: { limit },
  })
}

// 手动处理指定异步计算任务
export function processCalculationTask(taskId) {
  return request({
    url: `/distribution/commission/calculation-tasks/${taskId}/process`,
    method: 'post',
  })
}

// 结算佣金
export function settleCommission(recordId) {
  return request({
    url: `/distribution/commission/settle/${recordId}`,
    method: 'post',
    adminStepUp: { message: '佣金结算会改变会员资金余额。' },
  })
}

// 批量结算佣金
export function settleCommissionBatch(recordIds) {
  return request({
    url: '/distribution/commission/settle-batch',
    method: 'post',
    data: recordIds,
    adminStepUp: { message: '批量佣金结算会同时改变多笔会员资金余额。' },
  })
}

export function createSettlementBatch(data) {
  return request({ url: '/distribution/commission/settlement-batches', method: 'post', data, adminStepUp: { message: '创建结算批次会锁定所选佣金记录。' } })
}

export function listSettlementBatches(params) {
  return request({ url: '/distribution/commission/settlement-batches', method: 'get', params })
}

export function executeSettlementBatch(id) {
  return request({ url: `/distribution/commission/settlement-batches/${id}/execute`, method: 'post', adminStepUp: { message: '执行结算批次会改变多笔会员资金余额。' } })
}

// 取消佣金
export function cancelCommission(recordId, cancelReason) {
  return request({
    url: `/distribution/commission/cancel/${recordId}`,
    method: 'post',
    params: { cancelReason },
    adminStepUp: { message: '取消佣金会改变会员待结算权益。' },
  })
}

// 查询佣金记录
export function getCommissionRecords(params) {
  return request({
    url: '/distribution/commission/records',
    method: 'get',
    params,
    silentError: Boolean(params?.memberKey),
  })
}

// 查询待结算佣金总额
export function getUnsettledAmount(agentId) {
  return request({
    url: `/distribution/commission/unsettled/${agentId}`,
    method: 'get',
  })
}

// 查询已结算佣金总额
export function getSettledAmount(agentId) {
  return request({
    url: `/distribution/commission/settled/${agentId}`,
    method: 'get',
  })
}
