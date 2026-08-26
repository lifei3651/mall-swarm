<template>
  <div class="app-shell" :class="[{ 'home-shell': isHome }, `layout-${layoutTemplate}`]">
    <header v-if="showGlobalChrome" class="site-header desktop-site-header">
      <RouterLink class="brand desktop-brand" to="/" aria-label="返回商城首页">
        <img v-if="brand.logoUrl" class="brand-logo" :src="brand.logoUrl" :alt="`${brand.brandName} Logo`" />
        <span v-else class="brand-mark">{{ brandShortName }}</span>
        <span>{{ brand.brandName }}</span>
      </RouterLink>

      <nav class="top-nav desktop-main-nav" aria-label="电脑端商城导航">
        <RouterLink
          v-for="item in bottomNavItems"
          :key="item.type"
          :to="item.path"
          :class="{ 'desktop-cart-link': item.type === 'cart' }"
        >
          <span>{{ item.label }}</span>
          <span v-if="item.type === 'cart' && count" class="desktop-cart-badge">{{ count > 99 ? '99+' : count }}</span>
        </RouterLink>
      </nav>

      <div class="desktop-auth-actions">
        <RouterLink v-if="isLoggedIn" class="desktop-account-link" to="/profile">
          <UserRound :size="17" />
          <span>{{ desktopAccountLabel }}</span>
        </RouterLink>
        <template v-else>
          <RouterLink class="desktop-login-link" :to="loginLocation">登录</RouterLink>
          <RouterLink class="desktop-register-link" to="/register">注册</RouterLink>
        </template>
      </div>
    </header>

    <main :class="{ 'home-main': isHome }">
      <RouterView />
    </main>

    <footer v-if="isHome" class="site-footer">
      <p>{{ legal.companyName || brand.brandName }}</p>
      <nav class="footer-links" aria-label="商城服务信息">
        <RouterLink to="/legal/after-sale">交易与售后</RouterLink>
        <RouterLink to="/legal/contact">联系客服</RouterLink>
        <RouterLink to="/legal/license">经营资质</RouterLink>
        <RouterLink to="/legal/agreement">用户协议</RouterLink>
        <RouterLink to="/legal/privacy">隐私政策</RouterLink>
      </nav>
      <p class="records">
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">{{ legal.icpNumber || '湘ICP备2026028410号-1' }}</a>
        <a v-if="safePoliceUrl" :href="safePoliceUrl" target="_blank" rel="noopener noreferrer">{{ legal.policeRecordNumber || '公安备案' }}</a>
      </p>
    </footer>

    <nav ref="bottomNavRef" v-if="showGlobalChrome" class="bottom-nav" :style="{ '--bottom-nav-columns': bottomNavColumns }">
      <RouterLink v-for="item in bottomNavItems" :key="item.type" :to="item.path" @touchend.prevent="navigateTo(item.path)">
        <span v-if="item.type === 'cart'" class="bottom-cart-icon">
          <ShoppingBag :size="20" />
          <span v-if="count" class="bottom-cart-badge">{{ count > 99 ? '99+' : count }}</span>
          <span v-if="cartFeedback" class="cart-add-feedback">{{ cartFeedback }}</span>
        </span>
        <component v-else :is="navIcon(item.type)" :size="20" />
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div v-if="authPrompt" class="global-auth-toast" role="status" aria-live="polite">{{ authPrompt }}</div>

    <div v-if="availableRelease" class="update-overlay" @click.self="dismissUpdate">
      <section class="update-dialog" role="dialog" aria-modal="true" aria-labelledby="update-title">
        <span class="update-badge">发现新版本</span>
        <h2 id="update-title">{{ brand.brandName }} {{ availableRelease.versionName }}</h2>
        <p>当前版本 {{ currentAndroidVersionName || currentAndroidVersionCode }}，建议下载新安装包后覆盖安装。</p>
        <ul v-if="availableRelease.notes.length">
          <li v-for="note in availableRelease.notes" :key="note">{{ note }}</li>
        </ul>
        <p v-if="updateError" class="update-error">{{ updateError }}</p>
        <button class="update-primary" type="button" @click="downloadUpdate">立即更新</button>
        <button v-if="!availableRelease.required" class="update-later" type="button" @click="dismissUpdate">稍后提醒</button>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Home, ShoppingBag, UserRound, Grid3x3, ClipboardList } from 'lucide-vue-next'
import { getHome, getLegalConfig, getMe } from '@/api/shop'
import { useCart } from '@/store/cart'
import { applyBrandConfig, currentBrandName, updatePageTitle } from '@/utils/brand'
import { currentAndroidVersionCode, currentAndroidVersionName, fetchAndroidRelease, hasAndroidUpdate, openAndroidDownload } from '@/utils/appRelease'
import { isNativeApp } from '@/utils/appEnvironment'
import { AUTH_REQUIRED_EVENT } from '@/utils/authNavigation'
import { applyShopSession, hasShopSession } from '@/utils/shopSession'
import { useVisualViewportFixedBottom } from '@/utils/visualViewportFixedBottom'
import { resolveBottomNav } from '@/utils/bottomNav'

const route = useRoute()
const router = useRouter()
const { count, addSequence, lastAddedQuantity } = useCart()
const cartFeedback = ref('')
let cartFeedbackTimer
const brand = ref({ brandName: currentBrandName(), logoUrl: '' })
provide('shopBrand', brand)
const legal = ref({})
const displayConfig = ref({})
const availableRelease = ref(null)
const updateError = ref('')
const authPrompt = ref('')
const isLoggedIn = ref(false)
const authMember = ref({})
const bottomNavRef = ref(null)
useVisualViewportFixedBottom(bottomNavRef)
let authPromptTimer
const isHome = computed(() => route.name === 'Home')
const isProductDetail = computed(() => route.name === 'ProductDetail')
const isCheckout = computed(() => route.name === 'Checkout')
const isAuthPage = computed(() => ['Login', 'Register'].includes(route.name))
const isBrandCulturePage = computed(() => route.name === 'BrandCulture')
const showGlobalChrome = computed(() => !isProductDetail.value && !isCheckout.value && !isAuthPage.value && !isBrandCulturePage.value)
const navIconMap = { home: Home, category: Grid3x3, cart: ShoppingBag, orders: ClipboardList, profile: UserRound }
const navIcon = (type) => navIconMap[type] || Home
const bottomNavItems = computed(() => {
  let configured = null
  let independent = false
  try {
    const extra = JSON.parse(displayConfig.value.extraConfigJson || '{}')
    independent = extra.bottomNavIndependent === 1
    if (Array.isArray(extra.bottomNav) && extra.bottomNav.length) {
      configured = extra.bottomNav
    }
  } catch (_) {}
  const legacyCategoryEnabled = configured
    ? true
    : Number(displayConfig.value.showBottomCategoryNav ?? 1) === 1
  const items = resolveBottomNav(configured, { legacyCategoryEnabled })
  if (!independent
      && displayConfig.value.layoutTemplate === 'product-focus'
      && Number(displayConfig.value.showBottomCategoryNav ?? 1) === 0) {
    const category = items.find((item) => item.type === 'category')
    if (category) category.enabled = true
  }
  return items.filter((item) => item.enabled !== false)
})
const bottomNavColumns = computed(() => Math.max(bottomNavItems.value.length, 1))
const brandShortName = computed(() => String(brand.value.brandName || '商城').slice(0, 2))
const desktopAccountLabel = computed(() => authMember.value.nickname
  || authMember.value.username
  || authMember.value.phone
  || '我的账号')
const loginLocation = computed(() => ({
  name: 'Login',
  query: route.fullPath && route.fullPath !== '/login' ? { redirect: route.fullPath } : {},
}))
const layoutTemplate = computed(() => ['standard', 'product-focus', 'category-focus'].includes(displayConfig.value.layoutTemplate)
  ? displayConfig.value.layoutTemplate
  : 'standard')
const safePoliceUrl = computed(() => /^https?:\/\//i.test(legal.value.policeRecordUrl || '') ? legal.value.policeRecordUrl : '')
const navigateTo = (path) => {
  if (route.path !== path) router.push(path)
}

const readCachedMember = () => {
  try {
    return JSON.parse(localStorage.getItem('shop_member') || '{}') || {}
  } catch (_) {
    return {}
  }
}

const syncAuthState = () => {
  isLoggedIn.value = hasShopSession()
  authMember.value = isLoggedIn.value ? readCachedMember() : {}
}

const showAuthPrompt = (event) => {
  authPrompt.value = event?.detail?.message || '请先登录'
  window.clearTimeout(authPromptTimer)
  authPromptTimer = window.setTimeout(() => { authPrompt.value = '' }, 1800)
}

const loadBrand = async () => {
  try {
    const res = await getHome()
    brand.value = applyBrandConfig(res.data || {})
    displayConfig.value = res.data?.displayConfig || {}
    legal.value = (await getLegalConfig()).data || {}
  } catch (_) {
    // 首页会显示可重试的错误态；外壳保留默认品牌，避免请求失败时出现未处理异常。
    brand.value = { brandName: currentBrandName(), logoUrl: '' }
    displayConfig.value = {}
    legal.value = {}
  } finally {
    updatePageTitle(route.name, brand.value.brandName)
  }
}

// 首页是公开页面，不能只依赖受保护页面的接口来发现旧会话失效。
// 每次商城启动且本地仍有登录凭证时主动校验一次，保证新设备登录后旧设备刷新立即退出。
const validateExistingSession = async () => {
  syncAuthState()
  if (!isLoggedIn.value) return
  try {
    const res = await getMe()
    authMember.value = { ...authMember.value, ...(res.data || {}) }
    applyShopSession(authMember.value)
  } catch (_) {
    // 401 由请求拦截器统一清理凭证并跳转登录；网络错误不阻塞商城启动。
  } finally {
    syncAuthState()
  }
}

const checkAndroidUpdate = async () => {
  if (!isNativeApp) return
  try {
    const release = await fetchAndroidRelease()
    const dismissedVersion = Number(sessionStorage.getItem('dismissed_android_version') || 0)
    if (hasAndroidUpdate(release) && (release.required || release.versionCode !== dismissedVersion)) {
      availableRelease.value = release
    }
  } catch (_) {
    // 版本检查失败不能影响商城启动，下次打开时自动重试。
  }
}

const dismissUpdate = () => {
  if (!availableRelease.value || availableRelease.value.required) return
  sessionStorage.setItem('dismissed_android_version', String(availableRelease.value.versionCode))
  availableRelease.value = null
}

const downloadUpdate = async () => {
  if (!availableRelease.value) return
  updateError.value = ''
  try {
    await openAndroidDownload(availableRelease.value.downloadUrl)
  } catch (e) {
    updateError.value = e.message || '更新页面打开失败，请稍后再试'
  }
}

watch(() => route.name, (name) => updatePageTitle(name, brand.value.brandName))
watch(() => route.fullPath, () => {
  const preserveAuthPrompt = route.name === 'Login' && route.query.authRequired === '1'
  if (!preserveAuthPrompt) {
    authPrompt.value = ''
    window.clearTimeout(authPromptTimer)
  }
  syncAuthState()
})
watch(addSequence, (sequence) => {
  if (!sequence) return
  cartFeedback.value = `+${lastAddedQuantity.value || 1}`
  window.clearTimeout(cartFeedbackTimer)
  cartFeedbackTimer = window.setTimeout(() => { cartFeedback.value = '' }, 1400)
})
onMounted(() => {
  window.addEventListener(AUTH_REQUIRED_EVENT, showAuthPrompt)
  window.addEventListener('storage', syncAuthState)
  syncAuthState()
  loadBrand()
  validateExistingSession()
  checkAndroidUpdate()
})
onBeforeUnmount(() => {
  window.removeEventListener(AUTH_REQUIRED_EVENT, showAuthPrompt)
  window.removeEventListener('storage', syncAuthState)
  window.clearTimeout(cartFeedbackTimer)
  window.clearTimeout(authPromptTimer)
})
</script>

<style scoped>
.home-main { min-height: 100vh; }
main { min-height: calc(100vh - 120px); }
.brand-logo { display:block; width:auto; max-width:136px; height:38px; object-fit:contain; }
.desktop-site-header { display:flex; }
.desktop-brand { flex:0 0 auto; min-width:0; }
.desktop-main-nav { align-items:center; }
.desktop-main-nav a { position:relative; display:inline-flex; align-items:center; min-height:40px; white-space:nowrap; }
.desktop-cart-link { padding-right:8px; }
.desktop-cart-badge { position:absolute; top:1px; right:-8px; display:grid; place-items:center; min-width:17px; height:17px; padding:0 4px; color:#fff; background:var(--brand-primary); border:2px solid #fff; border-radius:999px; font-size:9px; font-weight:800; line-height:1; }
.desktop-auth-actions { display:flex; align-items:center; gap:9px; margin-left:auto; }
.desktop-login-link,.desktop-register-link,.desktop-account-link { min-height:38px; display:inline-flex; align-items:center; justify-content:center; gap:6px; padding:0 14px; border-radius:10px; font-size:13px; font-weight:800; white-space:nowrap; }
.desktop-login-link,.desktop-account-link { color:var(--ink); background:#fff; border:1px solid var(--line); }
.desktop-register-link { color:#fff; background:var(--brand-primary); border:1px solid var(--brand-primary); }
.desktop-login-link:hover,.desktop-account-link:hover { color:var(--brand-primary); border-color:var(--brand-primary); }
.desktop-register-link:hover { background:var(--brand-primary-dark); border-color:var(--brand-primary-dark); }
.site-footer { padding: 18px 16px calc(62px + env(safe-area-inset-bottom)); text-align: center; color: #999; font-size: 12px; background: #f7f7f7; }
.footer-links { display: flex; justify-content: center; flex-wrap: wrap; gap: 8px 14px; margin-bottom: 9px; }
.footer-links a, .records a { color: #777; text-decoration: none; }
.site-footer p { margin: 6px 0; }
.records { display: flex; justify-content: center; flex-wrap: wrap; gap: 12px; }
.bottom-cart-icon { position:relative; display:inline-flex; }
.bottom-cart-badge { position:absolute; top:-9px; right:-14px; min-width:17px; height:17px; display:grid; place-items:center; padding:0 4px; color:#fff; background:#ef334e; border:2px solid #fff; border-radius:999px; font-size:10px; font-weight:800; line-height:1; }
.cart-add-feedback { position:absolute; right:-24px; top:-31px; padding:3px 7px; color:#fff; background:#ef334e; border-radius:999px; box-shadow:0 4px 12px rgba(239,51,78,.32); font-size:12px; font-weight:800; animation:cart-feedback 1.4s ease both; }
.global-auth-toast { position:fixed; z-index:12000; left:50%; bottom:calc(92px + env(safe-area-inset-bottom)); max-width:calc(100% - 48px); padding:11px 18px; color:#fff; background:rgba(17,24,39,.92); border-radius:999px; box-shadow:0 10px 30px rgba(0,0,0,.2); font-size:14px; font-weight:700; text-align:center; transform:translateX(-50%); animation:auth-toast-in .18s ease-out; }
@keyframes auth-toast-in { from{opacity:0;transform:translate(-50%,8px)} to{opacity:1;transform:translate(-50%,0)} }
@keyframes cart-feedback { 0%{opacity:0;transform:translateY(8px) scale(.75)} 20%{opacity:1;transform:translateY(0) scale(1.08)} 75%{opacity:1;transform:translateY(-4px) scale(1)} 100%{opacity:0;transform:translateY(-12px) scale(.9)} }
@media (min-width: 921px) { .site-footer { padding-bottom: 28px; } }
@media (max-width: 920px) { .desktop-site-header { display:none; } }
.update-overlay { position:fixed; inset:0; z-index:10000; display:grid; place-items:center; padding:20px; background:rgba(15,23,42,.55); backdrop-filter:blur(3px); }
.update-dialog { width:min(390px,100%); padding:24px; background:#fff; border-radius:20px; box-shadow:0 24px 70px rgba(0,0,0,.24); }
.update-badge { display:inline-flex; padding:4px 10px; color:#08724f; background:#e8f8f1; border-radius:999px; font-size:12px; font-weight:800; }
.update-dialog h2 { margin:12px 0 7px; font-size:20px; }
.update-dialog p { margin:0; color:#667085; font-size:13px; line-height:1.65; }
.update-dialog ul { margin:14px 0 0; padding:12px 12px 12px 30px; color:#475467; background:#f8fafc; border-radius:12px; font-size:13px; line-height:1.7; }
.update-dialog .update-error { margin-top:10px; color:#b42318; }
.update-primary,.update-later { width:100%; min-height:44px; margin-top:16px; border-radius:12px; font-size:14px; font-weight:800; cursor:pointer; }
.update-primary { color:#fff; background:#0f766e; border:0; }
.update-later { margin-top:8px; color:#667085; background:#fff; border:1px solid #d0d5dd; }
</style>
