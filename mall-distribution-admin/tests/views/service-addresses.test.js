import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const sourcePath = resolve(process.cwd(), 'src/views/shop/service-addresses.vue')

describe('发货与退货地址', () => {
  it('联系电话只接收手机号或座机号码', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('inputmode="tel"')
    expect(source).toContain('@input="normalizePhone"')
    expect(source).toContain("replace(/[^0-9-]/g, '')")
    expect(source).toContain('请填写正确的手机号或座机号码')
  })

  it('省市区选择值属于表单模型并能通过地区校验', async () => {
    const source = await readFile(sourcePath, 'utf8')
    expect(source).toContain('label="所在地区" prop="region" required')
    expect(source).toContain("region: [{ required: true, validator:")
    expect(source).toContain('请选择完整省、市、区/县')
    expect(source).toContain('v-model="form.region"')
    expect(source).toContain('region: []')
    expect(source).toContain('province: form.region[0]')
    expect(source).not.toContain('const region = ref([])')
  })
})
