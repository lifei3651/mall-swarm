<template>
  <div class="layout-container" :class="{ 'dashboard-mode': isDashboard }">
    <!-- 侧边栏 -->
    <div class="layout-sidebar" :class="{ collapsed: isCollapsed }">
      <div class="logo">
        <img :src="sidebarLogoSrc" :alt="`${brand.brandName} Logo`" @error="handleSidebarLogoError" />
        <span v-if="!isCollapsed">{{ brand.brandName }}</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="isDashboard ? ['products'] : []"
        :collapse="isCollapsed"
        background-color="#111c36"
        text-color="#aeb9cf"
        active-text-color="#ffffff"
        router
      >
        <template v-for="menu in visibleBusinessMenus" :key="menu.key || menu.path">
          <el-menu-item
            v-if="menu.path"
            :index="menu.path"
          >
            <el-icon><component :is="menuIcon(menu.icon)" /></el-icon>
            <template #title>{{ menu.title }}</template>
          </el-menu-item>

          <el-sub-menu v-else :index="menu.key">
            <template #title>
              <el-icon><component :is="menuIcon(menu.icon)" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item
              v-for="item in menu.items"
              :key="item.path"
              :index="item.path"
            >
              {{ item.title }}
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
      <button
        type="button"
        class="sidebar-collapse"
        :aria-label="isCollapsed ? '展开侧边菜单' : '收起侧边菜单'"
        @click="isCollapsed = !isCollapsed"
      >
        <el-icon><DArrowRight v-if="isCollapsed" /><DArrowLeft v-else /></el-icon>
        <span v-if="!isCollapsed">收起菜单</span>
      </button>
    </div>

    <!-- 主内容区 -->
    <div class="layout-main">
      <!-- 头部 -->
      <div v-if="!isDashboard" class="layout-header">
        <div class="header-left">
          <el-icon
            class="collapse-btn"
            @click="isCollapsed = !isCollapsed"
          >
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-popover placement="bottom-end" :width="340" trigger="click">
            <template #reference>
              <button type="button" class="todo-bell" aria-label="查看经营待办">
                <el-icon><Bell /></el-icon><sup v-if="todoTotal">{{ todoTotal > 99 ? '99+' : todoTotal }}</sup>
              </button>
            </template>
            <div class="todo-popover">
              <div class="todo-popover-title"><strong>经营待办</strong><span>{{ todoTotal ? `共 ${todoTotal} 项` : '暂无待办' }}</span></div>
              <button v-for="item in todoItems" :key="item.key" type="button" @click="router.push(item.path)"><span>{{ item.title }}</span><strong>{{ item.count }}</strong></button>
              <p v-if="!todoItems.length">当前没有需要处理的认证、商品、订单、售后或财务事项。</p>
            </div>
          </el-popover>
          <div class="system-status"><i></i><span>系统运行正常</span></div>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="30"><el-icon><UserFilled /></el-icon></el-avatar>
              <span class="username">{{ store.userInfo.nickname || store.userInfo.username || '管理员' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ store.userInfo.roleCode || 'ADMIN' }}</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <!-- 内容区 -->
      <div class="layout-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount, onMounted, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import {
  CreditCard,
  Bell,
  DataAnalysis,
  DArrowLeft,
  DArrowRight,
  Expand,
  Fold,
  Goods,
  Money,
  Monitor,
  OfficeBuilding,
  Setting,
  TrendCharts,
  Upload,
  User,
  UserFilled,
  Wallet,
} from '@element-plus/icons-vue'
import { getMe, logout as logoutApi } from '@/api/auth'
import { getAdminOrderWorkSummary } from '@/api/shop'
import { listMerchantProductReviews } from '@/api/shop'
import { listMerchants, listMerchantWithdrawals } from '@/api/merchant'
import { connectAdminOrderRealtime } from '@/utils/orderRealtime'
import { getShopBrand } from '@/api/shopBrand'
import { useAppStore } from '@/store'
import defaultLogo from '@/assets/lingqi-logo-mark.png'
import {
  ADMIN_SESSION_EXPIRED_EVENT,
  expireAdminSession,
  isAdminSessionExpired,
} from '@/utils/adminSession'
import { updateAdminBrowserLogo } from '@/utils/adminBrand'
import { adminPortalForAccount, adminPortalLoginPath, saveAdminPortal } from '@/utils/adminPortal'
import { MERCHANT_HOME_PATH, isMerchantWorkspacePath } from '@/utils/adminWorkspace'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const isCollapsed = ref(false)
const isDashboard = computed(() => route.path === '/dashboard')
const brand = reactive({ brandName: localStorage.getItem('admin_brand_name') || '灵启商城', logoUrl: '' })
const brandLogoLoadFailed = ref(false)
const todoSummary = reactive({ pendingShipment: 0, afterSale: 0, merchantCertification: 0, productReview: 0, finance: 0 })
const todoItems = computed(() => [
  store.hasPermission('shop:order') && { key: 'pendingShipment', title: '待发货订单', count: todoSummary.pendingShipment, path: '/shop/orders?orderState=PENDING_SHIPMENT' },
  store.hasPermission('shop:order') && { key: 'afterSale', title: '待处理售后', count: todoSummary.afterSale, path: '/shop/orders?orderState=AFTER_SALE' },
  store.userInfo?.merchantId && { key: 'merchantCertification', title: '入驻资料待完善', count: todoSummary.merchantCertification, path: '/merchant/profile' },
  !store.userInfo?.merchantId && store.hasPermission('system:manage') && store.hasPermission('shop:product') && { key: 'merchantCertification', title: '待认证商户', count: todoSummary.merchantCertification, path: '/shop/merchants' },
  store.hasPermission('shop:product-review') && { key: 'productReview', title: '待审核商户商品', count: todoSummary.productReview, path: '/shop/merchant-product-reviews' },
  store.hasPermission('finance:read') && { key: 'finance', title: store.userInfo?.merchantId ? '提现与发票待跟进' : '商户财务待处理', count: todoSummary.finance, path: '/audit/merchant-finance?tab=withdrawals' },
].filter((item) => item && Number(item.count || 0) > 0))
const todoTotal = computed(() => todoItems.value.reduce((sum, item) => sum + Number(item.count || 0), 0))
const initializedTodoKeys = new Set()
const updateTodoValues = (next) => {
  const increased = []
  Object.entries(next).forEach(([key, value]) => {
    const count = Math.max(0, Number(value || 0))
    if (initializedTodoKeys.has(key) && count > Number(todoSummary[key] || 0)) increased.push(count - Number(todoSummary[key] || 0))
    todoSummary[key] = count
    initializedTodoKeys.add(key)
  })
  if (increased.length) ElNotification({ title: '有新的经营待办', message: `新增 ${increased.reduce((sum, value) => sum + value, 0)} 项，请在右上角待办中查看。`, type: 'warning', duration: 5000 })
}
const sidebarLogoSrc = computed(() => brandLogoLoadFailed.value ? defaultLogo : (brand.logoUrl || defaultLogo))
const handleSidebarLogoError = () => {
  if (sidebarLogoSrc.value === defaultLogo) return
  brandLogoLoadFailed.value = true
  updateAdminBrowserLogo('')
}
const menuIcons = {
  CreditCard,
  DataAnalysis,
  Goods,
  Money,
  Monitor,
  OfficeBuilding,
  Setting,
  TrendCharts,
  Upload,
  User,
  Wallet,
}
const menuIcon = (name) => menuIcons[name] || Setting

// 后台菜单按运营人员的业务流程组织。页面地址和接口保持不变，避免影响旧链接与权限。
const businessMenus = [
  { key: 'dashboard', title: '工作台', icon: 'Monitor', path: '/dashboard' },
  {
    key: 'merchant-onboarding', title: '入驻与认证', icon: 'OfficeBuilding', items: [
      { title: '经营与结算资料', path: '/merchant/profile', merchantOnly: true },
      { title: '子账号与权限', path: '/merchant/staff', permission: 'merchant:staff-manage', merchantOnly: true },
    ],
  },
  {
    key: 'products', title: '商品与库存', icon: 'Goods', items: [
      { title: '商品管理', path: '/shop/products', permission: 'shop:product' },
      { title: '商户管理', path: '/shop/merchants', permission: 'system:manage' },
      { title: '商户商品审核', path: '/shop/merchant-product-reviews', permission: 'shop:product-review' },
      { title: '发货与退货地址', path: '/shop/service-addresses', permission: 'shop:product' },
      { title: '分类与规格', path: '/shop/categories', permission: 'shop:product' },
      { title: '商品评价', path: '/shop/reviews', permission: 'shop:product' },
      { title: '秒杀活动', path: '/tenant/flash-sales', permission: 'shop:product' },
    ],
  },
  {
    key: 'orders', title: '订单与售后', icon: 'DataAnalysis', items: [
      { title: '订单管理', path: '/shop/orders', permission: 'shop:order' },
      { title: '客服工单', path: '/shop/service-tickets', permission: 'shop:order' },
      { title: '订单奖金与利润追溯', path: '/audit/orders', permission: 'finance:read' },
      { title: 'ERP订单对接', path: '/tenant/erp', permission: 'config:integration' },
    ],
  },
  {
    key: 'members', title: '会员与团队', icon: 'User', items: [
      { title: '会员全景管理', path: '/members/list', permission: 'shop:member' },
      { title: '团队关系树', path: '/members/tree', permission: 'distribution:manage' },
      { title: '会员业绩概览', path: '/performance/overview', permission: 'distribution:manage' },
      { title: '业绩来源明细', path: '/performance/contributions', permission: 'distribution:manage' },
      { title: '团队业绩排名', path: '/performance/ranking', permission: 'distribution:manage' },
      { title: '移线记录', path: '/members/line-changes', permission: 'line-change:apply' },
    ],
  },
  {
    key: 'finance', title: '奖金与财务', icon: 'Wallet', items: [
      { title: '奖金记录', path: '/commission/records', permission: 'commission:manage' },
      { title: '待结算奖金（收货后7天）', path: '/commission/settle', permission: 'commission:manage' },
      { title: '会员余额账户', path: '/account/list', permission: 'distribution:manage' },
      { title: '余额流水', path: '/account/flows', permission: 'finance:read' },
      { title: '提现审核', path: '/withdraw/audit', permission: 'finance:manage' },
      { title: '提现记录', path: '/withdraw/list', permission: 'finance:read' },
      { title: '财务总览', path: '/audit/finance', permission: 'finance:read' },
      { title: '商户货款与总账', path: '/audit/merchant-finance', permission: 'finance:read' },
    ],
  },
  {
    key: 'operations', title: '商城设置', icon: 'OfficeBuilding', items: [
      { title: '商城视觉与页面', path: '/tenant/list', permission: 'config:shop' },
      { title: '直播运营中心', path: '/tenant/live-rooms', permission: 'shop:product' },
      { title: '商城资料与客服', path: '/tenant/profile', permission: 'config:shop' },
      { title: '消息运营', path: '/tenant/message-operations', permission: 'config:shop' },
      { title: '秒杀与复购模式', path: '/tenant/business-modes', permission: 'config:bonus' },
      { title: '协议与规则', path: '/tenant/legal', permission: 'config:shop' },
      { title: '客户奖金接入', path: '/tenant/bonus-config', permission: 'config:bonus' },
      { title: '会员端业绩查看权限', path: '/audit/settings', permission: 'config:bonus' },
    ],
  },
  {
    key: 'risk', title: '风控与审计', icon: 'DataAnalysis', items: [
      { title: '会员资金与订单全景', path: '/audit/person-profile', permission: 'finance:read' },
      { title: '后台操作日志', path: '/audit/operation-logs', permission: 'system:manage' },
    ],
  },
  {
    key: 'migration', title: '数据迁移', icon: 'Upload', items: [
      { title: '外部团队平移', path: '/import/agents', permission: 'import:manage' },
      { title: '历史订单导入', path: '/import/orders', permission: 'import:manage' },
    ],
  },
  {
    key: 'system', title: '系统与权限', icon: 'Setting', items: [
      { title: '后台账号与权限', path: '/system/admin-users', permission: 'system:manage' },
    ],
  },
]

const loadBrand = async () => {
  try {
    const res = await getShopBrand()
    brand.brandName = res.data?.brandName?.trim() || '灵启商城'
    brand.logoUrl = res.data?.logoUrl || ''
    brandLogoLoadFailed.value = false
    localStorage.setItem('admin_brand_name', brand.brandName)
    document.title = `${brand.brandName}管理后台`
    updateAdminBrowserLogo(brand.logoUrl)
  } catch {
    // 品牌读取失败时保留中性默认值，不影响后台使用。
  }
}

let sessionCheckTimer
let orderWorkTimer
let operationalTodoTimer
let stopOrderRealtime
let realtimeRefreshTimer
let lastActivityCheck = 0
let lastServerSessionCheck = 0
let serverSessionCheckPromise = null

const checkSessionDeadline = () => {
  if (store.token && isAdminSessionExpired()) {
    expireAdminSession('后台登录已超时，请重新登录')
    return false
  }
  return Boolean(store.token)
}

const checkServerSession = (force = false) => {
  if (!checkSessionDeadline()) return Promise.resolve(false)
  const now = Date.now()
  if (!force && now - lastServerSessionCheck < 60000) return Promise.resolve(true)
  if (serverSessionCheckPromise) return serverSessionCheckPromise

  lastServerSessionCheck = now
  serverSessionCheckPromise = getMe({ silentError: true })
    .then((res) => {
      store.setAuth({
        token: null,
        expireTime: store.expireTime,
        admin: res.data?.admin || store.userInfo,
        permissions: res.data?.permissions || store.permissions,
      })
      return true
    })
    // 401/会话失效由全局响应拦截器负责清理并跳转；短暂网络异常不误退出。
    .catch(() => false)
    .finally(() => { serverSessionCheckPromise = null })
  return serverSessionCheckPromise
}

const checkSessionOnActivity = () => {
  const now = Date.now()
  if (now - lastActivityCheck < 10000) return
  lastActivityCheck = now
  checkServerSession()
}

const checkSessionOnVisibility = () => {
  if (document.visibilityState === 'visible') {
    checkServerSession(true)
    loadAllTodos()
  }
}

const loadOrderWorkSummary = async () => {
  if (!store.token || !store.hasPermission('shop:order')) return
  try {
    const res = await getAdminOrderWorkSummary()
    const summary = {
      pendingShipment: Number(res.data?.pendingShipment || 0),
      afterSale: Number(res.data?.afterSale || 0),
    }
    updateTodoValues(summary)
    window.dispatchEvent(new CustomEvent('admin-order-work-summary', { detail: summary }))
  } catch {
    // 待办数字读取失败不影响后台使用，下一轮定时刷新会自动重试。
  }
}

const loadOperationalTodos = async () => {
  if (!store.token) return
  const jobs = []
  if (store.hasPermission('shop:product')) {
    jobs.push(listMerchants().then((res) => {
      const rows = res.data || []
      updateTodoValues({ merchantCertification: store.userInfo?.merchantId
        ? (rows.some((item) => item.auditStatus !== 'APPROVED') ? 1 : 0)
        : rows.filter((item) => item.auditStatus === 'PENDING').length })
    }))
  }
  if (store.hasPermission('shop:product-review')) {
    jobs.push(listMerchantProductReviews({ status: 'PENDING', pageNum: 1, pageSize: 1 })
      .then((res) => updateTodoValues({ productReview: Number(res.data?.total || 0) })))
  }
  if (store.hasPermission('finance:read')) {
    jobs.push(listMerchantWithdrawals().then((res) => {
      const active = new Set(['SUBMITTED', 'INVOICE_PENDING', 'READY_TO_PAY', 'PAYMENT_PROCESSING', 'PAYMENT_FAILED', 'RISK_FROZEN'])
      updateTodoValues({ finance: (res.data || []).filter((item) => active.has(item.status)).length })
    }))
  }
  await Promise.allSettled(jobs)
}

const loadAllTodos = () => Promise.allSettled([loadOrderWorkSummary(), loadOperationalTodos()])

const handleRealtimeOrderChange = () => {
  window.clearTimeout(realtimeRefreshTimer)
  realtimeRefreshTimer = window.setTimeout(() => {
    loadOrderWorkSummary()
    window.dispatchEvent(new CustomEvent('admin-order-changed'))
  }, 250)
}

const handleRealtimeStatus = (connected) => {
  window.clearInterval(orderWorkTimer)
  orderWorkTimer = null
  if (!connected) orderWorkTimer = window.setInterval(loadOrderWorkSummary, 30000)
}

const handleSessionExpired = () => {
  store.logout()
  ElMessage.closeAll()
  ElNotification.closeAll()
  ElMessageBox.close()
}

onMounted(() => {
  loadBrand()
  checkServerSession(true)
  loadAllTodos()
  sessionCheckTimer = window.setInterval(() => checkServerSession(true), 60000)
  operationalTodoTimer = window.setInterval(loadOperationalTodos, 60000)
  stopOrderRealtime = connectAdminOrderRealtime({
    onEvent: handleRealtimeOrderChange,
    onStatus: handleRealtimeStatus,
  })
  window.addEventListener('focus', checkSessionOnVisibility)
  window.addEventListener('pointerdown', checkSessionOnActivity, true)
  window.addEventListener(ADMIN_SESSION_EXPIRED_EVENT, handleSessionExpired)
  document.addEventListener('visibilitychange', checkSessionOnVisibility)
})

onBeforeUnmount(() => {
  window.clearInterval(sessionCheckTimer)
  window.clearInterval(orderWorkTimer)
  window.clearInterval(operationalTodoTimer)
  window.clearTimeout(realtimeRefreshTimer)
  stopOrderRealtime?.()
  window.removeEventListener('focus', checkSessionOnVisibility)
  window.removeEventListener('pointerdown', checkSessionOnActivity, true)
  window.removeEventListener(ADMIN_SESSION_EXPIRED_EVENT, handleSessionExpired)
  document.removeEventListener('visibilitychange', checkSessionOnVisibility)
})

// 当前激活的菜单
const activeMenu = computed(() => {
  if (route.path.startsWith('/members/detail/')) return '/members/list'
  if (route.path.startsWith('/account/detail/')) return '/account/list'
  if (route.path.startsWith('/import/result/')) return '/import/agents'
  return route.path
})

// 面包屑
const breadcrumbs = computed(() => {
  for (const menu of visibleBusinessMenus.value) {
    const item = menu.items?.find((entry) => entry.path === activeMenu.value)
    if (item) {
      const currentTitle = route.meta?.title
      return [
        { path: '', title: menu.title },
        { path: route.path, title: currentTitle && route.path !== item.path ? currentTitle : item.title },
      ]
    }
  }
  const matched = route.matched.filter((item) => item.meta && item.meta.title)
  return matched.map((item) => ({
    path: item.path,
    title: item.meta.title,
  }))
})

const hasMenuPermission = (item) => {
  if (item.merchantOnly && !store.userInfo?.merchantId) return false
  if (store.userInfo?.merchantId) {
    return isMerchantWorkspacePath(item.path) && (!item.permission || store.hasPermission(item.permission))
  }
  return !item.permission || store.hasPermission(item.permission)
}
const visibleBusinessMenus = computed(() => businessMenus
  .map((menu) => menu.items
    ? { ...menu, items: menu.items.filter(hasMenuPermission) }
    : (store.userInfo?.merchantId && menu.path === '/dashboard'
      ? { ...menu, title: '商户工作台', path: MERCHANT_HOME_PATH }
      : menu))
  .filter((menu) => menu.path || menu.items?.length))

watch(() => store.userInfo?.merchantId, (merchantId) => {
  if (merchantId && route.path === '/dashboard') router.replace(MERCHANT_HOME_PATH)
}, { immediate: true })

const handleCommand = async (command) => {
  if (command === 'logout') {
    const portal = saveAdminPortal(adminPortalForAccount(store.userInfo))
    try {
      await logoutApi()
    } finally {
      store.logout()
      ElMessage.success('已退出登录')
      router.replace(adminPortalLoginPath(portal))
    }
  }
}
</script>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
  display: flex;
  color: #1f2937;
  background: #f4f7fb;
}

.layout-container.dashboard-mode {
  color: #edf4ff;
  background: #020b18;
}

.layout-sidebar {
  width: 220px;
  background: #071326;
  box-shadow: 8px 0 28px rgba(0, 7, 19, .18);
  transition: width 0.24s ease;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  &.collapsed {
    width: 64px;
  }

  .logo {
    flex: 0 0 76px;
    height: 76px;
    display: flex;
    align-items: center;
    padding: 0 22px;
    background: rgba(9, 17, 37, .26);
    border-bottom: 1px solid rgba(255, 255, 255, .08);

    img {
      width: 38px;
      height: 38px;
      object-fit: contain;
      flex: 0 0 auto;
      border-radius: 11px;
      box-shadow: 0 6px 16px rgba(34, 53, 104, .35);
    }

    span {
      margin-left: 11px;
      max-width: 145px;
      overflow: hidden;
      color: #fff;
      color: #f5f7ff;
      font-size: 17px;
      font-weight: 750;
      letter-spacing: .3px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .el-menu {
    border-right: none;
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    overflow-x: hidden;
    scrollbar-width: thin;
    scrollbar-color: #405379 #111c36;

    &::-webkit-scrollbar { width: 7px; }
    &::-webkit-scrollbar-thumb { background: #405379; border-radius: 4px; }
    &::-webkit-scrollbar-track { background: #111c36; }

    :deep(.el-menu-item), :deep(.el-sub-menu__title) {
      height: 46px;
      margin: 4px 12px;
      padding: 0 15px !important;
      border-radius: 10px;
      font-size: 14px;
      transition: background .2s ease, color .2s ease;
    }

    :deep(.el-sub-menu .el-menu-item) {
      min-width: 0;
      margin: 2px 12px 2px 42px;
      padding-left: 14px !important;
      color: #9eabc2;
      font-size: 13px;
    }

    :deep(.el-menu-item:hover), :deep(.el-sub-menu__title:hover) {
      color: #fff !important;
      background: rgba(255, 255, 255, .09) !important;
    }

    :deep(.el-menu-item.is-active) {
      color: #fff !important;
      background: #153d7d !important;
      border: 1px solid rgba(82, 145, 255, .78);
      box-shadow: 0 8px 18px rgba(25, 79, 173, .28);
    }

    :deep(.el-sub-menu.is-opened > .el-sub-menu__title) { color: #fff; }

  }

  .sidebar-collapse {
    min-height: 42px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 9px;
    margin: 8px 12px 12px;
    color: #8ea0bc;
    font-size: 12px;
    border: 1px solid rgba(91, 119, 160, .24);
    border-radius: 9px;
    background: rgba(7, 21, 45, .76);
    cursor: pointer;
    transition: color .18s ease, background .18s ease, border-color .18s ease;

    &:hover {
      color: #fff;
      border-color: rgba(87, 145, 247, .48);
      background: rgba(20, 52, 103, .62);
    }

    &:focus-visible {
      outline: 2px solid #69a1ff;
      outline-offset: 2px;
    }
  }
}

.layout-main {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.layout-header {
  height: 72px;
  background: rgba(255, 255, 255, .92);
  border-bottom: 1px solid #e8edf5;
  box-shadow: 0 4px 18px rgba(32, 55, 93, .04);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;

  .header-left {
    display: flex;
    align-items: center;

    .collapse-btn {
      width: 34px;
      height: 34px;
      color: #64748b;
      font-size: 19px;
      line-height: 34px;
      text-align: center;
      border-radius: 9px;
      cursor: pointer;
      margin-right: 16px;
      transition: color .2s ease, background .2s ease;

      &:hover { color: #4f66df; background: #eef2ff; }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 22px;

    .todo-bell { position:relative; display:grid; width:38px; height:38px; place-items:center; color:#53657d; background:#f5f7fb; border:1px solid #e2e8f0; border-radius:10px; cursor:pointer; }
    .todo-bell:hover { color:#1556a3; border-color:#9dbce0; background:#eef5fd; }
    .todo-bell .el-icon { font-size:19px; }
    .todo-bell sup { position:absolute; top:-7px; right:-8px; min-width:20px; height:20px; padding:0 5px; color:#fff; background:#e5484d; border:2px solid #fff; border-radius:999px; font-size:10px; font-weight:800; line-height:16px; }

    .system-status {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      color: #718096;
      font-size: 12px;

      i {
        width: 7px;
        height: 7px;
        background: #29c38a;
        border-radius: 50%;
        box-shadow: 0 0 0 4px rgba(41, 195, 138, .12);
      }
    }

    .user-info {
      display: flex;
      align-items: center;
      cursor: pointer;

      .username {
        margin-left: 10px;
        color: #334155;
        font-size: 14px;
        font-weight: 600;
      }
    }
  }
}

:global(.todo-popover) { display:grid; gap:8px; }
:global(.todo-popover-title) { display:flex; align-items:center; justify-content:space-between; padding-bottom:9px; border-bottom:1px solid #edf0f4; }
:global(.todo-popover-title strong) { color:#27364f; font-size:15px; }
:global(.todo-popover-title span),:global(.todo-popover p) { color:#8a95a6; font-size:12px; }
:global(.todo-popover button) { display:flex; align-items:center; justify-content:space-between; width:100%; padding:10px 11px; color:#46566e; background:#f8fafc; border:1px solid #e7ecf2; border-radius:8px; cursor:pointer; }
:global(.todo-popover button:hover) { color:#1556a3; border-color:#b5cbe4; background:#f1f6fc; }
:global(.todo-popover button strong) { min-width:28px; color:#fff; background:#e5484d; border-radius:999px; line-height:24px; text-align:center; }

.layout-content {
  flex: 1;
  padding: 26px 30px 34px;
  background: #f4f7fb;
  overflow-y: auto;
}

.dashboard-mode .layout-content {
  padding: 16px 18px 24px;
  background-color: #020b18;
  background-image: url('@/assets/dashboard-command-bg.png');
  background-repeat: repeat-y;
  background-position: center top;
  background-size: 100% auto;
}

@media (max-width: 960px) {
  .layout-sidebar { width: 208px; }
  .layout-sidebar.collapsed { width: 64px; }
  .dashboard-mode .layout-content { padding: 14px; }
}
</style>
