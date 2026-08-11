import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
// ElMessage / ElMessageBox 是以函数方式调用，不会被组件自动导入插件识别，
// 必须显式引入样式，否则二次确认框会失去居中布局并显示在页面左上角。
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import './assets/styles.scss'
import { installGlobalErrorHandling } from './utils/runtimeErrors'

const app = createApp(App)
installGlobalErrorHandling(app, router)

app.use(createPinia())
app.use(router)

app.mount('#app')
