<template>
  <div class="page public-wallet">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>我的余额</h2><span></span>
    </header>
    <section class="balance-card"><span>商城可用余额（元）</span><strong>{{ money(wallet.balance) }}</strong><p>可在商城结算时按页面支持的方式使用。</p></section>
    <RouterLink v-if="!wallet.realNameVerified" class="identity-entry" to="/profile/real-name"><BadgeCheck :size="21" /><span><strong>实名认证</strong><small>身份信息加密保护</small></span><ChevronRight :size="18" /></RouterLink>
    <section class="panel records-panel">
      <h3>余额明细</h3>
      <div v-if="loading" class="records-empty">正在加载…</div>
      <div v-else-if="error" class="records-error">{{ error }}<button type="button" @click="load">重新加载</button></div>
      <div v-else-if="!flows.length" class="records-empty">暂无余额记录</div>
      <article v-for="item in flows" :key="item.id" class="record-item">
        <div><strong>{{ item.remark || flowTypeName(item.changeType) }}</strong><small>{{ dateTime(item.createTime) }}</small></div>
        <div><strong :class="[1, 4].includes(item.changeType) ? 'amount-in' : 'amount-out'">{{ [1, 4].includes(item.changeType) ? '+' : '-' }}{{ money(item.amount) }}</strong><small>余额 {{ money(item.balanceAfter) }}</small></div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, BadgeCheck, ChevronRight } from 'lucide-vue-next'
import { getWalletSummary, listMyBalanceFlows } from '@/api/shop'
import { dateTime, money } from '@/utils/format'
const router = useRouter()
const wallet = ref({ balance: 0, realNameVerified: false })
const flows = ref([])
const loading = ref(true)
const error = ref('')
const flowTypeName = (type) => ({ 1: '入账', 2: '支付', 3: '转出', 4: '转入', 5: '扣减' }[type] || '余额变动')
const load = async () => {
  loading.value = true; error.value = ''
  try { const [summary, records] = await Promise.all([getWalletSummary(), listMyBalanceFlows()]); wallet.value = summary.data || wallet.value; flows.value = records.data || [] }
  catch (e) { error.value = e.message || '余额信息加载失败' }
  finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.public-wallet{width:min(680px,calc(100% - 28px))}.sub-page-head{display:grid;grid-template-columns:40px 1fr 40px;align-items:center;margin-bottom:14px}.sub-page-head h2{margin:0;text-align:center;font-size:19px}.sub-page-head button{width:40px;height:40px;display:grid;place-items:center;padding:0;background:#fff;border:0;border-radius:50%}.balance-card{padding:24px;color:#fff;background:linear-gradient(135deg,#e54b67,#a9183b);border-radius:19px}.balance-card span{color:rgba(255,255,255,.8);font-size:13px}.balance-card strong{display:block;margin:10px 0 8px;font-size:38px}.balance-card p{margin:0;color:rgba(255,255,255,.72);font-size:12px}.identity-entry{display:grid;grid-template-columns:30px 1fr auto;align-items:center;gap:8px;margin:12px 0;padding:13px 14px;color:#075985;background:#eff9ff;border:1px solid #bae6fd;border-radius:13px}.identity-entry strong,.identity-entry small{display:block}.identity-entry small{margin-top:3px;color:#397795;font-size:11px}.records-panel{margin-top:13px;border:0;border-radius:16px}.records-empty{padding:34px 0;color:var(--muted);text-align:center}.records-error{display:flex;justify-content:space-between;gap:12px;color:#b42318}.records-error button{color:#b42318;background:#fff;border:1px solid #f3b4ae;border-radius:7px}.record-item{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 0;border-bottom:1px solid var(--line)}.record-item>div:last-child{text-align:right}.record-item strong,.record-item small{display:block}.record-item small{margin-top:5px;color:var(--muted);font-size:11px}.amount-in{color:#15803d}.amount-out{color:#b42318}
</style>
