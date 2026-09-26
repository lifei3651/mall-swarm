import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const source = (path) => readFile(resolve(process.cwd(), path), 'utf8')

describe('商户资料最小权限', () => {
  it('仅商户负责人看到经营与结算资料，商品页面改用安全选项', async () => {
    const router = await source('src/router/index.js')
    const layout = await source('src/components/Layout.vue')
    const products = await source('src/views/shop/products.vue')
    const api = await source('src/api/merchant.js')

    expect(router).toMatch(/path: 'merchant\/profile',[\s\S]*?permission: 'merchant:staff-manage'/)
    expect(layout).toContain("path: '/merchant/profile', permission: 'merchant:staff-manage'")
    expect(products).toContain('listMerchantOptions({ status: 1 })')
    expect(products).not.toContain('listMerchants(')
    expect(api).toContain("url: '/distribution/merchants/options'")
  })

  it('财务选商户依赖财务授权账户列表，不读取完整入驻资料', async () => {
    const finance = await source('src/views/audit/merchant-finance.vue')
    expect(finance).toContain('merchants.value = accounts.value.map')
    expect(finance).not.toContain('listMerchants(')
  })
})
