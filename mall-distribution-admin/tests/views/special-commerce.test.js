import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const read = (relative) => readFileSync(fileURLToPath(new URL(relative, import.meta.url)), 'utf8')

describe('团队、秒杀与复购后台配置', () => {
  it('新客户资格和特殊渠道默认关闭，奖金处理只按现有渠道规则选择', () => {
    const source = read('../../src/views/tenant/business-modes.vue')
    expect(source).toContain("promotionJoinMode:'DISABLED'")
    expect(source).toContain('受邀即开通')
    expect(source).toContain('后台审核')
    expect(source).toContain('老商城兼容方式')
    expect(source).toContain("flashSaleEnabled:0")
    expect(source).toContain("repurchaseMallEnabled:0")
    expect(source).toContain('按报单区奖金规则')
    expect(source).toContain('按复购奖金规则')
    expect(source).not.toContain('value="CUSTOM"')
  })

  it('商品编辑只提供报单区与复购区并关闭历史第三渠道', () => {
    const source = read('../../src/views/shop/products.vue')
    expect(source).toContain('form.normalSaleEnabled')
    expect(source).toContain('form.repurchaseSaleEnabled')
    expect(source).toContain('form.repurchasePurchaseLimit')
    expect(source).toContain('<el-form-item label="报单区"><el-switch v-model="form.normalSaleEnabled"')
    expect(source).toContain('<el-form-item label="复购区"><el-switch v-model="form.repurchaseSaleEnabled"')
    expect(source).not.toContain('v-model="form.enrollmentSaleEnabled"')
    expect(source).not.toContain('label="客户奖金处理"')
    expect(source).toContain('form.value.enrollmentSaleEnabled = 0')
  })

  it('秒杀活动页说明并发保护并配置独立活动库存', () => {
    const source = read('../../src/views/shop/flash-sales.vue')
    expect(source).toContain('Redis原子抢占')
    expect(source).toContain('form.totalStock')
    expect(source).toContain('form.perUserLimit')
  })
})
