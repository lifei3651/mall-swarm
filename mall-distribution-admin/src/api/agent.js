import request from '@/utils/request'

// 查询代理列表
export function listAgents(params) {
  return request({
    url: '/distribution/agent/list',
    method: 'get',
    params,
  })
}

export function exportAgents(params) {
  return request({
    url: '/distribution/agent/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

// 查询所有无上级的根代理，关系树从左侧菜单进入时自动加载
export function getRootAgents() {
  return request({
    url: '/distribution/agent/roots',
    method: 'get',
  })
}

// 按登录账号或手机号查询关系身份。
export function resolveAgent(memberKey) {
  return request({
    url: `/distribution/agent/resolve/${encodeURIComponent(memberKey)}`,
    method: 'get',
    silentError: true,
  })
}

// 代理注册
export function registerAgent(data) {
  return request({
    url: '/distribution/agent/register',
    method: 'post',
    data,
  })
}

// 更新代理状态
export function updateAgentStatus(id, status) {
  return request({
    url: `/distribution/agent/${id}/status`,
    method: 'put',
    params: { status },
  })
}

// 后台直接调整会员卡级（不需要审批，保留变更日志）
export function adjustAgentLevel(id, data) {
  return request({
    url: `/distribution/agent/${id}/level`,
    method: 'put',
    data,
  })
}

// 根据ID查询代理
export function getAgentById(id) {
  return request({
    url: `/distribution/agent/${id}`,
    method: 'get',
  })
}

// 根据用户ID查询代理
export function getAgentByUserId(userId) {
  return request({
    url: `/distribution/agent/user/${userId}`,
    method: 'get',
  })
}

// 根据代理编号查询代理
export function getAgentByCode(agentCode) {
  return request({
    url: `/distribution/agent/code/${agentCode}`,
    method: 'get',
  })
}

// 查询下级代理列表
export function getChildrenAgents(parentId) {
  return request({
    url: `/distribution/agent/children/${parentId}`,
    method: 'get',
  })
}

// 查询所有下级代理
export function getAllDescendants(agentId) {
  return request({
    url: `/distribution/agent/descendants/${agentId}`,
    method: 'get',
  })
}

// 代理移线（具备专用权限时提交后直接生效）
export function switchLine(data) {
  return request({
    url: '/distribution/agent/switch-line',
    method: 'post',
    data,
    adminStepUp: { message: '会员移线会改变整个团队关系和后续奖金归属。' },
  })
}

export function listLineChangeApplications(params) {
  return request({ url: '/distribution/agent/line-change-applications', method: 'get', params })
}

export function auditLineChangeApplication(id, data) {
  return request({ url: `/distribution/agent/line-change-applications/${id}/audit`, method: 'post', data, adminStepUp: { message: '审核移线申请会改变团队关系和后续奖金归属。' } })
}

// 生成推广二维码
export function generateQrCode(agentId) {
  return request({
    url: `/distribution/agent/qrcode/${agentId}`,
    method: 'get',
  })
}

// 查询团队成员数
export function getTeamMemberCount(agentId) {
  return request({
    url: `/distribution/agent/team-count/${agentId}`,
    method: 'get',
  })
}

// 查询各层级团队成员数
export function getLevelMemberCounts(agentId) {
  return request({
    url: `/distribution/agent/level-counts/${agentId}`,
    method: 'get',
  })
}
