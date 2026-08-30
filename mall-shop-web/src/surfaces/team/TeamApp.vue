<template>
  <div class="team-shell">
    <header class="team-header">
      <RouterLink class="team-brand" to="/">
        <img v-if="brand.logoUrl" :src="brand.logoUrl" :alt="`${brand.brandName} Logo`" />
        <span v-else>{{ shortName }}</span>
        <strong>{{ brand.brandName }}团队服务中心</strong>
      </RouterLink>
      <nav v-if="loggedIn" aria-label="团队服务导航">
        <RouterLink to="/">概览</RouterLink>
        <RouterLink to="/invite">邀请</RouterLink>
        <RouterLink to="/profile/team">业绩</RouterLink>
        <RouterLink to="/profile/wallet">奖金与提现</RouterLink>
      </nav>
      <RouterLink v-else class="login-link" to="/login">登录</RouterLink>
    </header>

    <main><RouterView /></main>

    <nav v-if="loggedIn && showBottomNav" class="team-bottom-nav" aria-label="手机端团队服务导航">
      <RouterLink to="/"><LayoutDashboard :size="20" /><span>概览</span></RouterLink>
      <RouterLink to="/invite"><UserRoundPlus :size="20" /><span>邀请</span></RouterLink>
      <RouterLink to="/profile/team"><ChartNoAxesCombined :size="20" /><span>业绩</span></RouterLink>
      <RouterLink to="/profile/wallet"><WalletCards :size="20" /><span>奖金</span></RouterLink>
    </nav>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChartNoAxesCombined, LayoutDashboard, UserRoundPlus, WalletCards } from 'lucide-vue-next'
import { getHome, getMe } from '@/api/shop'
import { applyBrandConfig, currentBrandName, updatePageTitle } from '@/utils/brand'
import { applyShopSession, hasShopSession } from '@/utils/shopSession'
import { installMobileViewport } from '@/utils/mobileViewport'

const route = useRoute()
const brand = ref({ brandName: currentBrandName(), logoUrl: '' })
provide('shopBrand', brand)
const loggedIn = ref(hasShopSession())
const shortName = computed(() => String(brand.value.brandName || '商城').slice(0, 2))
const showBottomNav = computed(() => !['Login', 'Register', 'ForgotPassword'].includes(route.name))
let releaseMobileViewport

const syncSession = () => { loggedIn.value = hasShopSession() }
const bootstrap = async () => {
  try {
    brand.value = applyBrandConfig((await getHome()).data || {})
  } catch (_) {
    brand.value = { brandName: currentBrandName(), logoUrl: '' }
  }
  if (loggedIn.value) {
    try { applyShopSession((await getMe()).data || {}) } catch (_) {}
  }
  syncSession()
  updatePageTitle(route.name, brand.value.brandName)
}

watch(() => route.fullPath, syncSession)
watch(() => route.name, (name) => updatePageTitle(name, brand.value.brandName))
onMounted(() => {
  releaseMobileViewport = installMobileViewport(720)
  window.addEventListener('storage', syncSession)
  bootstrap()
})
onBeforeUnmount(() => {
  releaseMobileViewport?.()
  window.removeEventListener('storage', syncSession)
})
</script>

<style scoped>
.team-shell{min-height:100vh;padding-bottom:70px;background:#f4f6fa;color:#17202e}.team-header{position:sticky;top:0;z-index:20;display:flex;align-items:center;gap:24px;min-height:66px;padding:0 max(20px,calc((100vw - 1120px)/2));background:rgba(255,255,255,.96);border-bottom:1px solid #e8ebf0;backdrop-filter:blur(12px)}.team-brand{display:flex;align-items:center;gap:10px;color:#17202e}.team-brand img,.team-brand>span{width:38px;height:38px;display:grid;place-items:center;object-fit:contain;color:#fff;background:var(--brand-primary,#e7193f);border-radius:11px;font-size:12px}.team-brand strong{font-size:16px}.team-header nav{display:flex;align-self:stretch;margin-left:auto}.team-header nav a{display:flex;align-items:center;padding:0 16px;color:#5a6473;border-bottom:3px solid transparent}.team-header nav a.router-link-exact-active{color:var(--brand-primary,#e7193f);border-bottom-color:var(--brand-primary,#e7193f);font-weight:700}.login-link{margin-left:auto;padding:9px 18px;color:#fff;background:var(--brand-primary,#e7193f);border-radius:10px}.team-shell main{min-height:calc(100vh - 66px)}.team-bottom-nav{display:none}
@media(max-width:720px){.team-shell{position:fixed;inset:0 auto auto 0;width:var(--shop-visual-viewport-width,100vw);height:var(--shop-visual-viewport-height,100dvh);min-height:0;display:grid;grid-template-rows:auto minmax(0,1fr) auto;overflow:hidden;padding-bottom:0}.team-header{min-height:58px;padding:0 15px}.team-brand strong{font-size:14px}.team-header nav{display:none}.team-shell main{min-height:0;overflow-x:hidden;overflow-y:auto;overscroll-behavior-y:contain;-webkit-overflow-scrolling:touch;scroll-padding-bottom:max(18px,env(safe-area-inset-bottom))}.team-bottom-nav{position:relative;z-index:30;display:grid;grid-template-columns:repeat(4,1fr);padding:7px 0 calc(7px + env(safe-area-inset-bottom));background:#fff;border-top:1px solid #e8ebf0}.team-bottom-nav a{display:flex;flex-direction:column;align-items:center;gap:3px;color:#7b8493;font-size:11px}.team-bottom-nav a.router-link-exact-active{color:var(--brand-primary,#e7193f)}:global(html.shop-mobile-keyboard-open .team-shell){grid-template-rows:auto minmax(0,1fr)}:global(html.shop-mobile-keyboard-open .team-shell main){padding-bottom:var(--shop-keyboard-inset,0px);scroll-padding-bottom:calc(var(--shop-keyboard-inset,0px) + 24px)}:global(html.shop-mobile-keyboard-open .team-bottom-nav){display:none}}
</style>
