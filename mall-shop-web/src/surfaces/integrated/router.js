import { createRouter, createWebHistory } from 'vue-router'
import { updatePageTitle } from '@/utils/brand'
import { loginRedirectLocation, notifyAuthRequired } from '@/utils/authNavigation'
import { clearStaleChunkRecovery, recoverFromStaleChunk } from '@/utils/chunkRecovery'
import { hasShopSession, restoreShopSession } from '@/utils/shopSession'

const protectedRoute = { requiresAuth: true }
const routes = [
  { path: '/', name: 'Home', component: () => import('@/views/HomeView.vue') },
  { path: '/category', name: 'Category', component: () => import('@/views/CategoryView.vue') },
  { path: '/notices', name: 'NoticeList', component: () => import('@/views/NoticeListPage.vue') },
  { path: '/notices/:id', name: 'NoticeDetail', component: () => import('@/views/NoticeDetailPage.vue') },
  { path: '/messages', name: 'Messages', component: () => import('@/views/MessageCenterView.vue'), meta: protectedRoute },
  { path: '/messages/:id', name: 'MessageDetail', component: () => import('@/views/MessageDetailView.vue'), meta: protectedRoute },
  { path: '/support', name: 'ServiceTickets', component: () => import('@/views/ServiceTicketsView.vue'), meta: protectedRoute },
  { path: '/support/:id', name: 'ServiceTicketDetail', component: () => import('@/views/ServiceTicketDetailView.vue'), meta: protectedRoute },
  { path: '/product/:id', name: 'ProductDetail', component: () => import('@/views/ProductDetailView.vue') },
  { path: '/flash-sale', name: 'FlashSale', component: () => import('@/views/FlashSaleView.vue'), meta: protectedRoute },
  { path: '/live', name: 'LiveSquare', component: () => import('@/views/LiveSquareView.vue') },
  { path: '/live/:id', name: 'LiveRoom', component: () => import('@/views/LiveRoomView.vue') },
  { path: '/live-studio', name: 'LiveStudio', component: () => import('@/views/LiveStudioView.vue'), meta: { requiresAuth: true } },
  { path: '/new-arrivals', name: 'NewArrivals', component: () => import('@/views/NewArrivalsView.vue') },
  { path: '/brand-culture', name: 'BrandCulture', component: () => import('@/views/BrandCultureView.vue') },
  { path: '/repurchase', name: 'Repurchase', component: () => import('@/views/RepurchaseView.vue'), meta: protectedRoute },
  { path: '/cart', name: 'Cart', component: () => import('@/views/CartView.vue'), meta: protectedRoute },
  { path: '/checkout', name: 'Checkout', component: () => import('@/views/CheckoutView.vue'), meta: protectedRoute },
  { path: '/login', name: 'Login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', name: 'Register', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/app-download', name: 'AppDownload', component: () => import('@/views/AppDownloadView.vue') },
  { path: '/forgot-password', name: 'ForgotPassword', component: () => import('@/views/ForgotPasswordView.vue'), meta: { public: true } },
  { path: '/invite', name: 'Invite', component: () => import('@/views/InviteView.vue'), meta: protectedRoute },
  { path: '/legal/:type', name: 'Legal', component: () => import('@/views/LegalView.vue') },
  { path: '/profile', name: 'Profile', component: () => import('@/views/ProfileView.vue'), meta: protectedRoute },
  { path: '/profile/settings', name: 'ProfileSettings', component: () => import('@/views/ProfileSettingsView.vue'), meta: protectedRoute },
  { path: '/profile/wallet', name: 'ProfileWallet', component: () => import('./IntegratedWalletView.vue'), meta: protectedRoute },
  { path: '/profile/wallet/transfer', name: 'BalanceTransfer', component: () => import('@/views/BalanceTransferView.vue'), meta: protectedRoute },
  { path: '/profile/real-name', name: 'RealNameVerification', component: () => import('@/views/RealNameVerificationView.vue'), meta: protectedRoute },
  { path: '/profile/team', name: 'ProfileTeam', component: () => import('@/views/TeamPerformanceView.vue'), meta: protectedRoute },
  { path: '/profile/security', name: 'ProfileSecurity', component: () => import('@/views/SecurityView.vue'), meta: protectedRoute },
  { path: '/profile/security/change-login-password', name: 'ChangeLoginPassword', component: () => import('@/views/ChangeLoginPasswordView.vue'), meta: protectedRoute },
  { path: '/profile/security/change-payment-password', name: 'ChangePaymentPassword', component: () => import('@/views/ChangePaymentPasswordView.vue'), meta: protectedRoute },
  { path: '/profile/addresses', name: 'ProfileAddresses', component: () => import('@/views/AddressView.vue'), meta: protectedRoute },
  { path: '/orders', name: 'Orders', component: () => import('@/views/OrdersView.vue'), meta: protectedRoute },
  { path: '/orders/:id', name: 'OrderDetail', component: () => import('@/views/OrderDetailView.vue'), meta: protectedRoute },
  { path: '/:pathMatch(.*)*', name: 'NotFound', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.onError((error, to) => recoverFromStaleChunk(error, to ? router.resolve(to).href : undefined))
router.beforeEach(async (to, from, next) => {
  const authenticated = !to.meta.requiresAuth
    || hasShopSession()
    || await restoreShopSession('integrated')
  if (!authenticated) {
    notifyAuthRequired('请先登录')
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
