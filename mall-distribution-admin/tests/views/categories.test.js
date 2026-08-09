import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const viewPath = resolve(process.cwd(), 'src/views/shop/categories.vue')
const apiPath = resolve(process.cwd(), 'src/api/shop.js')

describe('商品分类删除', () => {
  it('提供二次确认的删除入口并在成功后刷新列表', async () => {
    const source = await readFile(viewPath, 'utf8')

    expect(source).toContain('@click="removeCategory(row)"')
    expect(source).toContain('确认删除分类？')
    expect(source).toContain('await deleteShopCategory(row.id)')
    expect(source).toContain("ElMessage.success('分类已删除')")
    expect(source).toContain('await loadCategories()')
  })

  it('调用后台分类删除接口', async () => {
    const source = await readFile(apiPath, 'utf8')

    expect(source).toContain('export function deleteShopCategory(id)')
    expect(source).toContain("method: 'delete'")
  })

  it('分类图标上传使用浏览器边界并处理成功与失败回调', async () => {
    const source = await readFile(viewPath, 'utf8')
    const apiSource = await readFile(apiPath, 'utf8')
    const uploadApi = apiSource.slice(
      apiSource.indexOf('export function uploadShopImage'),
      apiSource.indexOf('export function getProductSettings'),
    )

    expect(uploadApi).toContain('data })')
    expect(uploadApi).not.toContain('headers: {')
    expect(uploadApi).toContain('不手动设置 Content-Type')
    expect(source).toContain('onSuccess?.({ url }, file)')
    expect(source).toContain('onError?.(error)')
    expect(source).toContain('normalizeMediaUrl')
    expect(source).toContain('分类图标仅支持 JPG、PNG、WEBP 或 GIF 格式')
  })
})
