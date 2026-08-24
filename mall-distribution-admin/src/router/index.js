import { createRouter, createWebHistory } from 'vue-router'
import { useAppStore } from '@/store'
import { expireAdminSession, isAdminSessionExpired } from '@/utils/adminSession'
import { getMe } from '@/api/auth'
import { ElMessage } from 'element-plus'

let authHydrationPromise = null

function hydrateAdminAuth(store) {
  if (!authHydrationPromise) {
    authHydrationPromise = getMe({ silentError: true })
      .then((res) => store.setAuth(res.data || {}))
      .finally(() => {
        authHydrationPromise = null
      })
  }
  return authHydrationPromise
}

// 布局组件
const Layout = () => import('@/components/Layout.vue')

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '后台登录', public: true },
  },
  {
    path: '/change-password',
    name: 'ChangeAdminPassword',
    component: () => import('@/views/login/change-password.vue'),
    meta: { title: '修改后台密码', passwordChangeOnly: true },
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'Monitor' },
      },
      // 商城管理
      {
        path: 'shop',
        name: 'Shop',
        redirect: '/shop/products',
        meta: { title: '商品与订单', icon: 'Goods' },
        children: [
          {
            path: 'products',
            name: 'ShopProducts',
            component: () => import('@/views/shop/products.vue'),
            meta: { title: '商品管理', permission: 'shop:product' },
          },
          {
            path: 'merchants',
            name: 'ShopMerchants',
            component: () => import('@/views/shop/merchants.vue'),
            meta: { title: '商户管理', permission: 'shop:product' },
          },
          {
            path: 'merchant-product-reviews',
            name: 'MerchantProductReviews',
            component: () => import('@/views/shop/merchant-product-reviews.vue'),
            meta: { title: '商户商品审核', permission: 'shop:product-review' },
          },
          {
            path: 'service-addresses',
            name: 'ShopServiceAddresses',
            component: () => import('@/views/shop/service-addresses.vue'),
            meta: { title: '发货与退货地址', permission: 'shop:product' },
          },
          {
            path: 'orders',
            name: 'ShopOrders',
            component: () => import('@/views/shop/orders.vue'),
            meta: { title: '订单管理', permission: 'shop:order' },
          },
          {
            path: 'categories',
            name: 'ShopCategories',
            component: () => import('@/views/shop/categories.vue'),
            meta: { title: '分类与规格', permission: 'shop:product' },
          },
          {
            path: 'reviews',
            name: 'ShopReviews',
            component: () => import('@/views/shop/reviews.vue'),
            meta: { title: '商品评价', permission: 'shop:product' },
          },
          {
            path: 'banners',
            name: 'ShopBannersLegacy',
            redirect: '/tenant/banners',
            meta: { title: '首页Banner', hidden: true, permission: 'config:shop' },
          },
        ],
      },
      // 会员统一管理：同一列表同时管理未进入和已进入奖金体系的账号
      {
        path: 'members',
        name: 'Members',
        redirect: '/members/list',
        meta: { title: '会员管理', icon: 'User' },
        children: [
          {
            path: 'list', name: 'ShopMembers', component: () => import('@/views/shop/members.vue'),
            meta: { title: '会员全景管理', permission: 'shop:member' },
          },
          {
            path: 'tree', name: 'MemberAgentTree', component: () => import('@/views/agent/tree.vue'),
            meta: { title: '团队关系树', permission: 'distribution:manage' },
          },
          {
            path: 'line-changes', name: 'MemberLineChanges', component: () => import('@/views/agent/line-changes.vue'),
            meta: { title: '移线记录', permission: 'line-change:apply' },
          },
          {
            path: 'detail/:id', name: 'MemberAgentDetail', component: () => import('@/views/agent/detail.vue'),
            meta: { title: '会员推广详情', hidden: true, permission: 'distribution:manage' },
          },
        ],
      },
      // 旧地址兼容，不再在侧边栏重复显示代理列表
      {
        path: 'agent',
        name: 'Agent',
        redirect: '/agent/list',
        meta: { title: '代理管理', icon: 'User', hidden: true },
        children: [
          {
            path: 'list',
            name: 'AgentList',
            component: () => import('@/views/agent/list.vue'),
            meta: { title: '代理列表', permission: 'distribution:manage' },
          },
          {
            path: 'detail/:id',
            name: 'AgentDetail',
            component: () => import('@/views/agent/detail.vue'),
            meta: { title: '代理详情', hidden: true, permission: 'distribution:manage' },
          },
          {
            path: 'add',
            name: 'AgentAdd',
            redirect: '/members/list?create=1',
            meta: { title: '后台新增会员', hidden: true, permission: 'distribution:manage' },
          },
          {
            path: 'tree',
            name: 'AgentTree',
            component: () => import('@/views/agent/tree.vue'),
            meta: { title: '代理关系树', permission: 'distribution:manage' },
          },
          {
            path: 'line-changes',
            name: 'AgentLineChanges',
            component: () => import('@/views/agent/line-changes.vue'),
            meta: { title: '移线记录', permission: 'line-change:apply' },
          },
        ],
      },
      // 奖金管理（底层保留 commission 路径，兼容旧链接）
      {
        path: 'commission',
        name: 'Commission',
        redirect: '/commission/records',
        meta: { title: '奖金管理', icon: 'Money' },
        children: [
          {
            path: 'records',
            name: 'CommissionRecords',
            component: () => import('@/views/commission/records.vue'),
            meta: { title: '奖金记录', permission: 'commission:manage' },
          },
          {
            path: 'settle',
            name: 'CommissionSettle',
            component: () => import('@/views/commission/settle.vue'),
            meta: { title: '待结算奖金（收货后7天）', permission: 'commission:manage' },
          },
        ],
      },
      // 业绩统计
      {
        path: 'performance',
        name: 'Performance',
        redirect: '/performance/overview',
        meta: { title: '业绩统计', icon: 'TrendCharts' },
        children: [
          {
            path: 'overview',
            name: 'PerformanceOverview',
            component: () => import('@/views/performance/overview.vue'),
            meta: { title: '会员业绩概览', permission: 'distribution:manage' },
          },
          {
            path: 'contributions',
            name: 'PerformanceContributions',
            component: () => import('@/views/performance/contributions.vue'),
            meta: { title: '业绩来源明细', permission: 'distribution:manage' },
          },
          {
            path: 'ranking',
            name: 'PerformanceRanking',
            component: () => import('@/views/performance/ranking.vue'),
            meta: { title: '业绩排名', permission: 'distribution:manage' },
          },
        ],
      },
      // 分销审计
      {
        path: 'audit',
        name: 'Audit',
        redirect: '/audit/orders',
        meta: { title: '分销审计', icon: 'DataAnalysis' },
        children: [
          {
            path: 'orders',
            name: 'AuditOrders',
            component: () => import('@/views/audit/orders.vue'),
            meta: { title: '订单奖金与利润追溯', permission: 'finance:read' },
          },
          {
            path: 'person-profile',
            name: 'AuditPersonProfile',
            component: () => import('@/views/audit/person-profile.vue'),
            meta: { title: '会员资金与订单全景', permission: 'finance:read' },
          },
          {
            path: 'finance',
            name: 'AuditFinance',
            component: () => import('@/views/audit/finance.vue'),
            meta: { title: '财务总览', permission: 'finance:read' },
          },
          {
            path: 'merchant-finance',
            name: 'MerchantFinance',
            component: () => import('@/views/audit/merchant-finance.vue'),
            meta: { title: '商户货款', permission: 'finance:read' },
          },
          {
            path: 'settings',
            name: 'AuditSettings',
            component: () => import('@/views/audit/settings.vue'),
            meta: { title: '会员端业绩查看权限', permission: 'config:bonus' },
          },
          {
            path: 'operation-logs',
            name: 'AuditOperationLogs',
            component: () => import('@/views/audit/operation-logs.vue'),
            meta: { title: '后台操作日志', permission: 'system:manage' },
          },
        ],
      },
      // 单商城交付设置
      {
        path: 'tenant',
        name: 'Tenant',
        redirect: '/tenant/list',
        meta: { title: '商城设置', icon: 'OfficeBuilding' },
        children: [
          {
            path: 'list',
            name: 'TenantList',
            component: () => import('@/views/tenant/list.vue'),
            meta: { title: '商城视觉与页面', permission: 'config:shop' },
          },
          {
            path: 'banners',
            name: 'TenantBanners',
            component: () => import('@/views/shop/banners.vue'),
            // Banner 已并入“商城视觉与页面”，保留 URL 兼容旧书签但不再单独显示菜单。
            meta: { title: '首页Banner', hidden: true, permission: 'config:shop' },
          },
          {
            path: 'profile',
            name: 'TenantProfile',
            component: () => import('@/views/tenant/profile.vue'),
            meta: { title: '商城资料与客服', permission: 'config:shop' },
          },
          {
            path: 'business-modes',
            name: 'TenantBusinessModes',
            component: () => import('@/views/tenant/business-modes.vue'),
            meta: { title: '秒杀与复购模式', permission: 'config:bonus' },
          },
          {
            path: 'flash-sales',
            name: 'TenantFlashSales',
            component: () => import('@/views/shop/flash-sales.vue'),
            meta: { title: '秒杀活动', permission: 'shop:product' },
          },
          {
            path: 'live-rooms',
            name: 'TenantLiveRooms',
            component: () => import('@/views/shop/live-rooms.vue'),
            meta: { title: '直播运营中心', permission: 'shop:product' },
          },
          {
            path: 'notices',
            name: 'TenantNotices',
            component: () => import('@/views/tenant/notices.vue'),
            meta: { title: '商城公告', permission: 'config:shop' },
          },
          {
            path: 'message-operations',
            name: 'TenantMessageOperations',
            component: () => import('@/views/tenant/message-operations.vue'),
            meta: { title: '消息运营', permission: 'config:shop' },
          },
          {
            path: 'display',
            name: 'TenantDisplay',
            redirect: '/tenant/list',
            meta: { title: '商城装修', permission: 'config:shop' },
          },
          {
            path: 'legal',
            name: 'TenantLegal',
            component: () => import('@/views/tenant/legal.vue'),
            meta: { title: '协议与规则', permission: 'config:shop' },
          },
          {
            path: 'bonus-config',
            name: 'TenantBonusConfig',
            component: () => import('@/views/tenant/bonus-config.vue'),
            meta: { title: '奖金与钱包规则', permission: 'config:bonus' },
          },
          {
            path: 'erp',
            name: 'TenantErp',
            component: () => import('@/views/tenant/erp.vue'),
            meta: { title: 'ERP订单对接', permission: 'config:integration' },
          },
        ],
      },
      // 提现管理
      {
        path: 'withdraw',
        name: 'Withdraw',
        redirect: '/withdraw/list',
        meta: { title: '提现管理', icon: 'Wallet' },
        children: [
          {
            path: 'list',
            name: 'WithdrawList',
            component: () => import('@/views/withdraw/list.vue'),
            meta: { title: '提现记录', permission: 'finance:read' },
          },
          {
            path: 'audit',
            name: 'WithdrawAudit',
            component: () => import('@/views/withdraw/audit.vue'),
            meta: { title: '提现审核', permission: 'finance:manage' },
          },
        ],
      },
      // 批量导入
      {
        path: 'import',
        name: 'Import',
        redirect: '/import/agents',
        meta: { title: '数据迁移', icon: 'Upload' },
        children: [
          {
            path: 'agents',
            name: 'ImportAgents',
            component: () => import('@/views/import/agents.vue'),
            meta: { title: '外部团队平移', permission: 'import:manage' },
          },
          {
            path: 'orders',
            name: 'ImportOrders',
            component: () => import('@/views/import/orders.vue'),
            meta: { title: '历史订单导入', permission: 'import:manage' },
          },
          {
            path: 'result/:batchNo',
            name: 'ImportResult',
            component: () => import('@/views/import/result.vue'),
            meta: { title: '导入结果', hidden: true, permission: 'import:manage' },
          },
        ],
      },
      // 账户管理
      {
        path: 'account',
        name: 'Account',
        redirect: '/account/list',
        meta: { title: '账户管理', icon: 'CreditCard' },
        children: [
          {
            path: 'list',
            name: 'AccountList',
            component: () => import('@/views/account/list.vue'),
            meta: { title: '会员余额账户', permission: 'distribution:manage' },
          },
          {
            path: 'flows',
            name: 'BalanceFlows',
            component: () => import('@/views/account/flows.vue'),
            meta: { title: '余额流水', permission: 'finance:read' },
          },
          {
            path: 'detail/:agentId',
            name: 'AccountDetail',
            component: () => import('@/views/account/detail.vue'),
            meta: { title: '账户详情', hidden: true, permission: 'distribution:manage' },
          },
        ],
      },
      // 系统管理
      {
        path: 'system',
        name: 'System',
        redirect: '/system/admin-users',
        meta: { title: '系统与权限', icon: 'Setting' },
        children: [
          {
            path: 'admin-users',
            name: 'SystemAdminUsers',
            component: () => import('@/views/system/admin-users.vue'),
            meta: { title: '后台账号与权限', permission: 'system:manage' },
          },
        ],
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在', public: true },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

const CHUNK_RELOAD_KEY = 'admin_chunk_reload_path'
const isStaleChunkError = (error) => [
  'Failed to fetch dynamically imported module',
  'Importing a module script failed',
  'Unable to preload CSS',
  'ChunkLoadError',
  'Loading chunk',
].some((text) => String(error?.message || error || '').includes(text))

// 后台页面跨版本长时间打开时，旧页面引用的分块文件可能已被新版本替换。
// 遇到此类错误自动刷新一次，避免用户点击菜单无反应、只能手工刷新。
router.onError((error, to) => {
  if (!isStaleChunkError(error)) return
  const targetPath = to?.fullPath || window.location.pathname
  let reloadedPath = ''
  try {
    reloadedPath = window.sessionStorage.getItem(CHUNK_RELOAD_KEY) || ''
  } catch {
    // 会话存储不可用时仍允许刷新。
  }
  if (reloadedPath === targetPath) return
  try {
    window.sessionStorage.setItem(CHUNK_RELOAD_KEY, targetPath)
  } catch {
    // 会话存储不可用时仍允许刷新。
  }
  window.location.reload()
})

router.afterEach(() => {
  try {
    window.sessionStorage.removeItem(CHUNK_RELOAD_KEY)
  } catch {
    // 忽略会话存储异常。
  }
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  // 设置页面标题
  const adminBrandName = localStorage.getItem('admin_brand_name') || '商城'
  document.title = to.meta.title ? `${to.meta.title} - ${adminBrandName}管理后台` : `${adminBrandName}管理后台`
  const store = useAppStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!store.token) {
    ElMessage.warning({ message: '请先登录后台', grouping: true })
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (isAdminSessionExpired()) {
    expireAdminSession('后台登录已超时，请重新登录')
    next(false)
    return
  }
  // 用户和权限只从服务端会话恢复，不信任 localStorage 中可被修改的副本。
  if (!store.authHydrated) {
    try {
      await hydrateAdminAuth(store)
    } catch {
      expireAdminSession('无法验证后台会话，请重新登录')
      next(false)
      return
    }
  }
  if (Number(store.userInfo?.mustChangePassword) === 1 && !to.meta.passwordChangeOnly) {
    ElMessage.warning({ message: '请先修改后台初始密码', grouping: true })
    next('/change-password')
    return
  }
  if (store.userInfo?.merchantId && to.path === '/dashboard') {
    next('/audit/merchant-finance')
    return
  }
  const requiredPermissions = to.matched
    .map((item) => item.meta?.permission)
    .filter(Boolean)
  if (requiredPermissions.length > 0 && !requiredPermissions.every((item) => store.hasPermission(item))) {
    ElMessage.warning({ message: '当前账号没有访问该页面的权限', grouping: true })
    next(store.userInfo?.merchantId ? '/audit/merchant-finance' : '/dashboard')
    return
  }
  next()
})

export default router
