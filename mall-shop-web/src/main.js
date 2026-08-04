import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/styles.css'
import { startBuildFreshnessGuard } from './utils/buildFreshness'

createApp(App).use(router).mount('#app')
startBuildFreshnessGuard()
