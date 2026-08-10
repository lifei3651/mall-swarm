import { clearCurrentCart, switchCartOwner } from '@/store/cart'

const LEGACY_TOKEN_KEY = 'shop_token'
const MEMBER_KEY = 'shop_member'

const safeMemberSnapshot = (member) => ({
  id: member?.id || null,
  nickname: member?.nickname || '',
  username: member?.username || '',
})

export const applyShopSession = (member) => {
  // 登录凭证由服务端写入 HttpOnly Cookie，浏览器脚本不再持有 Token。
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  localStorage.setItem(MEMBER_KEY, JSON.stringify(safeMemberSnapshot(member)))
  switchCartOwner(member)
}

export const hasShopSession = () => Boolean(
  localStorage.getItem(MEMBER_KEY) || localStorage.getItem(LEGACY_TOKEN_KEY),
)

export const getLegacyShopToken = () => localStorage.getItem(LEGACY_TOKEN_KEY)

export const finishLegacyTokenMigration = () => localStorage.removeItem(LEGACY_TOKEN_KEY)

export const clearShopSession = ({ clearCart = false } = {}) => {
  if (clearCart) clearCurrentCart()
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  localStorage.removeItem(MEMBER_KEY)
  switchCartOwner(null)
}
