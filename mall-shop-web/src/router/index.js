import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { updatePageTitle } from '@/utils/brand'
import { isNativeApp } from '@/utils/appEnvironment'
import { loginRedirectLocation, notifyAuthRequired } from '@/utils/authNavigation'
import { clearStaleChunkRecovery, recoverFromStaleChunk } from '@/utils/chunkRecovery'
import { hasShopSession, restoreShopSession } from '@/utils/shopSession'

const routes = [
  { path: '/', name: 'Home', component: () => import('@/views/HomeView.vue') },
  { path: '/category', name: 'Category', component: () => import('@/views/CategoryView.vue') },
  { path: '/notices', name: 'NoticeList', component: () => import('@/views/NoticeListPage.vue') },
  { path: '/notices/:id', name: 'NoticeDetail', component: () => import('@/views/NoticeDetailPage.vue') },
  { path: '/product/:id', name: 'ProductDetail', component: () => import('@/views/ProductDetailView.vue') },
  { path: '/flash-sale', name: 'FlashSale', component: () => import('@/views/FlashSaleView.vue'), meta: { requiresAuth: true } },
  { path: '/cart', name: 'Cart', component: () => import('@/views/CartView.vue'), meta: { requiresAuth: true } },
  { path: '/checkout', name: 'Checkout', component: () => import('@/views/CheckoutView.vue'), meta: { requiresAuth: true } },
  { path: '/login', name: 'Login', component: () => import('@/surfaces/public/PublicLoginView.vue'), meta: { public: true } },
  { path: '/register', name: 'Register', component: () => import('@/surfaces/public/PublicLoginView.vue'), meta: { public: true } },
  { path: '/app-download', name: 'AppDownload', component: () => import('@/views/AppDownloadView.vue') },
  { path: '/forgot-password', name: 'ForgotPassword', component: () => import('@/views/ForgotPasswordView.vue'), meta: { public: true } },
  { path: '/legal/:type', name: 'Legal', component: () => import('@/views/LegalView.vue') },
  { path: '/profile', name: 'Profile', component: () => import('@/surfaces/public/PublicProfileView.vue'), meta: { requiresAuth: true } },
  { path: '/profile/settings', name: 'ProfileSettings', component: () => import('@/views/ProfileSettingsView.vue'), meta: { requiresAuth: true } },
  { path: '/profile/security/change-login-password', name: 'ChangeLoginPassword', component: () => import('@/views/ChangeLoginPasswordView.vue'), meta: { requiresAuth: true } },
  { path: '/profile/addresses', name: 'ProfileAddresses', component: () => import('@/views/AddressView.vue'), meta: { requiresAuth: true } },
  { path: '/orders', name: 'Orders', component: () => import('@/views/OrdersView.vue'), meta: { requiresAuth: true } },
  { path: '/orders/:id', name: 'OrderDetail', component: () => import('@/views/OrderDetailView.vue'), meta: { requiresAuth: true } },
  { path: '/:pathMatch(.*)*', name: 'NotFound', redirect: '/' },
]

const router = createRouter({
  history: isNativeApp ? createWebHashHistory() : createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

// 发布新版本后，长时间未刷新的页面可能仍引用已经替换的旧分块文件。
// 遇到动态模块加载失败时直接进入目标地址，重新获取最新入口和页面资源。
router.onError((error, to) => {
  const targetHref = to ? router.resolve(to).href : undefined
  recoverFromStaleChunk(error, targetHref)
})

// 路由守卫只检查非敏感会话提示；真正身份始终由 HttpOnly Cookie 和服务端确认。
router.beforeEach(async (to, from, next) => {
  const authenticated = !to.meta.requiresAuth
    || hasShopSession()
    || await restoreShopSession('public')
  if (!authenticated) {
    notifyAuthRequired('请先登录')
    next(loginRedirectLocation(to.fullPath))
  } else {
    next()
  }
})

router.afterEach((to) => {
  clearStaleChunkRecovery()
  updatePageTitle(to.name)
})

export default router
