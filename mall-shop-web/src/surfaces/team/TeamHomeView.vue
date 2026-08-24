<template>
  <div class="team-home">
    <section class="team-hero">
      <div>
        <span class="eyebrow">团队服务中心</span>
        <h1>{{ memberName }}</h1>
        <p>登录账号：{{ accountName }}</p>
      </div>
      <span class="level-badge">{{ levelName }}</span>
    </section>

    <section v-if="!loading && !hasRelationship" class="relationship-card">
      <div>
        <strong>首次进入，请确认您的邀请关系</strong>
        <p>该关系只可绑定一次，提交后不能由本人修改，请核对无误后再确认。</p>
      </div>
      <form @submit.prevent="bindRelationship">
        <label for="team-invite-code">邀请码</label>
        <input id="team-invite-code" v-model.trim="inviteCode" maxlength="8" autocomplete="off" placeholder="请输入8位邀请码" />
        <button type="submit" :disabled="binding">{{ binding ? '正在确认…' : '确认关系' }}</button>
      </form>
    </section>

    <section class="metric-grid" :aria-busy="loading">
      <RouterLink to="/profile/wallet" class="metric-card wallet">
        <span>可用奖金余额</span><strong>{{ loading ? '—' : `¥${money(wallet.balance)}` }}</strong><small>查看明细与提现</small>
      </RouterLink>
      <RouterLink to="/profile/team" class="metric-card">
        <span>本月团队业绩</span><strong>{{ loading ? '—' : `¥${money(performance.currentMonthTeamPerformance)}` }}</strong><small>查看业绩口径</small>
      </RouterLink>
      <RouterLink to="/profile/team" class="metric-card">
        <span>累计团队业绩</span><strong>{{ loading ? '—' : `¥${money(performance.totalTeamPerformance)}` }}</strong><small>退款与冲销同步扣减</small>
      </RouterLink>
    </section>

    <section class="action-grid">
      <MessageCenterEntry />
      <RouterLink to="/invite"><UserRoundPlus :size="27" /><strong>邀请会员</strong><span>查看邀请码和分享入口</span></RouterLink>
      <RouterLink to="/profile/team"><ChartNoAxesCombined :size="27" /><strong>团队业绩</strong><span>查看本人和团队业绩</span></RouterLink>
      <RouterLink to="/profile/wallet"><WalletCards :size="27" /><strong>奖金账户</strong><span>余额、流水与提现</span></RouterLink>
      <RouterLink to="/profile/security"><ShieldCheck :size="27" /><strong>资金安全</strong><span>管理支付密码</span></RouterLink>
    </section>

    <section class="team-notice">
      <ShieldCheck :size="21" />
      <p>团队关系、业绩和奖金以后台审核后的有效记录为准；退款、撤销或异常订单会按制度进行冲销。</p>
    </section>
    <p v-if="error" class="team-error" role="alert">{{ error }}</p>
    <button class="logout" type="button" @click="signOut">退出团队服务中心</button>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChartNoAxesCombined, ShieldCheck, UserRoundPlus, WalletCards } from 'lucide-vue-next'
import { bindTeamInvitation, getProfile, getProfilePerformance, getWalletSummary, logout } from '@/api/shop'
import { money } from '@/utils/format'
import { clearShopSession } from '@/utils/shopSession'
import MessageCenterEntry from '@/components/MessageCenterEntry.vue'

const router = useRouter()
const loading = ref(true)
const binding = ref(false)
const error = ref('')
const profile = ref({})
const performanceProfile = ref({})
const wallet = ref({ balance: 0 })
const inviteCode = ref('')

const member = computed(() => profile.value.member || {})
const agent = computed(() => profile.value.agent || {})
const performance = computed(() => performanceProfile.value.performance || {})
const memberName = computed(() => member.value.nickname || agent.value.agentName || '团队会员')
const accountName = computed(() => member.value.username || member.value.phone || '-')
const hasRelationship = computed(() => Boolean(member.value.inviterId))
const levelNames = ['普通会员', '会员', 'VIP会员', '店铺', '代理', '一星董事', '二星董事', '三星董事', '合伙人']
const levelName = computed(() => agent.value.status === 1 ? (levelNames[Number(agent.value.agentLevel)] || '团队会员') : '待开通')

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const [profileRes, performanceRes, walletRes] = await Promise.all([getProfile(), getProfilePerformance(), getWalletSummary()])
    profile.value = profileRes.data || {}
    performanceProfile.value = performanceRes.data || {}
    wallet.value = walletRes.data || { balance: 0 }
  } catch (e) {
    error.value = e.message || '团队服务数据加载失败'
  } finally {
    loading.value = false
  }
}

const bindRelationship = async () => {
  error.value = ''
  if (!/^[A-Za-z0-9]{8}$/.test(inviteCode.value)) {
    error.value = '请输入正确的8位邀请码'
    return
  }
  binding.value = true
  try {
    await bindTeamInvitation(inviteCode.value)
    inviteCode.value = ''
    await load()
  } catch (e) {
    error.value = e.message || '邀请关系确认失败'
  } finally {
    binding.value = false
  }
}

const signOut = async () => {
  try { await logout() } catch (_) {}
  clearShopSession()
  await router.replace('/login')
}

onMounted(load)
</script>

<style scoped>
.team-home{width:min(980px,calc(100% - 30px));margin:0 auto;padding:30px 0}.team-hero{display:flex;align-items:flex-start;justify-content:space-between;padding:30px;color:#fff;background:linear-gradient(135deg,#172554,#283f91 62%,var(--brand-primary,#e7193f));border-radius:24px;box-shadow:0 18px 40px rgba(23,37,84,.2)}.eyebrow{color:rgba(255,255,255,.7);font-size:12px;letter-spacing:1.5px}.team-hero h1{margin:9px 0 5px;font-size:27px}.team-hero p{margin:0;color:rgba(255,255,255,.72);font-size:13px}.level-badge{padding:8px 13px;background:rgba(255,255,255,.15);border:1px solid rgba(255,255,255,.22);border-radius:20px;font-size:13px}
.relationship-card{display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-top:15px;padding:20px;background:#fff9e8;border:1px solid #f2d990;border-radius:17px}.relationship-card strong{color:#7a4d00}.relationship-card p{margin:7px 0 0;color:#8b6b2e;font-size:13px;line-height:1.6}.relationship-card form{display:grid;grid-template-columns:1fr auto;gap:8px}.relationship-card label{grid-column:1/-1;color:#6f531c;font-size:12px}.relationship-card input{height:43px;padding:0 12px;border:1px solid #dfc371;border-radius:10px}.relationship-card button{padding:0 17px;color:#fff;background:#9a6700;border:0;border-radius:10px;font-weight:700}
.metric-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:13px;margin-top:15px}.metric-card{display:flex;flex-direction:column;padding:21px;color:#687386;background:#fff;border:1px solid #e9ecf1;border-radius:18px}.metric-card strong{margin:10px 0 7px;color:#17202e;font-size:25px}.metric-card small{color:#98a2b3}.metric-card.wallet{color:var(--brand-primary,#e7193f);background:var(--brand-primary-soft,#fff2f4);border-color:color-mix(in srgb,var(--brand-primary,#e7193f) 15%,#fff)}
.action-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:13px;margin-top:15px}.action-grid a{display:flex;flex-direction:column;gap:8px;padding:20px;color:#344ea0;background:#fff;border-radius:17px}.action-grid strong{color:#263147}.action-grid span{color:#98a2b3;font-size:12px;line-height:1.5}.team-notice{display:flex;align-items:flex-start;gap:10px;margin-top:15px;padding:15px 18px;color:#536078;background:#eaf0ff;border-radius:14px}.team-notice p{margin:0;font-size:13px;line-height:1.7}.team-error{padding:12px 14px;color:#b42318;background:#fff1f0;border-radius:10px}.logout{display:block;margin:24px auto 0;padding:10px 18px;color:#8a3b37;background:#fff;border:1px solid #ead7d5;border-radius:11px}
@media(max-width:720px){.team-home{width:calc(100% - 20px);padding:15px 0}.team-hero{padding:22px;border-radius:19px}.relationship-card{grid-template-columns:1fr}.metric-grid{grid-template-columns:1fr}.action-grid{grid-template-columns:1fr 1fr}.action-grid a{padding:17px}.team-hero h1{font-size:23px}}
</style>
