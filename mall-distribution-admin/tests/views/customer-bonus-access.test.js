import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { customerBonusName } from '../../src/utils/customerBonus.js'

const read = (relative) => readFileSync(fileURLToPath(new URL(relative, import.meta.url)), 'utf8')

describe('客户奖金接入页', () => {
  it('通用基座不再展示固定奖金制度、比例或等级条件', () => {
    const source = read('../../src/views/tenant/bonus-config.vue')
    expect(source).toContain('客户奖金接入')
    expect(source).toContain('奖金未接入 · 安全关闭')
    expect(source).toContain('历史演示制度 · 待替换')
    expect(source).not.toContain('新零售正式方案')
    expect(source).not.toContain('直推奖比例')
    expect(source).not.toContain('79%')
  })

  it('奖金记录支持客户自定义类型，而不是把未知类型误写成历史奖金', () => {
    expect(customerBonusName({ bonusType: 'REPURCHASE_REWARD', remark: '客户复购奖励；订单A' })).toBe('客户复购奖励')
    expect(customerBonusName({ bonusType: 'REGION_SHARE' })).toBe('REGION_SHARE')
    expect(customerBonusName({ bonusType: 'DIRECT_REWARD' })).toBe('历史示例·直推奖')
    const source = read('../../src/views/commission/records.vue')
    expect(source).toContain('奖金类型代码')
    expect(source).not.toContain('<el-option label="直推奖"')
  })
})
