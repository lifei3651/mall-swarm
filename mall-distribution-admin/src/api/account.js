import request from '@/utils/request'

// 查询代理账户信息（按代理ID）
export function getAccountByAgentId(agentId) {
  return request({
    url: `/distribution/account/agent/${agentId}`,
    method: 'get',
  })
}

// 查询代理账户信息（按用户ID）
export function getAccountByUserId(userId) {
  return request({
    url: `/distribution/account/user/${userId}`,
    method: 'get',
  })
}
