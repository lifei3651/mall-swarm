import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const reviewPagePath = resolve(process.cwd(), 'src/views/shop/reviews.vue')

describe('商品评价管理', () => {
  it('商品筛选项遵守服务端每页最多 100 条并自动加载后续页', async () => {
    const source = await readFile(reviewPagePath, 'utf8')

    expect(source).toContain('const PRODUCT_OPTION_PAGE_SIZE = 100')
    expect(source).toContain('pageSize: PRODUCT_OPTION_PAGE_SIZE')
    expect(source).toContain('loadedProducts.length < total')
    expect(source).not.toContain('pageSize: 200')
  })

  it('评价列表继续使用页面自己的分页参数', async () => {
    const source = await readFile(reviewPagePath, 'utf8')

    expect(source).toContain('pageSize: pagination.value.size')
    expect(source).toContain('v-model:page-size="pagination.size"')
  })
})
