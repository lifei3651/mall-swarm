import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const reviewPagePath = resolve(process.cwd(), 'src/views/shop/merchant-product-reviews.vue')
const productPagePath = resolve(process.cwd(), 'src/views/shop/products.vue')

describe('商户商品审核闭环', () => {
  it('审核页明确展示销售价、结算价、通过上架和驳回修改', async () => {
    const source = await readFile(reviewPagePath, 'utf8')

    expect(source).toContain('结算价是平台应付给商户的单件货款')
    expect(source).toContain('通过并上架')
    expect(source).toContain('驳回修改')
    expect(source).toContain('审核通过，商品已自动上架')
    expect(source).toContain('请填写驳回原因')
  })

  it('商品页要求先下架再改价并说明新老订单的结算口径', async () => {
    const source = await readFile(productPagePath, 'utf8')

    expect(source).toContain('修改销售价或结算价前必须先下架')
    expect(source).toContain('历史订单使用下单快照，新订单使用审核后的最新结算价')
    expect(source).toContain('保存为下架草稿，审核通过后自动上架')
  })
})
