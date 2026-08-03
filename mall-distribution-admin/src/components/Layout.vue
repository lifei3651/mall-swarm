<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <div class="layout-sidebar" :class="{ collapsed: isCollapsed }">
      <div class="logo">
        <img :src="brand.logoUrl || defaultLogo" :alt="`${brand.brandName} Logo`" />
        <span v-if="!isCollapsed">{{ brand.brandName }}</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
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
    </div>

    <!-- 主内容区 -->
    <div class="layout-main">
      <!-- 头部 -->
      <div class="layout-header">
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
import { ref, computed, onBeforeUnmount, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import {
  CreditCard,
  DataAnalysis,
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
import { getShopBrand } from '@/api/shopBrand'
import { useAppStore } from '@/store'
import defaultLogo from '@/assets/logo-small.svg'
import {
  ADMIN_SESSION_EXPIRED_EVENT,
  expireAdminSession,
  isAdminSessionExpired,
} from '@/utils/adminSession'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const isCollapsed = ref(false)
const brand = reactive({ brandName: localStorage.getItem('admin_brand_name') || '商城', logoUrl: '' })
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
    key: 'products', title: '商品与库存', icon: 'Goods', items: [
      { title: '商品管理', path: '/shop/products', permission: 'shop:product' },
      { title: '分类与规格', path: '/shop/categories', permission: 'shop:product' },
      { title: '商品评价', path: '/shop/reviews', permission: 'shop:product' },
    ],
  },
  {
    key: 'orders', title: '订单与售后', icon: 'DataAnalysis', items: [
      { title: '订单处理与售后', path: '/shop/orders', permission: 'shop:order' },
      { title: '订单奖金与利润追溯', path: '/audit/orders', permission: 'finance:read' },
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
    ],
  },
  {
    key: 'operations', title: '经营与规则设置', icon: 'OfficeBuilding', items: [
      { title: '商城品牌与界面', path: '/tenant/list', permission: 'config:manage' },
      { title: '奖金与钱包规则', path: '/tenant/bonus-config', permission: 'config:manage' },
      { title: '会员端业绩查看权限', path: '/audit/settings', permission: 'config:manage' },
      { title: 'ERP对接', path: '/tenant/erp', permission: 'config:manage' },
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
    brand.brandName = res.data?.brandName?.trim() || '商城'
    brand.logoUrl = res.data?.logoUrl || ''
    localStorage.setItem('admin_brand_name', brand.brandName)
  } catch {
    // 品牌读取失败时保留中性默认值，不影响后台使用。
  }
}

let sessionCheckTimer
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
    .then(() => true)
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
  if (document.visibilityState === 'visible') checkServerSession(true)
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
  sessionCheckTimer = window.setInterval(() => checkServerSession(true), 60000)
  window.addEventListener('focus', checkSessionOnVisibility)
  window.addEventListener('pointerdown', checkSessionOnActivity, true)
  window.addEventListener(ADMIN_SESSION_EXPIRED_EVENT, handleSessionExpired)
  document.addEventListener('visibilitychange', checkSessionOnVisibility)
})

onBeforeUnmount(() => {
  window.clearInterval(sessionCheckTimer)
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

const hasMenuPermission = (item) => !item.permission || store.hasPermission(item.permission)
const visibleBusinessMenus = computed(() => businessMenus
  .map((menu) => menu.items
    ? { ...menu, items: menu.items.filter(hasMenuPermission) }
    : menu)
  .filter((menu) => menu.path || menu.items?.length))

const handleCommand = async (command) => {
  if (command === 'logout') {
    try {
      await logoutApi()
    } finally {
      store.logout()
      ElMessage.success('已退出登录')
      router.replace('/login')
    }
  }
}
</script>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
  display: flex;
}

.layout-sidebar {
  width: 220px;
  background-color: #304156;
  transition: width 0.3s;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  &.collapsed {
    width: 64px;
  }

  .logo {
    flex: 0 0 60px;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #2b2f3a;

    img {
      width: 34px;
      height: 34px;
      object-fit: contain;
      flex: 0 0 auto;
    }

    span {
      margin-left: 9px;
      max-width: 145px;
      overflow: hidden;
      color: #fff;
      font-size: 16px;
      font-weight: 600;
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
    scrollbar-color: #6b7c93 #304156;

    &::-webkit-scrollbar { width: 7px; }
    &::-webkit-scrollbar-thumb { background: #6b7c93; border-radius: 4px; }
    &::-webkit-scrollbar-track { background: #304156; }
  }
}

.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.layout-header {
  height: 60px;
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;

  .header-left {
    display: flex;
    align-items: center;

    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
      margin-right: 20px;
    }
  }

  .header-right {
    .user-info {
      display: flex;
      align-items: center;
      cursor: pointer;

      .username {
        margin-left: 10px;
        font-size: 14px;
      }
    }
  }
}

.layout-content {
  flex: 1;
  padding: 20px;
  background-color: #f0f2f5;
  overflow-y: auto;
}
</style>
