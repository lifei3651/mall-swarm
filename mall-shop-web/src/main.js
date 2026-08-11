import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/styles.css'
import { startBuildFreshnessGuard } from './utils/buildFreshness'
import { installGlobalErrorHandling } from './utils/runtimeErrors'
import { registerPwa } from './utils/pwa'

const app = createApp(App)
installGlobalErrorHandling(app, router, 'shop')
app.use(router).mount('#app')
startBuildFreshnessGuard()
registerPwa()
