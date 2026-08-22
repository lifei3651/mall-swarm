import { createApp } from 'vue'
import App from '@surface-app'
import router from '@surface-router'
import './assets/styles.css'
import { startBuildFreshnessGuard } from './utils/buildFreshness'
import { installGlobalErrorHandling } from './utils/runtimeErrors'
import { registerPwa } from './utils/pwa'
import { appSurface } from './utils/appSurface'

const app = createApp(App)
const runtimeSurface = appSurface === 'team'
  ? 'team-h5'
  : appSurface === 'integrated'
    ? 'shop-integrated'
    : 'shop-public'
installGlobalErrorHandling(app, router, runtimeSurface)
app.use(router).mount('#app')
startBuildFreshnessGuard()
registerPwa()
