<template>
  <div class="page public-profile" :aria-busy="loading">
    <section class="account-card">
      <div class="avatar"><UserRound :size="30" /></div>
      <div>
        <h2>{{ loading ? '正在加载…' : displayName }}</h2>
        <p>账号：{{ loading ? '-' : accountName }}</p>
      </div>
      <RouterLink to="/profile/settings"><Settings :size="17" />账号设置</RouterLink>
    </section>

    <RouterLink class="wallet-card" to="/profile/wallet">
      <span><WalletCards :size="22" />商城可用余额</span><strong>{{ loading ? '—' : `¥${money(wallet.balance)}` }}</strong><small>查看余额明细</small>
    </RouterLink>

    <section class="order-card">
      <header><h3>我的订单</h3><RouterLink to="/orders">查看全部</RouterLink></header>
      <div class="order-grid">
        <RouterLink v-for="entry in orderEntries" :key="entry.key" :to="`/orders?tab=${entry.key}`">
          <span class="order-icon"><component :is="entry.icon" :size="24" /><em v-if="entry.count">{{ entry.count > 99 ? '99+' : entry.count }}</em></span>
          <span>{{ entry.label }}</span>
        </RouterLink>
      </div>
    </section>

    <section class="service-grid">
      <MessageCenterEntry />
      <RouterLink to="/profile/addresses"><MapPinned :size="25" /><strong>收货地址</strong><span>管理常用地址</span></RouterLink>
      <RouterLink to="/profile/security/change-login-password"><KeyRound :size="25" /><strong>登录密码</strong><span>保护账号安全</span></RouterLink>
      <RouterLink to="/profile/real-name"><BadgeCheck :size="25" /><strong>实名认证</strong><span>{{ wallet.realNameVerified ? '当前账号已认证' : '身份信息加密核验' }}</span></RouterLink>
      <RouterLink to="/legal/after-sale"><ShieldCheck :size="25" /><strong>售后规则</strong><span>查看服务说明</span></RouterLink>
      <RouterLink to="/legal/contact"><Headphones :size="25" /><strong>联系客服</strong><span>获取服务支持</span></RouterLink>
    </section>

    <p v-if="error" class="profile-error" role="alert">{{ error }}</p>
    <button class="logout-button" type="button" @click="confirmVisible = true">退出当前账号</button>

    <ConfirmDialog
      :visible="confirmVisible"
      title="退出当前账号？"
      message="退出后需要重新登录，当前设备中的购物车数据也会清除。"
      confirm-text="确认退出"
      cancel-text="继续使用"
      loading-text="正在退出…"
      icon-type="logout"
      is-danger
      :busy="loggingOut"
      @confirm="confirmLogout"
      @cancel="confirmVisible = false"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BadgeCheck, Headphones, KeyRound, MapPinned, MessageSquareText, PackageCheck, RotateCcw, Settings, ShieldCheck, Truck, UserRound, WalletCards } from 'lucide-vue-next'
import { getPublicProfile, getWalletSummary, logout } from '@/api/shop'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import MessageCenterEntry from '@/components/MessageCenterEntry.vue'
import { clearShopSession } from '@/utils/shopSession'
import { connectOrderRealtime } from '@/utils/orderRealtime'
import { money } from '@/utils/format'

const router = useRouter()
const loading = ref(true)
const loggingOut = ref(false)
const confirmVisible = ref(false)
const error = ref('')
const profile = ref({})
const wallet = ref({ balance: 0, realNameVerified: false })
let stopRealtime

const member = computed(() => profile.value.member || {})
const summary = computed(() => profile.value.orderSummary || {})
const displayName = computed(() => member.value.nickname || member.value.username || '商城用户')
const accountName = computed(() => member.value.username || member.value.phone || '-')
const orderEntries = computed(() => [
  { key: 'pending-payment', label: '待支付', icon: WalletCards, count: Number(summary.value.pendingPayment || 0) },
  { key: 'pending-shipment', label: '待发货', icon: PackageCheck, count: Number(summary.value.pendingShipment || 0) },
  { key: 'pending-receipt', label: '待收货', icon: Truck, count: Number(summary.value.pendingReceipt || 0) },
  { key: 'pending-review', label: '待评价', icon: MessageSquareText, count: Number(summary.value.pendingReview || 0) },
  { key: 'after-sale', label: '退款/售后', icon: RotateCcw, count: Number(summary.value.afterSale || 0) },
])

const loadProfile = async () => {
  try { const [profileRes, walletRes] = await Promise.all([getPublicProfile(), getWalletSummary()]); profile.value = profileRes.data || {}; wallet.value = walletRes.data || wallet.value }
  catch (e) { error.value = e.message || '个人中心加载失败' }
  finally { loading.value = false }
}

const confirmLogout = async () => {
  loggingOut.value = true
  try { await logout() } catch (_) {}
  clearShopSession({ clearCart: true })
  loggingOut.value = false
  confirmVisible.value = false
  await router.replace('/')
}

onMounted(() => {
  loadProfile()
  stopRealtime = connectOrderRealtime({ onEvent: loadProfile })
})
onBeforeUnmount(() => stopRealtime?.())
</script>

<style scoped>
.public-profile{width:min(860px,calc(100% - 28px));display:grid;gap:15px}.account-card{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:14px;padding:24px;color:#fff;background:linear-gradient(135deg,var(--brand-primary,#e7193f),var(--brand-primary-dark,#b70d2c));border-radius:21px}.avatar{width:54px;height:54px;display:grid;place-items:center;background:rgba(255,255,255,.18);border-radius:17px}.account-card h2{margin:0;font-size:22px}.account-card p{margin:6px 0 0;color:rgba(255,255,255,.75);font-size:13px}.account-card>a{display:flex;align-items:center;gap:6px;padding:9px 12px;color:#fff;background:rgba(255,255,255,.14);border-radius:11px;font-size:13px}
.order-card{padding:20px;background:#fff;border-radius:18px}.order-card header{display:flex;justify-content:space-between;align-items:center}.order-card h3{margin:0}.order-card header a{color:#7b8493;font-size:13px}.order-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:8px;margin-top:20px}.order-grid>a{display:flex;flex-direction:column;align-items:center;gap:8px;color:#344054;font-size:13px}.order-icon{position:relative;color:var(--brand-primary,#e7193f)}.order-icon em{position:absolute;top:-9px;right:-13px;min-width:17px;height:17px;padding:0 4px;color:#fff;background:#e5484d;border:2px solid #fff;border-radius:10px;font-size:10px;font-style:normal;line-height:13px;text-align:center}
.wallet-card{display:grid;grid-template-columns:1fr auto;gap:7px 12px;padding:19px 21px;color:#fff;background:linear-gradient(135deg,#172554,#3449a1);border-radius:17px}.wallet-card span{display:flex;align-items:center;gap:8px}.wallet-card strong{font-size:24px}.wallet-card small{grid-column:1/-1;color:rgba(255,255,255,.7)}
.service-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.service-grid>a{display:flex;flex-direction:column;gap:8px;padding:19px;color:var(--brand-primary,#e7193f);background:#fff;border-radius:16px}.service-grid strong{color:#253044;font-size:14px}.service-grid span{color:#98a2b3;font-size:12px}.profile-error{padding:12px 14px;color:#b42318;background:#fff1f0;border-radius:10px}.logout-button{justify-self:center;margin:12px 0 24px;padding:11px 24px;color:#a3302b;background:#fff;border:1px solid #ead7d5;border-radius:12px}
@media(max-width:620px){.public-profile{width:calc(100% - 20px)}.account-card{grid-template-columns:auto 1fr;padding:19px}.account-card>a{grid-column:1/-1;justify-content:center}.order-grid{gap:2px}.service-grid{grid-template-columns:1fr 1fr}}
</style>
