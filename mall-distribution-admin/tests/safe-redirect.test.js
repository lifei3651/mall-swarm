import { describe, expect, it } from 'vitest'
import { safeAdminRedirect } from '@/utils/safeRedirect'

describe('后台登录跳转边界', () => {
  it('保留合法站内路由及查询参数', () => {
    expect(safeAdminRedirect('/shop/orders?status=1#top')).toBe('/shop/orders?status=1#top')
  })

  it('拒绝绝对地址、协议相对地址和反斜杠绕过', () => {
    for (const value of [
      'https://evil.example',
      '//evil.example/path',
      '/\\evil.example',
      '/%2f%2fevil.example',
      '/%5cevil.example',
      'javascript:alert(1)'
    ]) {
      expect(safeAdminRedirect(value, '/dashboard')).toBe('/dashboard')
    }
  })

  it('拒绝登录页循环和非字符串输入', () => {
    expect(safeAdminRedirect('/login', '/dashboard')).toBe('/dashboard')
    expect(safeAdminRedirect('/merchant/login', '/dashboard')).toBe('/dashboard')
    expect(safeAdminRedirect('/platform/login', '/dashboard')).toBe('/dashboard')
    expect(safeAdminRedirect(['//evil.example'], '/dashboard')).toBe('/dashboard')
  })
})
