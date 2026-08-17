import { createRouter, createWebHistory } from 'vue-router'
import { loginRedirectLocation, notifyAuthRequired } from '@/utils/authNavigation'
import { clearStaleChunkRecovery, recoverFromStaleChunk } from '@/utils/chunkRecovery'
import { hasShopSession } from '@/utils/shopSession'
import { updatePageTitle } from '@/utils/brand'

const protectedRoute = { requiresAuth: true }
const routes = [
  { path: '/', name: 'TeamHome', component: () => import('./TeamHomeView.vue'), meta: protectedRoute },
  { path: '/login', name: 'Login', component: () => import('@/views/LoginView.vue') },
  { path: '/register', name: 'Register', component: () => import('@/views/LoginView.vue') },
  { path: '/forgot-password', name: 'ForgotPassword', component: () => import('@/views/ForgotPasswordView.vue') },
  { path: '/invite', name: 'Invite', component: () => import('@/views/InviteView.vue'), meta: protectedRoute },
  { path: '/profile/team', name: 'ProfileTeam', component: () => import('@/views/TeamPerformanceView.vue'), meta: protectedRoute },
  { path: '/profile/wallet', name: 'ProfileWallet', component: () => import('@/views/WalletView.vue'), meta: protectedRoute },
  { path: '/profile/wallet/transfer', name: 'BalanceTransfer', component: () => import('@/views/BalanceTransferView.vue'), meta: protectedRoute },
  { path: '/profile/security', name: 'ProfileSecurity', component: () => import('@/views/SecurityView.vue'), meta: protectedRoute },
  { path: '/profile/security/change-login-password', name: 'ChangeLoginPassword', component: () => import('@/views/ChangeLoginPasswordView.vue'), meta: protectedRoute },
  { path: '/profile/security/change-payment-password', name: 'ChangePaymentPassword', component: () => import('@/views/ChangePaymentPasswordView.vue'), meta: protectedRoute },
  { path: '/profile/settings', name: 'ProfileSettings', component: () => import('@/views/ProfileSettingsView.vue'), meta: protectedRoute },
  { path: '/legal/:type', name: 'Legal', component: () => import('@/views/LegalView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.onError((error, to) => recoverFromStaleChunk(error, to ? router.resolve(to).href : undefined))
router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !hasShopSession()) {
    notifyAuthRequired('请先登录团队服务中心')
    next(loginRedirectLocation(to.fullPath))
    return
  }
  next()
})
router.afterEach((to) => {
  clearStaleChunkRecovery()
  updatePageTitle(to.name)
})

export default router
