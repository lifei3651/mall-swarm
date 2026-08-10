import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/shop/members.vue')

describe('会员全景直属邀请人信息', () => {
  it('只展示邀请人会员名称和手机号，不展示邀请人登录账号', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('label="邀请人会员名称"')
    expect(source).toContain('label="邀请人手机号"')
    expect(source).toContain("手机号：{{ row.inviterPhone || '未设置' }}")
    expect(source).not.toContain('label="邀请人登录账号"')
    expect(source).not.toContain('账号：${account}')
  })

  it('订单区只表达有效订单并准确区分已售后与已关闭', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('<template #header>有效订单</template>')
    expect(source).toContain("hasAfterSale(row) ? '已售后' : '已关闭'")
    expect(source).not.toContain("4: '售后关闭'")
  })
})
