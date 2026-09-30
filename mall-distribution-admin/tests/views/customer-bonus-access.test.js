import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { customerBonusName } from '../../src/utils/customerBonus.js'

const read = (relative) => readFileSync(fileURLToPath(new URL(relative, import.meta.url)), 'utf8')

describe('客户奖金接入页', () => {
  it('通用基座支持独立直接推荐配置，历史及定制制度仍明确区分', () => {
    const source = read('../../src/views/tenant/bonus-config.vue')
    expect(source).toContain('客户奖金接入')
    expect(source).toContain('奖金未接入 · 安全关闭')
    expect(source).toContain('历史演示制度 · 待替换')
    expect(source).toContain('基座直接推荐成交佣金')
    expect(source).toContain('B 保持普通客户')
    expect(source).toContain('配置并切换到基座佣金')
    expect(source).not.toContain('新零售正式方案')
    expect(source).not.toContain('直推奖比例')
    expect(source).not.toContain('79%')
  })

  it('奖金记录支持客户自定义类型，而不是把未知类型误写成历史奖金', () => {
    expect(customerBonusName({ bonusType: 'REPURCHASE_REWARD', remark: '客户复购奖励；订单A' })).toBe('客户复购奖励')
    expect(customerBonusName({ bonusType: 'REGION_SHARE' })).toBe('REGION_SHARE')
    expect(customerBonusName({ bonusType: 'DIRECT_REWARD' })).toBe('历史示例·直推奖')
    expect(customerBonusName({ bonusType: 'DIRECT_REFERRAL', remark: '订单A' })).toBe('直接推荐佣金')
    const source = read('../../src/views/commission/records.vue')
    expect(source).toContain('奖金类型代码')
    expect(source).not.toContain('<el-option label="直推奖"')
  })
})
