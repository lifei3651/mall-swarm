import { getBusinessConfig } from '@/api/shop'
export const teamBusinessAllowed = (config) => !!config && !config.businessMode && config.teamFeaturesEnabled !== false
export async function checkTeamBusinessRoute(path) {
  if (!['/invite', '/profile/team'].includes(path)) return true
  return teamBusinessAllowed((await getBusinessConfig()).data)
}
