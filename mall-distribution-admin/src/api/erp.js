import request from '@/utils/request'

export const listErpIntegrations = () => request({ url: '/distribution/erp/integrations', method: 'get', params: { tenantId: 1 } })
export const saveErpIntegration = (data) => request({ url: '/distribution/erp/integrations', method: 'post', data })
export const listErpTasks = (params) => request({ url: '/distribution/erp/tasks', method: 'get', params })
export const retryErpTask = (id) => request({ url: `/distribution/erp/tasks/${id}/retry`, method: 'post' })
