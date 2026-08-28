<template>
  <div class="page profile-page" :aria-busy="profileLoading">
      <div class="profile-content">
        <section class="identity-card" :class="identityInfo.className">
        <div class="identity-top">
          <div class="identity-avatar"><component :is="identityInfo.icon" :size="34" /></div>
          <div class="identity-main">
            <div class="identity-name-row">
              <h2>{{ profileLoading ? '正在加载...' : memberName }}</h2>
              <span v-if="activeAgent" class="rank-badge"><component :is="identityInfo.icon" :size="15" />{{ identityInfo.name }}</span>
            </div>
            <p>账号：{{ profileLoading ? '-' : accountName }}</p>
          </div>
          <div class="identity-actions">
            <RouterLink to="/profile/settings" class="identity-action"><Settings :size="16" />设置</RouterLink>
            <button type="button" class="identity-action" @click="openInvite"><Gift :size="16" />邀请</button>
          </div>
        </div>
        <div class="identity-stats" :class="{ 'without-team-performance': !showTeamPerformance }">
          <RouterLink to="/profile/wallet"><span>余额</span><strong>{{ walletLoading ? '加载中' : `¥${money(walletSummary.balance)}` }}</strong></RouterLink>
          <RouterLink v-if="showTeamPerformance" to="/profile/team"><span>本月团队业绩</span><strong>{{ teamPerformanceText }}</strong></RouterLink>
          <div><span>团队身份</span><strong>{{ profileLoading ? '加载中' : (activeAgent ? identityInfo.name : '首单后开通') }}</strong></div>
        </div>
        </section>

      <section class="panel order-hub">
        <div class="order-hub-head">
          <h3>我的订单</h3>
        </div>
        <div class="order-entry-grid">
          <RouterLink v-for="entry in orderEntries" :key="entry.key" :to="`/orders?tab=${entry.key}`" class="order-entry">
            <span class="order-entry-icon">
              <component :is="entry.icon" :size="25" />
              <em v-if="entry.count">{{ entry.count > 99 ? '99+' : entry.count }}</em>
            </span>
            <span>{{ entry.label }}</span>
          </RouterLink>
        </div>
      </section>

      <section class="profile-menu" :class="{ 'without-team-performance': !showTeamPerformance }">
        <MessageCenterEntry class="menu-tile" />
        <RouterLink to="/profile/wallet" class="menu-tile">
          <span class="tile-icon wallet-icon"><WalletCards :size="26" /></span>
          <span class="tile-label">余额</span>
        </RouterLink>
        <RouterLink v-if="showTeamPerformance" to="/profile/team" class="menu-tile">
          <span class="tile-icon team-icon"><ChartNoAxesCombined :size="26" /></span>
          <span class="tile-label">业绩</span>
        </RouterLink>
        <RouterLink to="/profile/security" class="menu-tile">
          <span class="tile-icon security-icon"><ShieldCheck :size="26" /></span>
          <span class="tile-label">支付安全</span>
          <i v-if="!walletLoading && !walletSummary.hasPaymentPassword" class="tile-badge">待设置</i>
        </RouterLink>
        <RouterLink to="/profile/addresses" class="menu-tile">
          <span class="tile-icon address-icon"><MapPinned :size="26" /></span>
          <span class="tile-label">收货地址</span>
        </RouterLink>
        <RouterLink to="/support" class="menu-tile">
          <span class="tile-icon service-icon"><Headphones :size="26" /></span>
          <span class="tile-label">客服工单</span>
        </RouterLink>
      </section>

        <p v-if="error" class="profile-error">{{ error }}</p>
      </div>
      <div class="profile-actions">
        <button class="logout-button" type="button" @click="logoutConfirmVisible = true">退出当前账号</button>
      </div>

    <ConfirmDialog
      :visible="logoutConfirmVisible"
      title="退出当前账号？"
      message="退出后需要重新登录，当前设备中的购物车数据也会清除。"
      confirm-text="确认退出"
      cancel-text="继续使用"
      loading-text="正在退出…"
      icon-type="logout"
      is-danger
      :busy="loggingOut"
      @confirm="confirmLogout"
      @cancel="closeLogoutConfirm"
    />
    <InviteDialog :visible="inviteDialogVisible" @close="inviteDialogVisible = false" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BadgeCheck,
  ChartNoAxesCombined,
  Crown,
  Gem,
  Gift,
  Headphones,
  MapPinned,
  Medal,
  MessageSquareText,
  PackageCheck,
  RotateCcw,
  Settings,
  ShieldCheck,
  Sparkles,
  Store,
  Truck,
  UserRound,
  WalletCards,
} from 'lucide-vue-next'
import { getProfile, getProfilePerformance, getWalletSummary, logout } from '@/api/shop'
import InviteDialog from '@/components/InviteDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import MessageCenterEntry from '@/components/MessageCenterEntry.vue'
import { money } from '@/utils/format'
import { clearShopSession } from '@/utils/shopSession'
import { connectOrderRealtime } from '@/utils/orderRealtime'

const router = useRouter()
const profileLoading = ref(true)
const walletLoading = ref(true)
const performanceLoading = ref(true)
const error = ref('')
const profile = ref({})
const performanceProfile = ref({})
const walletSummary = ref({ balance: 0, hasPaymentPassword: false })
const logoutConfirmVisible = ref(false)
const inviteDialogVisible = ref(false)
const loggingOut = ref(false)
let stopOrderRealtime = null
let fallbackPollTimer = null
let realtimeRefreshTimer = null
let disposed = false

const rankMap = {
  0: { name: '', icon: UserRound, className: 'rank-0' },
  1: { name: '会员', icon: BadgeCheck, className: 'rank-1' },
  2: { name: 'VIP会员', icon: Gem, className: 'rank-2' },
  3: { name: '店铺', icon: Store, className: 'rank-3' },
  4: { name: '代理', icon: Medal, className: 'rank-4' },
  5: { name: '一星董事', icon: Crown, className: 'rank-5' },
  6: { name: '二星董事', icon: Crown, className: 'rank-6' },
  7: { name: '三星董事', icon: Sparkles, className: 'rank-7' },
  8: { name: '合伙人', icon: Crown, className: 'rank-8' },
}
const activeAgent = computed(() => Number(profile.value.agent?.status || 0) === 1 ? profile.value.agent : null)
const identityInfo = computed(() => rankMap[Number(activeAgent.value?.agentLevel || 0)] || rankMap[0])
const memberName = computed(() => profile.value.member?.nickname || profile.value.agent?.agentName || '商城用户')
const accountName = computed(() => profile.value.member?.username || profile.value.member?.phone || '-')
const orderSummary = computed(() => profile.value.orderSummary || {})
const showTeamPerformance = computed(() => performanceProfile.value.canViewTeamPerformance === true)
const teamPerformanceText = computed(() => {
  if (performanceLoading.value) return '加载中'
  return `¥${money(performanceProfile.value.performance?.currentMonthTeamPerformance)}`
})
const orderEntries = computed(() => [
  { key: 'pending-payment', label: '待支付', icon: WalletCards, count: Number(orderSummary.value.pendingPayment || 0) },
  { key: 'pending-shipment', label: '待发货', icon: PackageCheck, count: Number(orderSummary.value.pendingShipment || 0) },
  { key: 'pending-receipt', label: '待收货', icon: Truck, count: Number(orderSummary.value.pendingReceipt || 0) },
  { key: 'pending-review', label: '待评价', icon: MessageSquareText, count: Number(orderSummary.value.pendingReview || 0) },
  { key: 'after-sale', label: '退款/售后', icon: RotateCcw, count: Number(orderSummary.value.afterSale || 0) },
])

const openInvite = () => {
  inviteDialogVisible.value = true
}

const fetchProfile = async () => {
  profileLoading.value = true
  try {
    profile.value = (await getProfile()).data || {}
  } catch (e) { error.value = e.message || '个人中心加载失败' }
  finally { profileLoading.value = false }
}

const fetchWallet = async () => {
  walletLoading.value = true
  try { walletSummary.value = (await getWalletSummary()).data || walletSummary.value }
  catch (e) { error.value ||= e.message || '余额信息加载失败' }
  finally { walletLoading.value = false }
}

const fetchPerformance = async () => {
  performanceLoading.value = true
  try { performanceProfile.value = (await getProfilePerformance()).data || {} }
  catch (e) { error.value ||= e.message || '团队业绩加载失败' }
  finally { performanceLoading.value = false }
}

const closeLogoutConfirm = () => {
  if (!loggingOut.value) logoutConfirmVisible.value = false
}

const confirmLogout = async () => {
  if (loggingOut.value) return
  loggingOut.value = true
  try { await logout() } finally {
    clearShopSession({ clearCart: true })
    logoutConfirmVisible.value = false
    loggingOut.value = false
    router.replace('/login')
  }
}

onMounted(() => {
  error.value = ''
  fetchProfile()
  fetchWallet()
  fetchPerformance()
  stopOrderRealtime = connectOrderRealtime({
    onEvent: () => {
      window.clearTimeout(realtimeRefreshTimer)
      realtimeRefreshTimer = window.setTimeout(fetchProfile, 250)
    },
    onStatus: (connected) => {
      window.clearInterval(fallbackPollTimer)
      fallbackPollTimer = null
      if (!connected && !disposed) fallbackPollTimer = window.setInterval(fetchProfile, 30000)
    },
  })
})
onBeforeUnmount(() => {
  disposed = true
  stopOrderRealtime?.()
  window.clearInterval(fallbackPollTimer)
  window.clearTimeout(realtimeRefreshTimer)
})
</script>

<style scoped>
.profile-page { width:min(760px,calc(100% - 28px)); }
.profile-content,.profile-actions { width:100%; }
.profile-actions { padding-top:14px; }
.identity-card { position:relative; overflow:hidden; padding:22px; color:#fff; border-radius:20px; box-shadow:0 14px 34px rgba(27,31,38,.18); }
.identity-card::after { content:""; position:absolute; width:210px; height:210px; right:-80px; top:-100px; border:34px solid rgba(255,255,255,.09); border-radius:50%; }
.rank-0 { background:linear-gradient(135deg,#64748b,#334155); }
.rank-1 { background:linear-gradient(135deg,#b7793c,#71451f); }
.rank-2 { background:linear-gradient(135deg,#16a5b5,#145b78); }
.rank-3 { background:linear-gradient(135deg,#16a36a,#096448); }
.rank-4 { background:linear-gradient(135deg,#5367df,#28368f); }
.rank-5 { background:linear-gradient(135deg,#d49b22,#8e5c08); }
.rank-6 { background:linear-gradient(135deg,#8b5cf6,#5130a9); }
.rank-7 { background:linear-gradient(135deg,#e65378,#9b2046); }
.rank-8 { background:linear-gradient(135deg,#202734,#090c12); }
.identity-top { position:relative; z-index:1; display:grid; grid-template-columns:54px minmax(0,1fr) auto; align-items:center; gap:13px; }
.identity-avatar { width:54px; height:54px; display:grid; place-items:center; border:1px solid rgba(255,255,255,.38); border-radius:50%; background:rgba(255,255,255,.16); backdrop-filter:blur(8px); }
.identity-main { min-width:0; }
.identity-name-row { display:flex; align-items:center; flex-wrap:wrap; gap:8px; }
.identity-name-row h2 { margin:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:20px; }
.rank-badge { display:inline-flex; align-items:center; gap:4px; padding:4px 8px; color:#fff; background:rgba(255,255,255,.18); border:1px solid rgba(255,255,255,.24); border-radius:999px; font-size:11px; font-weight:800; }
.identity-main p { margin:6px 0 0; color:rgba(255,255,255,.76); font-size:12px; }
.identity-actions { position:relative; z-index:2; display:flex; flex-direction:column; align-items:stretch; gap:6px; }
.identity-action { display:inline-flex; align-items:center; justify-content:center; gap:4px; padding:7px 9px; color:#fff; background:rgba(255,255,255,.15); border:1px solid rgba(255,255,255,.24); border-radius:999px; font-size:11px; }
.identity-stats { position:relative; z-index:1; display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:1px; margin-top:22px; padding-top:17px; border-top:1px solid rgba(255,255,255,.22); }
.identity-stats.without-team-performance { grid-template-columns:repeat(2,minmax(0,1fr)); }
.identity-stats > * { min-width:0; padding:0 10px; border-right:1px solid rgba(255,255,255,.18); text-align:center; }
.identity-stats > *:last-child { border-right:0; }
.identity-stats span,.identity-stats strong { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.identity-stats span { color:rgba(255,255,255,.72); font-size:11px; }
.identity-stats strong { margin-top:6px; color:#fff; font-size:14px; }
.order-hub { margin-top:14px; padding:16px 14px; border:0; border-radius:16px; }
.order-hub-head { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.order-hub-head h3 { margin:0; font-size:17px; }
.order-hub-head a { display:inline-flex; align-items:center; color:var(--muted); font-size:13px; }
.order-entry-grid { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); margin-top:17px; }
.order-entry { display:flex; flex-direction:column; align-items:center; gap:7px; min-width:0; color:var(--ink); font-size:12px; text-align:center; }
.order-entry-icon { position:relative; display:inline-grid; place-items:center; color:#252b37; }
.order-entry-icon em { position:absolute; top:-9px; right:-13px; display:grid; place-items:center; min-width:18px; height:18px; padding:0 4px; color:#fff; background:var(--brand-primary); border:2px solid #fff; border-radius:999px; font-size:9px; font-style:normal; font-weight:800; }
.profile-menu { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10px; margin-top:14px; }
.profile-menu.without-team-performance { grid-template-columns:repeat(3,minmax(0,1fr)); }
.menu-tile { position:relative; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:8px; aspect-ratio:1; padding:14px 8px; background:#fff; border-radius:16px; box-shadow:0 4px 14px rgba(31,41,55,.05); text-align:center; }
.tile-icon { width:48px; height:48px; display:grid; place-items:center; border-radius:14px; }
.tile-label { font-size:13px; font-weight:600; color:var(--ink); }
.tile-badge { position:absolute; top:8px; right:8px; padding:2px 6px; color:#c2410c; background:#fff2e8; border-radius:999px; font-size:9px; font-style:normal; }
.wallet-icon { color:#be3552; background:#fff0f3; }
.team-icon { color:#3867d6; background:#eef3ff; }
.security-icon { color:#0f8a62; background:#eaf8f3; }
.address-icon { color:#b26b13; background:#fff6e8; }
.profile-error { margin:14px 0 0; padding:12px 14px; color:#b42318; background:#fff1f0; border-radius:10px; }
.logout-button { width:100%; min-height:46px; color:#b42318; background:#fff; border:0; border-radius:14px; }
@media (max-width:560px) {
  .profile-page { min-height:100vh; min-height:100dvh; display:flex; flex-direction:column; padding-top:12px; }
  .profile-actions { margin-top:auto; }
  .identity-card { padding:18px 15px; border-radius:17px; }
  .identity-top { grid-template-columns:48px minmax(0,1fr) auto; gap:10px; }
  .identity-avatar { width:48px; height:48px; }
  .identity-name-row h2 { max-width:145px; font-size:18px; }
  .identity-main p span { display:none; }
  .identity-stats > * { padding:0 5px; }
  .identity-stats strong { font-size:12px; }
  .profile-menu { grid-template-columns:repeat(4,minmax(0,1fr)); gap:8px; }
}
@media (max-width:560px) and (max-height:700px) {
  .profile-page { padding-top:8px; }
  .identity-card { padding:14px 13px; }
  .identity-top { grid-template-columns:44px minmax(0,1fr) auto; gap:8px; }
  .identity-avatar { width:44px; height:44px; }
  .identity-stats { margin-top:14px; padding-top:12px; }
  .order-hub { margin-top:10px; padding:13px 11px; }
  .order-entry-grid { margin-top:12px; }
  .profile-menu { margin-top:10px; }
  .menu-tile { gap:5px; padding:8px 5px; border-radius:14px; }
  .tile-icon { width:42px; height:42px; }
  .profile-actions { padding-top:10px; }
  .logout-button { min-height:42px; }
}
</style>
