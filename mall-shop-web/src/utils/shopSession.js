import { clearCurrentCart, switchCartOwner } from '@/store/cart'
import { apiBaseUrl } from '@/utils/appEnvironment'

const LEGACY_TOKEN_KEY = 'shop_token'
const MEMBER_KEY = 'shop_member'
// 旧版 Token 只在当前页面内存中保留到 HttpOnly Cookie 换发完成，启动后立即清除持久化副本。
let legacyShopToken = localStorage.getItem(LEGACY_TOKEN_KEY)
localStorage.removeItem(LEGACY_TOKEN_KEY)
// localStorage 只保存非敏感展示快照，不能作为已验证登录态。
let sessionVerified = false

const safeMemberSnapshot = (member) => ({
  id: member?.id || null,
  nickname: member?.nickname || '',
})

export const applyShopSession = (member) => {
  // 登录凭证由服务端写入 HttpOnly Cookie，浏览器脚本不再持有 Token。
  legacyShopToken = null
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  localStorage.setItem(MEMBER_KEY, JSON.stringify(safeMemberSnapshot(member)))
  sessionVerified = true
  switchCartOwner(member)
}

export const hasShopSession = () => sessionVerified && Boolean(
  localStorage.getItem(MEMBER_KEY) || legacyShopToken,
)

let sessionRestorePromise

export const restoreShopSession = async (surface = 'public') => {
  if (hasShopSession()) return true
  if (sessionRestorePromise) return sessionRestorePromise

  const fullAccountSurface = surface === 'team' || surface === 'integrated'
  const profilePath = fullAccountSurface ? '/shop/auth/me' : '/shop/public/profile'
  sessionRestorePromise = fetch(`${apiBaseUrl}${profilePath}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'X-Shop-Client': 'storefront',
      'X-Shop-Surface': fullAccountSurface ? surface : 'public',
    },
  }).then(async (response) => {
    if (!response.ok) return false
    const payload = await response.json().catch(() => null)
    const member = payload?.code === 200
      ? (payload?.data?.member || payload?.data)
      : null
    if (!member?.id) return false
    applyShopSession(member)
    return true
  }).catch(() => false).finally(() => {
    sessionRestorePromise = null
  })
  return sessionRestorePromise
}

export const getLegacyShopToken = () => legacyShopToken

export const finishLegacyTokenMigration = () => {
  legacyShopToken = null
  sessionVerified = false
  localStorage.removeItem(LEGACY_TOKEN_KEY)
}

export const clearShopSession = ({ clearCart = false } = {}) => {
  if (clearCart) clearCurrentCart()
  legacyShopToken = null
  sessionVerified = false
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  localStorage.removeItem(MEMBER_KEY)
  switchCartOwner(null)
}
