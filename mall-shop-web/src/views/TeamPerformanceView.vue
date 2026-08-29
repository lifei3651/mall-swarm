<template>
  <div class="page sub-page team-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>业绩概览</h2><span></span>
    </header>

    <div v-if="loading" class="empty">正在加载业绩数据...</div>
    <section v-else-if="!canView" class="empty locked-state">
      <LockKeyhole :size="38" />
      <h3>{{ profile.agent ? '业绩数据暂未开放' : '开通推广资格后可查询' }}</h3>
      <p>{{ profile.agent ? '该功能由商城后台按代理权限开放。' : '当前账号尚未开通推广资格，请按本商城规则申请或联系管理员。' }}</p>
    </section>
    <template v-else>
      <section class="performance-hero">
        <span>总业绩</span>
        <strong>¥{{ money(performance.totalTeamPerformance) }}</strong>
        <p>累计本人及团队的有效业绩，退款金额会同步扣减</p>
      </section>

      <section class="month-performance-card">
        <span>本月团队业绩</span>
        <strong>¥{{ money(performance.currentMonthTeamPerformance) }}</strong>
        <p>仅统计本月有效业绩；已退款和已冲销金额不计入结果。</p>
      </section>
    </template>
    <p v-if="error" class="page-error">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, LockKeyhole } from 'lucide-vue-next'
import { getProfilePerformance } from '@/api/shop'
import { money } from '@/utils/format'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const profile = ref({})
const performance = computed(() => profile.value.performance || {})
const canView = computed(() => profile.value.canViewTeamPerformance === true)
onMounted(async () => {
  try { profile.value = (await getProfilePerformance()).data || {} }
  catch (e) { error.value = e.message || '业绩数据加载失败' }
  finally { loading.value = false }
})
</script>

<style scoped>
.team-page { width:min(680px,calc(100% - 28px)); }
.sub-page-head { display:grid; grid-template-columns:40px 1fr 40px; align-items:center; margin-bottom:14px; }
.sub-page-head h2 { margin:0; text-align:center; font-size:19px; }.sub-page-head button{width:40px;height:40px;display:grid;place-items:center;padding:0;background:#fff;border:0;border-radius:50%}
.locked-state { min-height:340px; border:0; border-radius:16px; }.locked-state h3,.locked-state p{margin:0}
.performance-hero { padding:23px; color:#fff; background:linear-gradient(135deg,#396bd6,#233d91); border-radius:19px; box-shadow:0 12px 28px rgba(35,61,145,.2); }.performance-hero span{color:rgba(255,255,255,.78);font-size:13px}.performance-hero strong{display:block;margin:10px 0 8px;font-size:34px}.performance-hero p{margin:0;color:rgba(255,255,255,.7);font-size:12px}
.month-performance-card { margin-top:12px; padding:20px; background:#fff; border-radius:16px; }.month-performance-card span,.month-performance-card strong{display:block}.month-performance-card span{color:var(--muted);font-size:13px}.month-performance-card strong{margin-top:9px;font-size:26px}.month-performance-card p{margin:10px 0 0;color:var(--muted);font-size:12px;line-height:1.6}
.page-error{padding:12px 14px;color:#b42318;background:#fff1f0;border-radius:10px}
@media(max-width:560px){.team-page{padding-top:10px}}
</style>
