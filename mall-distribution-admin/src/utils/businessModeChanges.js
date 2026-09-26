const fields = {
  promotionJoinMode: '推广资格开通方式', flashSaleEnabled: '秒杀专区', flashSaleBonusMode: '秒杀奖金处理',
  repurchaseMallEnabled: '复购区', repurchaseEligibilityMode: '复购进入资格', repurchaseBonusMode: '复购奖金处理',
  couponEnabled: '优惠券模块',
}
const labels = { DISABLED: '关闭', AUTO_ON_INVITE: '受邀即开通', MANUAL_REVIEW: '后台审核', FIRST_PAID_ORDER: '首笔有效订单', NONE: '不计奖', STANDARD: '按渠道奖金规则', CUSTOM: '历史自定义状态', PAID_MEMBER: '已开通推广资格', AGENT: '代理及以上', ALL_MEMBER: '全部注册会员' }
const display = (key, value) => key.endsWith('Enabled') ? (Number(value) === 1 ? '开启' : '关闭') : labels[value] || String(value ?? '未配置')
export function businessModeChanges(before, after) {
  return Object.entries(fields).filter(([key]) => String(before[key] ?? '') !== String(after[key] ?? ''))
    .map(([key, title]) => ({ key, title, before: display(key, before[key]), after: display(key, after[key]) }))
}
