import { createRouter, createWebHistory } from 'vue-router'
import { loginRedirectLocation, notifyAuthRequired } from '@/utils/authNavigation'
import { clearStaleChunkRecovery, recoverFromStaleChunk } from '@/utils/chunkRecovery'
import { hasShopSession, restoreShopSession } from '@/utils/shopSession'
import { updatePageTitle } from '@/utils/brand'
import { toPublicRegistrationUrl } from '@/utils/appEnvironment'

const protectedRoute = { requiresAuth: true }
const routes = [
  { path: '/', name: 'TeamHome', component: () => import('./TeamHomeView.vue'), meta: protectedRoute },
  { path: '/login', name: 'Login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', name: 'Register', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/forgot-password', name: 'ForgotPassword', component: () => import('@/views/ForgotPasswordView.vue'), meta: { public: true } },
  { path: '/invite', name: 'Invite', component: () => import('@/views/InviteView.vue'), meta: protectedRoute },
  { path: '/messages', name: 'Messages', component: () => import('@/views/MessageCenterView.vue'), meta: protectedRoute },
  { path: '/messages/:id', name: 'MessageDetail', component: () => import('@/views/MessageDetailView.vue'), meta: protectedRoute },
  { path: '/support', name: 'ServiceTickets', component: () => import('@/views/ServiceTicketsView.vue'), meta: protectedRoute },
  { path: '/support/:id', name: 'ServiceTicketDetail', component: () => import('@/views/ServiceTicketDetailView.vue'), meta: protectedRoute },
  { path: '/profile/team', name: 'ProfileTeam', component: () => import('@/views/TeamPerformanceView.vue'), meta: protectedRoute },
  { path: '/profile/wallet', name: 'ProfileWallet', component: () => import('@/views/WalletView.vue'), meta: protectedRoute },
  { path: '/profile/real-name', name: 'RealNameVerification', component: () => import('@/views/RealNameVerificationView.vue'), meta: protectedRoute },
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
router.beforeEach(async (to, from, next) => {
  // 团队 H5 只负责团队服务，不承载购物账号注册。旧书签或旧二维码进入
  // /register 时也必须回到公开商城，避免注册完成后误落到团队业绩首页。
  if (to.name === 'Register') {
    window.location.replace(toPublicRegistrationUrl(to.query))
    next(false)
    return
  }
  const authenticated = !to.meta.requiresAuth
    || hasShopSession()
    || await restoreShopSession('team')
  if (!authenticated) {
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
