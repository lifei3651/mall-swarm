import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/audit/operation-logs.vue')

describe('后台操作日志', () => {
  it('操作时间统一使用空格分隔日期和时间', async () => {
    const source = await readFile(sourcePath, 'utf8')

    expect(source).toContain('formatOperationTime(row.createTime)')
    expect(source).toContain('formatOperationTime(current.createTime)')
    expect(source).toContain("formatDateTime as formatOperationTime")
    expect(source).not.toContain('<el-table-column prop="createTime" label="操作时间"')
  })
})
