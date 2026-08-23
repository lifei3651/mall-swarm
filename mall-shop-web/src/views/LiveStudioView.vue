<template>
  <main class="studio-page">
    <header class="studio-header"><button type="button" aria-label="返回" @click="goBack"><ArrowLeft :size="21" /></button><strong>主播工作台</strong><RouterLink to="/live"><Radio :size="20" /></RouterLink></header>
    <div v-if="loading" class="studio-state"><LoaderCircle class="spin" :size="28" />正在核对直播权限…</div>
    <div v-else-if="error" class="studio-state"><ShieldAlert :size="32" /><strong>{{ error }}</strong><span>直播账号由平台后台直接开通，无需逐场申请。</span><RouterLink to="/">返回商城</RouterLink></div>
    <template v-else-if="studio.anchor">
      <section class="anchor-card">
        <span class="anchor-icon"><Radio :size="24" /></span>
        <div><h1>{{ studio.anchor.anchor.displayName }}</h1><p>{{ typeLabel(studio.anchor.anchor.anchorType) }} · {{ studio.anchor.anchor.companyName || '平台授权账号' }}</p></div>
        <em :class="{ disabled: !studio.canStart }">{{ studio.statusMessage }}</em>
      </section>
      <section class="safety-note"><ShieldCheck :size="20" /><div><strong>开播权限已绑定当前商城账号</strong><span>推流地址每次开播短时签发，请勿截图或转发；平台可随时停播、暂停或收回权限。</span></div></section>
      <section class="room-list">
        <div class="section-heading"><div><h2>我的直播间</h2><span>{{ studio.rooms?.length || 0 }} 个</span></div><button type="button" @click="load">刷新</button></div>
        <article v-for="item in studio.rooms" :key="item.room.id" class="room-card">
          <img :src="item.room.coverUrl" :alt="item.room.title" @error="applyImageFallback" />
          <div class="room-copy"><span class="room-state" :class="String(item.roomState).toLowerCase()">{{ stateLabel(item.roomState) }}</span><h3>{{ item.room.title }}</h3><p>{{ item.room.subtitle || '直播商品讲解' }}</p><small>{{ item.products?.length || 0 }} 件商品 · {{ timeLabel(item.room.scheduledStartTime) }}</small></div>
          <div class="room-actions">
            <button v-if="Number(item.room.status) !== 2" type="button" class="primary" :disabled="!studio.canStart || busyId === item.room.id || item.roomState === 'DISABLED'" @click="start(item)"><RadioTower :size="17" />开始直播</button>
            <button v-else type="button" class="danger" :disabled="busyId === item.room.id" @click="stop(item)"><Square :size="16" />结束直播</button>
            <RouterLink :to="`/live/${item.room.id}`">查看观众页</RouterLink>
          </div>
        </article>
        <div v-if="!studio.rooms?.length" class="studio-state"><Radio :size="30" /><strong>平台还没有给你分配直播间</strong><span>直播间由平台维护商品和展示信息，分配后这里会自动出现。</span></div>
      </section>
    </template>

    <div v-if="credentialVisible" class="credential-mask" @click.self="closeCredential">
      <section class="credential-card">
        <button type="button" class="close" aria-label="关闭" @click="closeCredential"><X :size="20" /></button>
        <span class="success-icon"><CircleCheckBig :size="34" /></span><h2>直播间已开始</h2>
        <p>{{ credential.instructions }}</p>
        <label v-if="credential.pushUrl">本次短时推流地址<textarea readonly :value="credential.pushUrl" /></label>
        <label>观众播放地址<textarea readonly :value="credential.playbackUrl" /></label>
        <small v-if="credential.expireTime">推流地址有效至 {{ timeLabel(credential.expireTime) }}</small>
        <button v-if="credential.pushUrl" type="button" class="copy" @click="copyPushUrl">复制推流地址</button>
        <RouterLink :to="`/live/${credential.roomId}`">进入观众页检查</RouterLink>
      </section>
    </div>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, CircleCheckBig, LoaderCircle, Radio, RadioTower, ShieldAlert, ShieldCheck, Square, X } from 'lucide-vue-next'
import { getLiveStudio, startLiveRoom, stopLiveRoom } from '@/api/shop'
import { applyImageFallback } from '@/utils/imageFallback'

const router = useRouter()
const studio = ref({})
const credential = ref({})
const credentialVisible = ref(false)
const busyId = ref(null)
const loading = ref(false)
const error = ref('')
const goBack = () => window.history.length > 1 ? router.back() : router.push('/')
const typeLabel = (type) => ({ PRODUCT: '厂家商品主播', PLATFORM: '平台讲解主播', FACTORY: '工厂实景主播' }[type] || '直播账号')
const stateLabel = (state) => ({ DRAFT: '草稿', UPCOMING: '待开播', CONNECTING: '视频连接中', LIVE: '直播中', ENDED: '已结束', DISABLED: '平台停用' }[state] || state)
const timeLabel = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '时间待定'

const load = async () => {
  loading.value = true; error.value = ''
  try { studio.value = (await getLiveStudio()).data || {} } catch (event) { error.value = event?.message || '当前账号无法进入主播工作台' } finally { loading.value = false }
}
const start = async (item) => {
  busyId.value = item.room.id
  try { credential.value = (await startLiveRoom(item.room.id)).data || {}; credentialVisible.value = true; await load() } finally { busyId.value = null }
}
const stop = async (item) => {
  if (!window.confirm(`确定结束“${item.room.title}”吗？`)) return
  busyId.value = item.room.id
  try { await stopLiveRoom(item.room.id); await load() } finally { busyId.value = null }
}
const closeCredential = () => { credentialVisible.value = false; credential.value = {} }
const copyPushUrl = async () => { try { await navigator.clipboard.writeText(credential.value.pushUrl) } catch {} }
onMounted(load)
</script>

<style scoped>
.studio-page{min-height:100vh;padding-bottom:60px;background:#f4f6f8}.studio-header{height:58px;display:grid;grid-template-columns:48px 1fr 48px;align-items:center;padding:0 max(8px,calc((100% - 980px)/2));background:#fff;border-bottom:1px solid #e8edf3}.studio-header button,.studio-header a{width:40px;height:40px;display:grid;place-items:center;color:#344054;background:transparent;border:0;border-radius:50%}.studio-header strong{text-align:center}.studio-header a{justify-self:end}.anchor-card,.safety-note,.room-list,.studio-state{width:min(980px,calc(100% - 24px));margin:16px auto}.anchor-card{display:grid;grid-template-columns:54px minmax(0,1fr) auto;align-items:center;gap:14px;padding:18px 20px;color:#fff;background:linear-gradient(135deg,#071a35,#0a4ea0);border-radius:22px}.anchor-icon{width:50px;height:50px;display:grid;place-items:center;background:rgba(255,255,255,.14);border-radius:15px}.anchor-card h1{margin:0;font-size:21px}.anchor-card p{margin:5px 0 0;color:rgba(255,255,255,.72);font-size:12px}.anchor-card em{padding:6px 10px;color:#b7f7d4;background:rgba(10,180,100,.18);border:1px solid rgba(183,247,212,.25);border-radius:999px;font-size:11px;font-style:normal}.anchor-card em.disabled{color:#ffd1d7;background:rgba(239,23,66,.18)}.safety-note{display:flex;align-items:flex-start;gap:10px;padding:15px 17px;color:#124b34;background:#ecfdf3;border:1px solid #b7ebc8;border-radius:16px}.safety-note strong,.safety-note span{display:block}.safety-note span{margin-top:4px;color:#47715f;font-size:12px;line-height:1.5}.room-list{padding:18px;background:#fff;border-radius:20px}.section-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.section-heading>div{display:flex;align-items:center;gap:8px}.section-heading h2{margin:0;font-size:19px}.section-heading span{color:#98a2b3}.section-heading button{padding:7px 12px;color:#475467;background:#fff;border:1px solid #d0d5dd;border-radius:8px}.room-card{display:grid;grid-template-columns:180px minmax(0,1fr) 140px;gap:15px;padding:14px 0;border-top:1px solid #eef1f4}.room-card>img{width:180px;aspect-ratio:16/9;object-fit:cover;border-radius:12px;background:#f2f4f7}.room-copy h3{margin:7px 0 5px}.room-copy p{margin:0;color:#667085;font-size:12px}.room-copy small{display:block;margin-top:9px;color:#98a2b3}.room-state{padding:3px 7px;color:#475467;background:#f2f4f7;border-radius:999px;font-size:10px}.room-state.live{color:#fff;background:#ef1742}.room-state.upcoming{color:#067647;background:#ecfdf3}.room-actions{display:flex;flex-direction:column;justify-content:center;gap:7px}.room-actions button,.room-actions a{height:36px;display:flex;align-items:center;justify-content:center;gap:5px;border-radius:9px;font-size:12px;text-decoration:none}.room-actions button{color:#fff;border:0}.room-actions .primary{background:#0a4ea0}.room-actions .danger{background:#d92d20}.room-actions button:disabled{opacity:.45}.room-actions a{color:#475467;border:1px solid #d0d5dd}.studio-state{min-height:280px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#667085;background:#fff;border-radius:20px}.studio-state a{padding:9px 16px;color:#fff;background:#0a4ea0;border-radius:999px;text-decoration:none}.credential-mask{position:fixed;z-index:100;inset:0;display:grid;place-items:center;padding:16px;background:rgba(3,12,26,.65)}.credential-card{position:relative;width:min(520px,100%);padding:28px;background:#fff;border-radius:22px}.credential-card .close{position:absolute;top:13px;right:13px;width:36px;height:36px;display:grid;place-items:center;background:#f2f4f7;border:0;border-radius:50%}.success-icon{width:58px;height:58px;display:grid;place-items:center;color:#067647;background:#ecfdf3;border-radius:50%}.credential-card h2{margin:15px 0 7px}.credential-card>p{color:#667085;font-size:13px;line-height:1.6}.credential-card label{display:block;margin-top:13px;color:#344054;font-size:12px}.credential-card textarea{width:100%;height:64px;margin-top:6px;padding:9px;resize:none;border:1px solid #d0d5dd;border-radius:9px;font-size:11px}.credential-card small{display:block;margin-top:8px;color:#98a2b3}.credential-card .copy,.credential-card>a{height:40px;display:flex;align-items:center;justify-content:center;margin-top:12px;border-radius:9px;text-decoration:none}.credential-card .copy{width:100%;color:#fff;background:#0a4ea0;border:0}.credential-card>a{color:#0a4ea0;border:1px solid #0a4ea0}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:720px){.studio-header{padding:0 8px}.anchor-card{grid-template-columns:48px minmax(0,1fr);padding:15px}.anchor-card em{grid-column:1/-1;width:max-content}.room-list{padding:13px}.room-card{grid-template-columns:100px minmax(0,1fr)}.room-card>img{width:100px}.room-actions{grid-column:1/-1;display:grid;grid-template-columns:1fr 1fr}.room-actions a{grid-column:1/-1}.credential-card{padding:24px 18px}}
</style>
