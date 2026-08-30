import { beforeEach, describe, expect, it } from 'vitest'
import * as adminPortal from '@/utils/adminPortal'
import {
  ADMIN_PORTAL_MERCHANT,
  ADMIN_PORTAL_PLATFORM,
  adminPortalForAccount,
  adminPortalLoginPath,
  readAdminPortal,
  saveAdminPortal,
} from '@/utils/adminPortal'

describe('后台入口隔离', () => {
  beforeEach(() => localStorage.clear())

  it('为平台和商家生成不同登录地址', () => {
    expect(adminPortalLoginPath(ADMIN_PORTAL_PLATFORM)).toBe('/platform/login')
    expect(adminPortalLoginPath(ADMIN_PORTAL_MERCHANT)).toBe('/merchant/login')
  })

  it('根据服务端返回的商户绑定识别账号入口', () => {
    expect(adminPortalForAccount({ id: 1, merchantId: null })).toBe(ADMIN_PORTAL_PLATFORM)
    expect(adminPortalForAccount({ id: 2, merchantId: 88 })).toBe(ADMIN_PORTAL_MERCHANT)
  })

  it('不再提供根据报错识别另一入口的能力', () => {
    expect(adminPortal).not.toHaveProperty('adminPortalMismatchFromError')
  })

  it('记住当前账号入口并对无效值安全回退到商家入口', () => {
    saveAdminPortal(ADMIN_PORTAL_PLATFORM)
    expect(readAdminPortal()).toBe(ADMIN_PORTAL_PLATFORM)
    localStorage.setItem('admin_portal', 'UNKNOWN')
    expect(readAdminPortal()).toBe(ADMIN_PORTAL_MERCHANT)
  })
})
