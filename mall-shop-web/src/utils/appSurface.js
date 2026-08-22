const configuredSurface = String(import.meta.env.VITE_APP_SURFACE || '').toLowerCase()

export const appSurface = ['public', 'team', 'integrated'].includes(configuredSurface)
  ? configuredSurface
  : 'public'
export const isTeamSurface = appSurface === 'team'
export const isIntegratedSurface = appSurface === 'integrated'
export const isPublicSurface = appSurface === 'public'
export const hasTeamBusiness = isTeamSurface || isIntegratedSurface
