const LEGACY_BONUS_NAMES = {
  DIRECT_REWARD: '历史示例·直推奖',
  DIRECTOR_SHARE: '历史示例·团队分红',
}

const firstRemarkSegment = (remark) => String(remark || '')
  .split(/[；;]/)[0]
  .trim()

export const customerBonusName = (row = {}, { includeLevel = false } = {}) => {
  const bonusType = String(row.bonusType || '').trim()
  if (bonusType === 'DIRECTOR_SHARE' && includeLevel && row.commissionLevel) {
    return `${LEGACY_BONUS_NAMES[bonusType]}（第${row.commissionLevel}层）`
  }
  if (LEGACY_BONUS_NAMES[bonusType]) return LEGACY_BONUS_NAMES[bonusType]
  return firstRemarkSegment(row.remark) || bonusType || '客户奖金'
}
