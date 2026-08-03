import request from '@/utils/request'

// 查询代理的业绩概览
export function getPerformanceOverview(agentId, startDate, endDate) {
  return request({
    url: `/distribution/performance/overview/${encodeURIComponent(agentId)}`,
    method: 'get',
    params: { startDate, endDate },
    silentError: true,
  })
}

// 查询代理的团队成员贡献列表
export function getSubordinateContributions(agentId, startDate, endDate) {
  return request({
    url: `/distribution/performance/contributions/${encodeURIComponent(agentId)}`,
    method: 'get',
    params: { startDate, endDate },
    silentError: true,
  })
}

// 查询某个下属贡献的具体订单明细
export function getSubordinateOrderDetails(agentId, subordinateAgentId, startDate, endDate) {
  return request({
    url: `/distribution/performance/contributions/${encodeURIComponent(agentId)}/details/${subordinateAgentId}`,
    method: 'get',
    params: { startDate, endDate },
    silentError: true,
  })
}

// 查询某个代理全部业绩来源明细（个人订单、团队订单、退款冲正）
export function getPerformanceSourceDetails(agentId, startDate, endDate) {
  return request({
    url: `/distribution/performance/sources/${encodeURIComponent(agentId)}`,
    method: 'get',
    params: { startDate, endDate },
    silentError: true,
  })
}

// 查询业绩排行榜
export function getPerformanceRanking(params) {
  return request({
    url: '/distribution/performance/ranking',
    method: 'get',
    params,
  })
}

// 刷新日业绩汇总
export function refreshDailySummary(statDate) {
  return request({
    url: '/distribution/performance/refresh/daily',
    method: 'post',
    params: { statDate },
  })
}

// 刷新月业绩汇总
export function refreshMonthlySummary(statDate) {
  return request({
    url: '/distribution/performance/refresh/monthly',
    method: 'post',
    params: { statDate },
  })
}
