import { describe, expect, it } from 'vitest'
import {
  MERCHANT_HOME_PATH,
  PLATFORM_HOME_PATH,
  adminHomePath,
  isMerchantWorkspacePath,
  resolveAdminRedirect,
} from '@/utils/adminWorkspace'

describe('admin workspace routing', () => {
  const merchant = { id: 2, merchantId: 8 }
  const platform = { id: 1, merchantId: null }

  it('uses a dedicated home for each account type', () => {
    expect(adminHomePath(merchant)).toBe(MERCHANT_HOME_PATH)
    expect(adminHomePath(platform)).toBe(PLATFORM_HOME_PATH)
  })

  it('keeps merchant business pages and their query strings', () => {
    expect(isMerchantWorkspacePath('/shop/orders?state=WAIT_SHIP')).toBe(true)
    expect(resolveAdminRedirect('/shop/orders?state=WAIT_SHIP', merchant)).toBe('/shop/orders?state=WAIT_SHIP')
  })

  it('sends merchant accounts away from platform-only pages', () => {
    expect(isMerchantWorkspacePath('/shop/categories')).toBe(false)
    expect(resolveAdminRedirect('/shop/categories', merchant)).toBe(MERCHANT_HOME_PATH)
    expect(resolveAdminRedirect('/dashboard', merchant)).toBe(MERCHANT_HOME_PATH)
  })

  it('does not let platform accounts open the merchant-only home', () => {
    expect(resolveAdminRedirect(MERCHANT_HOME_PATH, platform)).toBe(PLATFORM_HOME_PATH)
    expect(resolveAdminRedirect('/shop/categories', platform)).toBe('/shop/categories')
  })
})
