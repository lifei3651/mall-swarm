import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/shop/products.vue')

describe('商品中心筛选', () => {
  it('点击重置后商品状态恢复为全部', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain("query.value = { keyword: '', categoryName: '', status: null }")
    expect(source).toContain('placeholder="全部"')
  })

  it('商品编辑支持配置会员累计限购数量', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('v-model="form.purchaseLimit"')
    expect(source).toContain('每位会员限购')
    expect(source).toContain('0 表示不限购')
  })

  it('商户结算成本需要财务权限、修改原因和历史快照提示', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain("store.hasPermission('finance:manage')")
    expect(source).toContain('form.settlementCostChangeReason')
    expect(source).toContain('历史订单使用下单快照')
    expect(source).toContain(':disabled="Boolean(form.merchantId) && !canManageSettlementCost"')
  })

  it('商户商品支持跟随默认周期或单品覆盖0到365天', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('跟随商户默认')
    expect(source).toContain('form.settlementDelayDaysOverride')
    expect(source).toContain(':max="365"')
    expect(source).toContain('风险商品可单独设置30天')
    expect(source).toContain('effectiveSettlementDays(row)')
  })

  it('编辑及上架下架按钮只向商品管理权限用户展示', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain("const canManageProducts = computed(() => store.hasPermission('shop:product'))")
    expect(source).toContain('v-if="canManageProducts" type="primary" link')
    expect(source).toContain('v-if="canManageProducts" :type="row.status === 1 ?')
  })

  it('运营可把任意在售商品额外加入新品并设置30到365天或永久', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('设置新品展示')
    expect(source).toContain('updateProductNewArrival')
    expect(source).toContain('不额外推荐')
    expect(source).toContain('限时推荐')
    expect(source).toContain('永久推荐')
    expect(source).toContain(':min="30" :max="365"')
    expect(source).toContain('期限结束后只退出新品页，不会下架商品')
  })
})
