import request from '@/utils/request'

// 审核提现
export function auditWithdraw(data) {
  return request({
    url: '/distribution/withdraw/audit',
    method: 'post',
    data,
    adminStepUp: { message: '提现审核会改变会员资金状态，请核对金额和结论。' },
  })
}

// 确认打款
export function confirmPay(id, data) {
  return request({
    url: `/distribution/withdraw/confirm-pay/${id}`,
    method: 'post',
    data,
  })
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
