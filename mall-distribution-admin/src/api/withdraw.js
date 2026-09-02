import request from '@/utils/request'

// 审核提现
export function auditWithdraw(data) {
  return request({
    url: '/distribution/withdraw/audit',
    method: 'post',
    data,
  })
}

// 发起官方渠道打款
export function startWithdrawalPayout(id) {
  return request({
    url: `/distribution/withdraw/${id}/payout/start`,
    method: 'post',
  })
}

// 核对官方渠道结果
export function reconcileWithdrawalPayout(id) {
  return request({
    url: `/distribution/withdraw/${id}/payout/reconcile`,
    method: 'post',
  })
}

export function getWithdrawalPayout(id) {
  return request({ url: `/distribution/withdraw/${id}/payout`, method: 'get', silentError: true })
}

// 查询提现记录
export function getWithdrawById(id) {
  return request({
    url: `/distribution/withdraw/${id}`,
    method: 'get',
  })
}

// 查询代理的提现记录
export function getWithdrawsByAgentId(agentId) {
  return request({
    url: `/distribution/withdraw/agent/${agentId}`,
    method: 'get',
  })
}

// 查询待审核的提现记录
export function getPendingAuditWithdraws() {
  return request({
    url: '/distribution/withdraw/pending-audit',
    method: 'get',
  })
}

// 查询所有提现记录
export function getAllWithdraws() {
  return request({
    url: '/distribution/withdraw/all',
    method: 'get',
  })
}

// 按条件查询提现记录
export function listWithdraws(params) {
  return request({
    url: '/distribution/withdraw/list',
    method: 'get',
    params,
    silentError: Boolean(params?.memberKey),
  })
}

// 查询提现统计
export function getWithdrawStats(params) {
  return request({
    url: '/distribution/withdraw/stats',
    method: 'get',
    params,
    silentError: Boolean(params?.memberKey),
  })
}
