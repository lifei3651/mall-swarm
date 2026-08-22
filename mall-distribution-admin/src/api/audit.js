import request from '@/utils/request'

export function getAuditSettings() {
  return request({
    url: '/distribution/audit/settings',
    method: 'get',
  })
}

export function updateVisibility(data) {
  return request({
    url: '/distribution/audit/settings/visibility',
    method: 'put',
    data,
  })
}

export function saveViewPermission(data) {
  return request({
    url: '/distribution/audit/settings/permissions',
    method: 'post',
    data,
    silentError: true,
  })
}

export function deleteViewPermission(id) {
  return request({
    url: `/distribution/audit/settings/permissions/${id}`,
    method: 'delete',
  })
}

export function listAuditOrders(params) {
  return request({
    url: '/distribution/audit/orders',
    method: 'get',
    params,
    silentError: Boolean(params?.memberKey || params?.orderNo),
  })
}

export function listBonusSources(params) {
  return request({
    url: '/distribution/audit/bonus-sources',
    method: 'get',
    params,
    silentError: Boolean(params?.memberKey || params?.orderNo),
  })
}

export function getPersonProfile(params) {
  return request({
    url: '/distribution/audit/person-profile',
    method: 'get',
    params,
    silentError: true,
  })
}

export function getOrderFinance(orderId) {
  return request({
    url: `/distribution/audit/orders/${orderId}/finance`,
    method: 'get',
  })
}

export function saveOrderFinance(orderId, data) {
  return request({
    url: `/distribution/audit/orders/${orderId}/finance`,
    method: 'put',
    data,
  })
}

export function saveCompanyShares(orderId, data) {
  return request({
    url: `/distribution/audit/orders/${orderId}/company-shares`,
    method: 'put',
    data,
  })
}

export function getFinanceSummary(params) {
  return request({
    url: '/distribution/audit/finance/summary',
    method: 'get',
    params,
  })
}

export function getFinanceDailySummary(params) {
  return request({
    url: '/distribution/audit/finance/daily',
    method: 'get',
    params,
  })
}

export function exportFinanceDailySummary(params) {
  return request({
    url: '/distribution/audit/finance/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

export function saveFinanceRefund(data) {
  return request({
    url: '/distribution/audit/finance/refunds',
    method: 'post',
    data,
  })
}

export function getCompanyShareSummary(params) {
  return request({
    url: '/distribution/audit/finance/company-shares/summary',
    method: 'get',
    params,
  })
}

export function listRiskRules() {
  return request({
    url: '/distribution/audit/finance/risk-rules',
    method: 'get',
  })
}

export function saveRiskRule(data) {
  return request({
    url: '/distribution/audit/finance/risk-rules',
    method: 'post',
    data,
    adminStepUp: { message: '修改财务风险规则会影响后续风险判断，请再次验证。' },
  })
}

export function getRiskAlerts(params) {
  return request({
    url: '/distribution/audit/finance/risk-alerts',
    method: 'get',
    params,
  })
}
