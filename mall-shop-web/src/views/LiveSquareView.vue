<template>
  <main class="live-square-page">
    <section class="live-square-top">
      <header class="live-square-header">
        <button type="button" aria-label="返回上一页" @click="goBack"><ArrowLeft :size="23" /></button>
        <strong>直播广场</strong>
        <RouterLink to="/" aria-label="返回商城首页"><Home :size="21" /></RouterLink>
      </header>

      <nav v-if="!disabled" class="live-tabs" role="tablist" aria-label="直播广场分类">
        <button type="button" role="tab" :aria-selected="activeTab === 'live'" :class="{ active: activeTab === 'live' }" @click="activeTab = 'live'">
          直播中
        </button>
        <button type="button" role="tab" :aria-selected="activeTab === 'upcoming'" :class="{ active: activeTab === 'upcoming' }" @click="activeTab = 'upcoming'">
          直播预告
        </button>
      </nav>
    </section>

    <section class="live-square-content">
      <div v-if="loading" class="live-state-card"><LoaderCircle class="spin" :size="28" /><span>正在加载直播广场…</span></div>
      <div v-else-if="disabled" class="live-state-card"><Radio :size="34" /><strong>直播广场暂未开放</strong><span>当前公司尚未开启此独立页面</span><RouterLink to="/">返回商城首页</RouterLink></div>
      <div v-else-if="error" class="live-state-card"><CircleAlert :size="28" /><strong>{{ error }}</strong><button type="button" @click="load">重新加载</button></div>

      <template v-else-if="activeTab === 'live'">
        <div v-if="liveRooms.length" class="live-room-list">
          <article v-for="item in liveRooms" :key="item.room.id" class="live-room-card">
            <RouterLink class="live-room-cover" :to="`/live/${item.room.id}`" :aria-label="`进入${item.room.title}`">
              <img :src="item.room.coverUrl" :alt="item.room.title" loading="lazy" @error="applyImageFallback" />
              <span class="live-now-badge"><Radio :size="13" />{{ item.roomState === 'CONNECTING' ? '连接中' : '直播中' }}</span>
              <strong>{{ item.room.title }}</strong>
            </RouterLink>
            <div class="live-room-panel">
              <RouterLink class="live-anchor-row" :to="`/live/${item.room.id}`">
                <span class="anchor-avatar"><UserRound :size="17" /></span>
                <span class="anchor-copy"><strong>{{ item.room.anchorName || '官方直播间' }}</strong><small>{{ item.room.subtitle || '带你看精选好物' }}</small></span>
                <span class="room-metrics"><small><Users :size="12" />{{ heat(item.room.viewerCount) }}</small><small><Flame :size="12" />{{ heat(item.room.heatCount) }}</small></span>
              </RouterLink>
              <div class="live-product-stack">
                <RouterLink v-for="product in item.products?.slice(0, 2)" :key="product.id" class="live-product-row" :to="`/product/${product.id}`">
                  <img :src="product.coverUrl" :alt="product.productName" loading="lazy" @error="applyImageFallback" />
                  <span><strong>{{ product.productName }}</strong><small>{{ product.subtitle || '直播间精选商品' }}</small><b>¥{{ money(product.salePrice) }}<em>直播价</em></b></span>
                </RouterLink>
              </div>
            </div>
          </article>
        </div>
        <div v-else class="live-state-card compact"><Radio :size="32" /><strong>当前没有正在直播</strong><span>可以先看看已经发布的直播预告</span><button v-if="upcomingRooms.length" type="button" @click="activeTab = 'upcoming'">查看直播预告</button></div>
      </template>

      <template v-else>
        <div v-if="upcomingRooms.length" class="upcoming-grid">
          <article v-for="item in upcomingRooms" :key="item.room.id" class="upcoming-card">
            <RouterLink class="upcoming-cover" :to="`/live/${item.room.id}`">
              <img :src="item.room.coverUrl" :alt="item.room.title" loading="lazy" @error="applyImageFallback" />
              <span>{{ formatSchedule(item.room.scheduledStartTime) }}</span>
            </RouterLink>
            <RouterLink class="upcoming-title" :to="`/live/${item.room.id}`">{{ item.room.title }}</RouterLink>
            <div class="upcoming-footer">
              <span class="upcoming-anchor"><span><UserRound :size="14" /></span>{{ item.room.anchorName || '官方直播间' }}</span>
              <button type="button" :class="{ reserved: reservedIds.has(Number(item.room.id)) }" :disabled="reservingIds.has(Number(item.room.id))" @click="toggleReservation(item)">
                {{ reservingIds.has(Number(item.room.id)) ? '处理中' : reservedIds.has(Number(item.room.id)) ? '已预约' : '预约' }}
              </button>
            </div>
          </article>
        </div>
        <div v-else class="live-state-card compact"><CalendarClock :size="32" /><strong>暂时没有直播预告</strong><span>新的预告发布后会在这里出现</span><button v-if="liveRooms.length" type="button" @click="activeTab = 'live'">去看正在直播</button></div>
      </template>
    </section>

    <div v-if="toast" class="live-toast" role="status">{{ toast }}</div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CalendarClock, CircleAlert, Flame, Home, LoaderCircle, Radio, UserRound, Users } from 'lucide-vue-next'
import { cancelLiveReservation, getHome, listLiveReservations, listLiveRooms, reserveLiveRoom } from '@/api/shop'
import { requireShopSession } from '@/utils/authNavigation'
import { hasShopSession } from '@/utils/shopSession'
import { applyImageFallback } from '@/utils/imageFallback'
import { money } from '@/utils/format'

const router = useRouter()
const route = useRoute()
const rooms = ref([])
const activeTab = ref(route.query.tab === 'upcoming' ? 'upcoming' : 'live')
const reservedIds = ref(new Set())
const reservingIds = ref(new Set())
const loading = ref(false)
const disabled = ref(false)
const error = ref('')
const toast = ref('')
let toastTimer

const liveRooms = computed(() => rooms.value.filter((item) => ['LIVE', 'CONNECTING'].includes(item.roomState)))
const upcomingRooms = computed(() => rooms.value.filter((item) => item.roomState === 'UPCOMING'))
const heat = (value) => {
  const count = Math.max(0, Number(value || 0))
  return count >= 10000 ? `${(count / 10000).toFixed(count >= 100000 ? 0 : 1)}万` : String(count)
}
const formatSchedule = (value) => {
  if (!value) return '开播时间待定'
  const target = new Date(value)
  if (Number.isNaN(target.getTime())) return String(value).replace('T', ' ').slice(5, 16)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1)
  const targetDay = new Date(target.getFullYear(), target.getMonth(), target.getDate())
  const prefix = targetDay.getTime() === today.getTime()
    ? '今日' : targetDay.getTime() === tomorrow.getTime() ? '明日' : `${target.getMonth() + 1}月${target.getDate()}日`
  return `${prefix} ${String(target.getHours()).padStart(2, '0')}:${String(target.getMinutes()).padStart(2, '0')}开播`
}
const goBack = () => window.history.length > 1 ? router.back() : router.push('/')
const showToast = (message) => {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2200)
}
const replaceSetValue = (source, value, enabled) => {
  const next = new Set(source.value)
  if (enabled) next.add(Number(value)); else next.delete(Number(value))
  source.value = next
}
const toggleReservation = async (item) => {
  if (!requireShopSession(router, route.fullPath, '请先登录后预约直播')) return
  const roomId = Number(item.room.id)
  if (reservingIds.value.has(roomId)) return
  replaceSetValue(reservingIds, roomId, true)
  try {
    const wasReserved = reservedIds.value.has(roomId)
    if (wasReserved) await cancelLiveReservation(roomId); else await reserveLiveRoom(roomId)
    replaceSetValue(reservedIds, roomId, !wasReserved)
    showToast(wasReserved ? '已取消直播预约' : '预约成功，可随时回来查看')
  } catch (e) {
    showToast(e?.message || '直播预约暂时无法保存')
  } finally {
    replaceSetValue(reservingIds, roomId, false)
  }
}
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const home = (await getHome()).data || {}
    if (Number(home.displayConfig?.liveSquareEnabled ?? 1) !== 1) {
      disabled.value = true
      rooms.value = []
      return
    }
    disabled.value = false
    rooms.value = (await listLiveRooms({ limit: 50 })).data || []
    if (hasShopSession()) {
      try { reservedIds.value = new Set(((await listLiveReservations()).data || []).map(Number)) } catch { reservedIds.value = new Set() }
    }
  } catch (e) { error.value = e?.message || '直播广场暂时加载失败' } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.live-square-page{min-height:100vh;padding-bottom:72px;color:#17202a;background:var(--shop-page-bg,#f5f6f8)}
.live-square-top{position:sticky;top:0;z-index:20;background:color-mix(in srgb,var(--brand-primary,#16a36a) 18%,#fff 82%);border-bottom:1px solid color-mix(in srgb,var(--brand-primary,#16a36a) 16%,#fff 84%)}
.live-square-header{height:62px;display:grid;grid-template-columns:48px 1fr 48px;align-items:center;width:min(1180px,100%);margin:0 auto;padding:0 10px}
.live-square-header>strong{justify-self:center;font-size:22px;font-weight:900;letter-spacing:.02em}.live-square-header button,.live-square-header>a{width:40px;height:40px;display:grid;place-items:center;color:#17202a;background:transparent;border:0;border-radius:50%}.live-square-header>a{justify-self:end}
.live-tabs{width:min(1180px,100%);height:54px;display:flex;align-items:end;gap:22px;margin:0 auto;padding:0 18px}.live-tabs button{position:relative;height:48px;padding:0 0 10px;color:#344054;background:transparent;border:0;font-size:18px}.live-tabs button.active{color:#111827;font-weight:800}.live-tabs button.active::after{position:absolute;right:0;bottom:5px;left:0;height:3px;background:var(--brand-primary,#16a36a);border-radius:999px;content:""}
.live-square-content{width:min(1180px,calc(100% - 24px));margin:0 auto;padding-top:12px}.live-room-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.live-room-card{min-height:250px;display:grid;grid-template-columns:48% 52%;overflow:hidden;background:#fff;border:1px solid #e7e9ec;border-radius:12px;box-shadow:0 4px 14px rgba(15,23,42,.04)}
.live-room-cover{position:relative;min-width:0;overflow:hidden;background:#e9ecef}.live-room-cover>img{width:100%;height:100%;object-fit:cover}.live-room-cover>strong{position:absolute;right:0;bottom:0;left:0;padding:9px 8px;color:#fff;background:color-mix(in srgb,var(--brand-primary,#16a36a) 68%,transparent);font-size:17px;text-align:center;text-shadow:0 1px 2px rgba(0,0,0,.25)}.live-now-badge{position:absolute;top:10px;left:10px;display:flex;align-items:center;gap:4px;padding:4px 7px;color:#fff;background:#ef1742;border-radius:999px;font-size:10px;font-weight:800}
.live-room-panel{min-width:0;display:flex;flex-direction:column}.live-anchor-row{min-height:58px;display:flex;align-items:center;gap:7px;padding:7px;color:#202733;text-decoration:none}.anchor-avatar{width:31px;height:31px;flex:0 0 auto;display:grid;place-items:center;color:#667085;background:#e4e7ec;border-radius:50%}.anchor-copy{min-width:0;display:grid;gap:2px}.anchor-copy strong{overflow:hidden;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.anchor-copy small{overflow:hidden;color:#667085;font-size:9px;text-overflow:ellipsis;white-space:nowrap}.room-metrics{display:grid;gap:2px;margin-left:auto;color:#344054}.room-metrics small{display:flex;align-items:center;gap:3px;font-size:9px}
.live-product-stack{flex:1;display:grid;grid-template-rows:repeat(2,minmax(0,1fr));gap:6px;padding:0 7px 7px}.live-product-row{min-height:0;display:grid;grid-template-columns:44% 56%;overflow:hidden;color:#17202a;background:#f3f4f6;border-radius:6px;text-decoration:none}.live-product-row>img{width:100%;height:100%;object-fit:cover}.live-product-row>span{min-width:0;display:flex;flex-direction:column;padding:7px 6px}.live-product-row strong{display:-webkit-box;overflow:hidden;font-size:10px;line-height:1.35;-webkit-box-orient:vertical;-webkit-line-clamp:2}.live-product-row small{display:-webkit-box;overflow:hidden;margin-top:3px;color:#667085;font-size:8px;line-height:1.3;-webkit-box-orient:vertical;-webkit-line-clamp:1}.live-product-row b{margin-top:auto;color:var(--price-color,#ef1742);font-size:13px}.live-product-row em{margin-left:3px;font-size:8px;font-style:normal;font-weight:500}
.upcoming-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.upcoming-card{overflow:hidden;background:#fff;border:1px solid #e9ebee;border-radius:10px}.upcoming-cover{position:relative;display:block;aspect-ratio:1/1.14;overflow:hidden;background:#e7e9ec}.upcoming-cover img{width:100%;height:100%;object-fit:cover}.upcoming-cover span{position:absolute;top:0;left:0;padding:4px 7px;color:#667085;background:rgba(255,255,255,.9);border-radius:0 0 7px 0;font-size:10px}.upcoming-title{display:block;overflow:hidden;padding:10px 9px 4px;color:#17202a;font-size:15px;font-weight:800;text-decoration:none;text-overflow:ellipsis;white-space:nowrap}.upcoming-footer{display:flex;align-items:center;gap:6px;padding:5px 9px 10px}.upcoming-anchor{min-width:0;display:flex;align-items:center;gap:5px;overflow:hidden;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.upcoming-anchor>span{width:23px;height:23px;flex:0 0 auto;display:grid;place-items:center;color:#667085;background:#e4e7ec;border-radius:50%}.upcoming-footer>button{min-width:62px;margin-left:auto;padding:5px 12px;color:#ef1742;background:#ffd8dd;border:0;border-radius:999px;font-size:11px;font-weight:800}.upcoming-footer>button.reserved{color:#fff;background:var(--brand-primary,#16a36a)}.upcoming-footer>button:disabled{opacity:.65}
.live-state-card{min-height:360px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#98a2b3;background:#fff;border-radius:16px;text-align:center}.live-state-card.compact{min-height:300px}.live-state-card strong{color:#475467}.live-state-card button,.live-state-card>a{padding:9px 17px;color:#fff;background:var(--brand-primary,#16a36a);border:0;border-radius:999px;text-decoration:none}.live-toast{position:fixed;bottom:82px;left:50%;z-index:80;max-width:calc(100% - 40px);padding:10px 17px;color:#fff;background:rgba(17,24,39,.9);border-radius:999px;font-size:13px;transform:translateX(-50%)}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:760px){.live-square-header{height:60px}.live-square-header>strong{font-size:21px}.live-tabs{height:53px}.live-square-content{width:calc(100% - 30px);padding-top:8px}.live-room-list{grid-template-columns:1fr;gap:10px}.live-room-card{min-height:238px;grid-template-columns:46% 54%;border-radius:9px}.live-anchor-row{min-height:55px}.live-product-stack{gap:6px;padding:0 6px 6px}.upcoming-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.upcoming-card{border-radius:8px}.upcoming-title{font-size:14px}.upcoming-footer{padding:5px 8px 9px}.upcoming-footer>button{min-width:59px;padding:5px 10px}}
@media(max-width:365px){.live-room-card{grid-template-columns:46% 54%}.live-product-row>span{padding:5px 4px}.live-product-row strong{font-size:9px}.upcoming-anchor{font-size:10px}.upcoming-footer>button{min-width:54px;padding:5px 8px}}
</style>
