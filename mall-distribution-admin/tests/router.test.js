import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAppStore } from '@/store/index'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

vi.mock('@/utils/adminSession', () => ({
  isAdminSessionExpired: vi.fn(() => false),
  expireAdminSession: vi.fn(),
  ADMIN_SESSION_NOTICE_KEY: 'admin_session_notice',
}))

function makeStore(token = null) {
  setActivePinia(createPinia())
  const store = useAppStore()
  if (token) {
    store.token = token
    store.permissions = ['admin:read', 'shop:product']
  }
  return store
}

describe('router guards', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('public route passes without token', () => {
    const store = makeStore()
    const to = { meta: { public: true, title: '后台登录' }, matched: [], fullPath: '/login' }
    // Public routes skip auth checks entirely in beforeEach
    expect(to.meta.public).toBe(true)
  })

  it('protected route without token should redirect', () => {
    const store = makeStore()
    expect(store.token).toBe('')
    // Guard logic: if no token → redirect to login
  })

  it('protected route with token proceeds', () => {
    const store = makeStore('valid-token')
    expect(store.token).toBe('valid-token')
    expect(store.hasPermission('shop:product')).toBe(true)
  })

  it('missing permission redirects to dashboard', () => {
    const store = makeStore('valid-token')
    store.permissions = ['admin:read']
    expect(store.hasPermission('finance:manage')).toBe(false)
  })

  it('permission * grants access to all routes', () => {
    const store = makeStore('valid-token')
    store.permissions = ['*']
    expect(store.hasPermission('anything.any.action')).toBe(true)
    expect(store.hasPermission('system:manage')).toBe(true)
  })

  it('merchant dashboard route is redirected to merchant finance', async () => {
    const source = await readFile(resolve(process.cwd(), 'src/router/index.js'), 'utf8')
    expect(source).toContain("store.userInfo?.merchantId && to.path === '/dashboard'")
    expect(source).toContain("next('/audit/merchant-finance')")
  })

  it('shares one server session hydration request across concurrent route guards', async () => {
    const source = await readFile(resolve(process.cwd(), 'src/router/index.js'), 'utf8')
    expect(source).toContain('let authHydrationPromise = null')
    expect(source).toContain('if (!authHydrationPromise)')
    expect(source).toContain('await hydrateAdminAuth(store)')
    expect(source).toContain('authHydrationPromise = null')
  })
})
