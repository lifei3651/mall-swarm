import request from '@/utils/request'

export function listTenants(params) {
  return request({
    url: '/distribution/tenant/list',
    method: 'get',
    params,
  })
}

export function saveTenant(data, options = {}) {
  return request({
    url: '/distribution/tenant',
    method: 'post',
    data,
    ...options,
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

export function saveDisplayConfig(data, options = {}) {
  return request({
    url: '/distribution/tenant/display-config',
    method: 'post',
    data,
    ...options,
  })
}

export function listTenantConfigVersions(tenantId) {
  return request({
    url: `/distribution/tenant/${tenantId}/config-versions`,
    method: 'get',
  })
}

export function restoreTenantConfigVersion(tenantId, versionId) {
  return request({
    url: `/distribution/tenant/${tenantId}/config-versions/${versionId}/restore`,
    method: 'post',
  })
}
