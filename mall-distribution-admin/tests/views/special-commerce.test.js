import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const read = (relative) => readFileSync(fileURLToPath(new URL(relative, import.meta.url)), 'utf8')

describe('秒杀与复购后台配置', () => {
  it('默认关闭，并明确区分无奖金、标准奖金和客户定制', () => {
    const source = read('../../src/views/tenant/business-modes.vue')
    expect(source).toContain("flashSaleEnabled:0")
    expect(source).toContain("repurchaseMallEnabled:0")
    expect(source).toContain('客户定制（未配置禁下单）')
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
