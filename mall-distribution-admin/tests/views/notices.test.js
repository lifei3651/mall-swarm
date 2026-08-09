import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const viewPath = resolve(process.cwd(), 'src/views/tenant/notices.vue')
const apiPath = resolve(process.cwd(), 'src/api/shop.js')
const routerPath = resolve(process.cwd(), 'src/router/index.js')

describe('商城公告管理', () => {
  it('提供新增、编辑、展示隐藏、排序和删除能力', async () => {
    const source = await readFile(viewPath, 'utf8')

    expect(source).toContain('新增公告')
    expect(source).toContain('编辑公告')
    expect(source).toContain('展示顺序')
    expect(source).toContain('updateShopNoticeStatus')
    expect(source).toContain('确认删除公告？')
    expect(source).toContain('await deleteShopNotice(row.id)')
  })

  it('接入真实公告接口并加入商城设置菜单', async () => {
    const [apiSource, routerSource] = await Promise.all([
      readFile(apiPath, 'utf8'),
      readFile(routerPath, 'utf8'),
    ])

    expect(apiSource).toContain("url: '/shop/admin/notices'")
    expect(apiSource).toContain('export function deleteShopNotice(id)')
    expect(routerSource).toContain("path: 'notices'")
    expect(routerSource).toContain("title: '商城公告'")
  })

  it('编辑历史公告时保留后台有效期字段', async () => {
    const source = await readFile(viewPath, 'utf8')

    expect(source).toContain('startTime: null, endTime: null')
    expect(source).toContain('form.value = { ...emptyForm(), ...row }')
    expect(source).not.toContain('delete payload.startTime')
    expect(source).not.toContain('delete payload.endTime')
  })
})
