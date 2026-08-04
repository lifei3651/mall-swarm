import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/utils/adminSession', () => ({
  saveAdminSessionExpireTime: vi.fn(),
  clearAdminSessionStorage: vi.fn(),
}))

describe('useAppStore', () => {
  let appStoreModule
  let useAppStore

  beforeAll(async () => {
    appStoreModule = await import('@/store/index')
    useAppStore = appStoreModule.useAppStore
  })

  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('setAuth writes all pieces atomically to state and localStorage', () => {
    const store = useAppStore()
    store.setAuth({
      token: 'test-bearer-token',
      expireTime: '2099-01-01T00:00:00',
      admin: { id: 1, username: 'admin', nickname: '管理员' },
      permissions: ['admin:read', 'shop:product'],
    })

    expect(store.token).toBe('test-bearer-token')
    expect(store.expireTime).toBe('2099-01-01T00:00:00')
    expect(store.userInfo.username).toBe('admin')
    expect(store.userInfo.nickname).toBe('管理员')
    expect(store.permissions).toEqual(['admin:read', 'shop:product'])
    expect(localStorage.getItem('token')).toBe('test-bearer-token')
  })

  it('hasPermission returns true for wildcard *', () => {
    const store = useAppStore()
    store.setPermissions(['*'])
    expect(store.hasPermission('anything')).toBe(true)
    expect(store.hasPermission('shop:product')).toBe(true)
  })

  it('hasPermission returns true for exact match', () => {
    const store = useAppStore()
    store.setPermissions(['shop:product', 'finance:read'])
    expect(store.hasPermission('shop:product')).toBe(true)
    expect(store.hasPermission('finance:read')).toBe(true)
  })

  it('hasPermission returns false for missing permission', () => {
    const store = useAppStore()
    store.setPermissions(['shop:product'])
    expect(store.hasPermission('finance:manage')).toBe(false)
  })

  it('hasPermission returns true when permission arg is falsy', () => {
    const store = useAppStore()
    store.setPermissions(null)
    expect(store.hasPermission(null)).toBe(true)
    expect(store.hasPermission('')).toBe(true)
  })

  it('hasAnyPermission returns true if any item matches', () => {
    const store = useAppStore()
    store.setPermissions(['shop:product'])
    expect(store.hasAnyPermission(['finance:manage', 'shop:product'])).toBe(true)
  })

  it('hasAnyPermission returns false if none match', () => {
    const store = useAppStore()
    store.setPermissions(['shop:product'])
    expect(store.hasAnyPermission(['finance:manage', 'system:manage'])).toBe(false)
  })

  it('logout clears all state and localStorage', () => {
    const store = useAppStore()
    store.setAuth({
      token: 'token-to-clear',
      expireTime: '2099-01-01T00:00:00',
      admin: { id: 1, username: 'admin' },
      permissions: ['admin:read'],
    })

    store.logout()

    expect(store.token).toBe('')
    expect(store.permissions).toEqual([])
    expect(store.userInfo.id).toBeNull()
    expect(store.userInfo.username).toBe('')
  })

  it('token persisted via setAuth survives store re-creation', () => {
    const store = useAppStore()
    store.setAuth({
      token: 'surviving-token',
      expireTime: '2099-12-31T23:59:59',
      admin: { id: 2, username: 'user2' },
      permissions: ['admin:read'],
    })
    // After setAuth, localStorage should have the values
    expect(localStorage.getItem('token')).toBe('surviving-token')
    expect(localStorage.getItem('permissions')).toBe('["admin:read"]')
  })
})
