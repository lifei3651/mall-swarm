import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const source = (relative) => readFileSync(resolve(process.cwd(), relative), 'utf8')

describe('后台敏感操作二次验证', () => {
  it('由请求层统一获取一次性服务端凭证且不持久化密码', () => {
    const request = source('src/utils/request.js')
    const helper = source('src/utils/adminStepUp.js')
    expect(request).toContain("X-Admin-Step-Up-Token")
    expect(request).toContain('requestAdminStepUpToken')
    expect(helper).toContain("inputType: 'password'")
    expect(helper).toContain('encryptSensitiveRequest')
    expect(helper).toContain("password = ''")
    expect(helper).not.toMatch(/localStorage|sessionStorage/)
  })

  it('仅管理员账号安全和会员换线声明统一二次验证', () => {
    const accountApi = source('src/api/adminUser.js')
    const lineApi = source('src/api/agent.js')
    const businessApis = [
      'src/api/shop.js', 'src/api/merchant.js', 'src/api/withdraw.js', 'src/api/commission.js',
      'src/api/import.js', 'src/api/tenant.js', 'src/api/audit.js',
    ].map(source).join('\n')
    const allApis = `${accountApi}\n${lineApi}\n${businessApis}`

    expect((allApis.match(/adminStepUp:/g) || [])).toHaveLength(4)
    expect((accountApi.match(/adminStepUp:/g) || [])).toHaveLength(2)
    expect((lineApi.match(/adminStepUp:/g) || [])).toHaveLength(2)
    expect(businessApis).not.toContain('adminStepUp')
  })

  it('订单和售后全流程使用业务确认，不重复验证登录密码', () => {
    const shopApi = source('src/api/shop.js')
    const auditRequest = shopApi.match(/export function auditShopAfterSale[\s\S]*?\n}\n/)?.[0] || ''
    const returnReceivedRequest = shopApi.match(/export function confirmShopAfterSaleReturnReceived[\s\S]*?\n}\n/)?.[0] || ''

    expect(auditRequest).toContain('/after-sales/${id}/audit')
    expect(auditRequest).not.toContain('adminStepUp')
    expect(returnReceivedRequest).toContain('/after-sales/${id}/return-received')
    expect(returnReceivedRequest).not.toContain('adminStepUp')
  })

  it('人工增减余额和账号安全操作仍保留管理员密码保护', () => {
    const memberView = source('src/views/shop/members.vue')
    const adminUserView = source('src/views/system/admin-users.vue')

    expect(memberView).toContain('v-model="assetForm.adminPassword"')
    expect(memberView).toContain('v-model="phoneForm.adminPassword"')
    expect(memberView).toContain('v-model="passwordForm.adminPassword"')
    expect(adminUserView).toContain('v-model="form.currentAdminPassword"')
    expect(adminUserView).toContain("normalized.has('shop:aftersale')")
    expect(adminUserView).toContain("normalized.add('shop:order')")
    expect(adminUserView).toContain("normalized.has('finance:manage')")
    expect(adminUserView).toContain("normalized.add('finance:read')")
  })
})
