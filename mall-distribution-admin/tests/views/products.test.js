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
})
