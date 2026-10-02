import { getStorefrontBusinessConfig } from '@/api/shop'
const teamPaths = ['/members/tree', '/members/line-changes', '/performance/']
export const isTeamBusinessPath = (path) => teamPaths.some(item => path === item || (item.endsWith('/') && path.startsWith(item)))
export const modeMenuAllowed = (path, mode, ready = true) => (ready && !mode) || !(isTeamBusinessPath(path) || ['/commission/records', '/commission/settle'].includes(path))
export async function refreshCustomerBusinessMode(store) {
  const result = await getStorefrontBusinessConfig()
  if (!result.data || !Object.prototype.hasOwnProperty.call(result.data, 'invitationEnabled')) throw new Error('业务能力尚未读取')
  store.setBusinessMode?.(result.data.businessMode || null)
  return result.data
}
