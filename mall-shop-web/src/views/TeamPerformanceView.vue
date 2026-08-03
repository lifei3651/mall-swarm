<template>
  <div class="page sub-page team-page">
    <header class="sub-page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h2>团队业绩</h2><span></span>
    </header>

    <div v-if="loading" class="empty">正在加载业绩数据...</div>
    <section v-else-if="!canView" class="empty locked-state"><LockKeyhole :size="38" /><h3>团队业绩暂未开放</h3><p>该功能由商城后台按会员权限开放。</p></section>
    <template v-else>
      <section class="performance-hero">
        <span>本月团队业绩</span>
        <strong>¥{{ money(performance.teamPerformance) }}</strong>
        <p>统计本人及无限层团队本月产生的有效商品金额</p>
      </section>

      <section class="metric-list">
        <article><span class="metric-icon personal"><UserRound :size="21" /></span><div><small>本月个人业绩</small><strong>¥{{ money(performance.personalPerformance) }}</strong></div></article>
        <article><span class="metric-icon units"><PackageCheck :size="21" /></span><div><small>个人有效商品件数</small><strong>{{ performance.personalOrderCount || 0 }} 件</strong></div></article>
        <article><span class="metric-icon team"><UsersRound :size="21" /></span><div><small>团队有效商品件数</small><strong>{{ performance.teamOrderCount || 0 }} 件</strong></div></article>
        <article><span class="metric-icon active"><Activity :size="21" /></span><div><small>团队成员 / 活跃成员</small><strong>{{ performance.teamMemberCount || 0 }} / {{ performance.activeMemberCount || 0 }} 人</strong></div></article>
      </section>

      <section class="panel level-panel">
        <div class="panel-title"><h3>团队分层业绩</h3><span>本月</span></div>
        <div class="level-row"><span><i>1</i>直属团队</span><strong>¥{{ money(performance.level1Performance) }}</strong></div>
        <div class="level-row"><span><i>2</i>第二层团队</span><strong>¥{{ money(performance.level2Performance) }}</strong></div>
        <div class="level-row"><span><i>3</i>第三层团队</span><strong>¥{{ money(performance.level3Performance) }}</strong></div>
        <p>总团队业绩按无限层累计；这里的前三层仅用于查看团队结构，不代表只统计三层。</p>
      </section>

      <section class="panel growth-panel">
        <div><span>本月业绩变化</span><strong :class="{ down: Number(performance.performanceGrowthRate || 0) < 0 }">{{ growthText }}</strong></div>
        <p>业绩、有效件数和级别会随支付、退款及后台正式调整实时更新。</p>
      </section>
    </template>
    <p v-if="error" class="page-error">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Activity, ArrowLeft, LockKeyhole, PackageCheck, UserRound, UsersRound } from 'lucide-vue-next'
import { getProfile } from '@/api/shop'
import { money } from '@/utils/format'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const profile = ref({})
const performance = computed(() => profile.value.performance || {})
const canView = computed(() => profile.value.canViewTeamPerformance === true)
const growthText = computed(() => {
  const value = Number(performance.value.performanceGrowthRate || 0)
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`
})
onMounted(async () => {
  try { profile.value = (await getProfile()).data || {} }
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
.metric-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; margin-top:12px; }.metric-list article{display:grid;grid-template-columns:42px minmax(0,1fr);align-items:center;gap:10px;padding:15px;background:#fff;border-radius:14px}.metric-icon{width:42px;height:42px;display:grid;place-items:center;border-radius:13px}.metric-icon.personal{color:#9d3f5a;background:#fff0f3}.metric-icon.units{color:#a3640d;background:#fff7e8}.metric-icon.team{color:#315ec0;background:#edf3ff}.metric-icon.active{color:#0c805c;background:#eaf8f3}.metric-list small,.metric-list strong{display:block}.metric-list small{overflow:hidden;color:var(--muted);text-overflow:ellipsis;white-space:nowrap;font-size:11px}.metric-list strong{margin-top:6px;font-size:15px}
.level-panel,.growth-panel { margin-top:12px; border:0; border-radius:16px; }.panel-title{display:flex;align-items:center;justify-content:space-between}.panel-title h3{margin:0}.panel-title span{color:var(--muted);font-size:12px}.level-row{display:flex;align-items:center;justify-content:space-between;padding:14px 0;border-bottom:1px solid #f0f1f2}.level-row span{display:flex;align-items:center;gap:9px}.level-row i{width:24px;height:24px;display:grid;place-items:center;color:#315ec0;background:#edf3ff;border-radius:50%;font-size:11px;font-style:normal;font-weight:800}.level-panel>p,.growth-panel>p{margin:14px 0 0;color:var(--muted);font-size:11px;line-height:1.6}
.growth-panel>div{display:flex;align-items:center;justify-content:space-between}.growth-panel strong{color:#16855f;font-size:20px}.growth-panel strong.down{color:#b42318}
.page-error{padding:12px 14px;color:#b42318;background:#fff1f0;border-radius:10px}
@media(max-width:560px){.team-page{padding-top:10px}.metric-list{grid-template-columns:1fr 1fr}.metric-list article{grid-template-columns:36px minmax(0,1fr);padding:12px 10px}.metric-icon{width:36px;height:36px}.metric-list strong{font-size:13px}}
</style>
