const fields = {
  businessMode: '经营模式', agencyRuleDraft: '客户代理规则草稿',
  invitationEnabled: '邀请功能', defaultInviterEnabled: '默认绑定主账号', defaultInviterCode: '主账号邀请码',
  balanceTransactionsEnabled: '余额新交易', multiMerchantEnabled: '多商户新业务',
  promotionJoinMode: '推广资格开通方式', flashSaleEnabled: '秒杀专区', flashSaleBonusMode: '秒杀奖金处理',
  repurchaseMallEnabled: '复购区', repurchaseEligibilityMode: '复购进入资格', repurchaseBonusMode: '复购奖金处理',
  couponEnabled: '优惠券模块',
}
const labels = { NORMAL: '普通商城', AGENCY: '直销／代理模式', DISABLED: '关闭', AUTO_ON_INVITE: '受邀即开通', MANUAL_REVIEW: '后台审核', FIRST_PAID_ORDER: '首笔有效订单', NONE: '不计奖', STANDARD: '按渠道奖金规则', CUSTOM: '历史自定义状态', PAID_MEMBER: '已开通推广资格', AGENT: '代理及以上', ALL_MEMBER: '全部注册会员' }
const draftSummary = (value) => !value ? '未配置' : JSON.stringify(value)
const comparison = (key, value) => key === 'agencyRuleDraft' ? JSON.stringify(value || null) : String(value ?? '')
const display = (key, value) => key === 'agencyRuleDraft' ? draftSummary(value) : key.endsWith('Enabled') ? (Number(value) === 1 ? '开启' : '关闭') : labels[value] || String(value ?? '未配置')
export function businessModeChanges(before, after) {
  return Object.entries(fields).filter(([key]) => comparison(key, before[key]) !== comparison(key, after[key]))
    .map(([key, title]) => ({ key, title, before: display(key, before[key]), after: display(key, after[key]) }))
}
