import { clearCurrentCart, switchCartOwner } from '@/store/cart'

export const applyShopSession = (token, member) => {
  localStorage.setItem('shop_token', token)
  localStorage.setItem('shop_member', JSON.stringify(member || {}))
  switchCartOwner(member)
}

export const clearShopSession = ({ clearCart = false } = {}) => {
  if (clearCart) clearCurrentCart()
  localStorage.removeItem('shop_token')
  localStorage.removeItem('shop_member')
  switchCartOwner(null)
}
