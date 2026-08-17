export const appSurface = import.meta.env.VITE_APP_SURFACE === 'team' ? 'team' : 'public'
export const isTeamSurface = appSurface === 'team'
export const isPublicSurface = !isTeamSurface
