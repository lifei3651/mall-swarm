import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const read = (relative) => readFileSync(fileURLToPath(new URL(relative, import.meta.url)), 'utf8')

describe('团队、秒杀与复购后台配置', () => {
  it('新客户资格和特殊渠道默认关闭，客户奖金程序未接入时明确禁止对应渠道下单', () => {
    const source = read('../../src/views/tenant/business-modes.vue')
    expect(source).toContain("promotionJoinMode:'DISABLED'")
    expect(source).toContain('受邀即开通')
    expect(source).toContain('后台审核')
    expect(source).toContain('老商城兼容方式')
    expect(source).toContain("flashSaleEnabled:0")
    expect(source).toContain("repurchaseMallEnabled:0")
    expect(source).toContain('客户奖金程序（未接入禁下单）')
    expect(source).not.toContain('沿用普通奖金')
  })

  it('商品编辑提供普通与复购两个独立销售渠道', () => {
    const source = read('../../src/views/shop/products.vue')
    expect(source).toContain('form.normalSaleEnabled')
    expect(source).toContain('form.repurchaseSaleEnabled')
    expect(source).toContain('form.repurchasePurchaseLimit')
  })

  it('秒杀活动页说明并发保护并配置独立活动库存', () => {
    const source = read('../../src/views/shop/flash-sales.vue')
    expect(source).toContain('Redis原子抢占')
    expect(source).toContain('form.totalStock')
    expect(source).toContain('form.perUserLimit')
  })
})
