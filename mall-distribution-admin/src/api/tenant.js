import request from '@/utils/request'

export function listTenants(params) {
  return request({
    url: '/distribution/tenant/list',
    method: 'get',
    params,
  })
}

export function saveTenant(data) {
  return request({
    url: '/distribution/tenant',
    method: 'post',
    data,
  })
}

export function getLegalTemplates() {
  return request({
    url: '/distribution/tenant/legal-templates',
    method: 'get',
  })
}

export function updateTenantStatus(id, status) {
  return request({
    url: `/distribution/tenant/${id}/status`,
    method: 'put',
    params: { status },
  })
}

export function listRuleVersions(tenantId) {
  return request({
    url: `/distribution/tenant/${tenantId}/rule-versions`,
    method: 'get',
  })
}

export function getDisplayConfig(tenantId) {
  return request({
    url: `/distribution/tenant/${tenantId}/display-config`,
    method: 'get',
  })
}

export function saveDisplayConfig(data) {
  return request({
    url: '/distribution/tenant/display-config',
    method: 'post',
    data,
  })
}
