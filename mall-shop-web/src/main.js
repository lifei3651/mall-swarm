import { createApp } from 'vue'
import App from '@surface-app'
import router from '@surface-router'
import './assets/styles.css'
import { startBuildFreshnessGuard } from './utils/buildFreshness'
import { installGlobalErrorHandling } from './utils/runtimeErrors'
import { registerPwa } from './utils/pwa'
import { appSurface } from './utils/appSurface'

const app = createApp(App)
installGlobalErrorHandling(app, router, appSurface === 'team' ? 'team-h5' : 'shop-public')
app.use(router).mount('#app')
startBuildFreshnessGuard()
registerPwa()
