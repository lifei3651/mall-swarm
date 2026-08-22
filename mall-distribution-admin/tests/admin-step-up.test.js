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

  it('账号、关系、退款、提现、佣金、批量导入和配置恢复均声明二次验证', () => {
    const files = [
      'src/api/adminUser.js', 'src/api/shop.js', 'src/api/agent.js', 'src/api/merchant.js',
      'src/api/withdraw.js', 'src/api/commission.js', 'src/api/import.js', 'src/api/tenant.js',
    ].map(source).join('\n')
    const protectedMarkers = files.match(/adminStepUp:/g) || []
    expect(protectedMarkers.length).toBeGreaterThanOrEqual(18)
    expect(files).toContain('/distribution/import/external-team/file')
    expect(files).toContain('/config-versions/${versionId}/restore')
  })
})
