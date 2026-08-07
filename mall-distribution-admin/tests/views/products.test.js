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
})
