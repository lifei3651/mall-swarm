<template>
  <main class="special-page flash-page">
    <header class="special-header">
      <button type="button" aria-label="返回" @click="$router.back()">‹</button>
      <div><h1>限时秒杀</h1><p>到点开抢 · 每人限购 · 库存抢完即止</p></div>
    </header>
    <p v-if="error" class="state error" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="state">活动加载中…</p>
    <section v-else-if="activities.length" class="special-grid">
      <article v-for="row in activities" :key="row.activity.id" class="special-card">
        <img :src="row.product?.coverUrl" :alt="row.product?.productName || row.activity.activityName" />
        <div class="special-copy">
          <span class="badge">{{ stateLabel(row) }}</span>
          <h2>{{ row.activity.activityName }}</h2>
          <p>{{ row.product?.productName }}<template v-if="row.sku"> · {{ row.sku.skuName }}</template></p>
          <div class="price"><strong>¥{{ money(row.activity.flashPrice) }}</strong><del>¥{{ money(row.sku?.salePrice ?? row.product?.salePrice) }}</del></div>
          <p class="stock">剩余 {{ row.activity.availableStock }} 件 · 每人限购 {{ row.activity.perUserLimit }} 件</p>
          <select v-model.number="quantities[row.activity.id]" :aria-label="`${row.activity.activityName}购买数量`">
            <option v-for="n in quantityOptions(row)" :key="n" :value="n">{{ n }} 件</option>
          </select>
          <button type="button" :disabled="row.activityState !== 'ACTIVE'" @click="buy(row)">
            {{ row.activityState === 'UPCOMING' ? countdown(row.activity.startTime) : row.activityState === 'SOLD_OUT' ? '已抢完' : row.activityState === 'ACTIVE' ? '立即抢购' : '已结束' }}
          </button>
        </div>
      </article>
    </section>
    <p v-else class="state">暂无秒杀活动</p>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listFlashSales } from '@/api/shop'
import { useCart } from '@/store/cart'
import { money } from '@/utils/format'

const router = useRouter()
const { beginDirectCheckout } = useCart()
const activities = ref([])
const quantities = reactive({})
const loading = ref(true)
const error = ref('')
const clock = ref(Date.now())
let timer
const quantityOptions = (row) => Array.from({ length: Math.max(1, Math.min(row.activity.perUserLimit, row.activity.availableStock)) }, (_, i) => i + 1)
const stateLabel = (row) => ({ UPCOMING: '即将开始', ACTIVE: '正在抢购', SOLD_OUT: '已抢完', ENDED: '已结束' }[row.activityState] || '暂不可用')
const countdown = (time) => {
  const seconds = Math.max(0, Math.ceil((new Date(time).getTime() - clock.value) / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')} 后开抢`
}
const buy = (row) => {
  const product = row.product || {}
  const sku = row.sku
  beginDirectCheckout({
    ...product, skuId: sku?.id || null, skuName: sku?.skuName || '',
    salePrice: Number(row.activity.flashPrice), pvValue: Number(row.activity.flashPv || 0),
    stock: Number(row.activity.availableStock), businessType: 'FLASH_SALE', businessSourceId: row.activity.id,
  }, quantities[row.activity.id] || 1)
  router.push('/checkout')
}
onMounted(async () => {
  timer = window.setInterval(() => { clock.value = Date.now() }, 1000)
  try {
    const res = await listFlashSales()
    activities.value = res.data || []
    activities.value.forEach((row) => { quantities[row.activity.id] = 1 })
  } catch (e) { error.value = e.message || '活动加载失败' } finally { loading.value = false }
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.special-page{min-height:100vh;padding:0 16px 70px;background:#f6f7f9}.special-header{max-width:1080px;display:flex;align-items:center;gap:14px;margin:auto;padding:24px 0}.special-header button{width:42px;height:42px;border:0;border-radius:50%;background:#fff;font-size:30px}.special-header h1{margin:0;font-size:26px}.special-header p,.special-copy p{margin:5px 0;color:#667085}.special-grid{max-width:1080px;display:grid;grid-template-columns:repeat(auto-fit,minmax(310px,1fr));gap:16px;margin:auto}.special-card{display:grid;grid-template-columns:145px 1fr;overflow:hidden;background:#fff;border-radius:18px;box-shadow:0 8px 25px rgba(20,30,55,.06)}.special-card img{width:100%;height:100%;min-height:230px;object-fit:cover}.special-copy{padding:18px}.special-copy h2{margin:10px 0 4px;font-size:18px}.badge{padding:4px 9px;color:#c51636;background:#fff0f2;border-radius:999px;font-size:12px}.price{display:flex;align-items:baseline;gap:10px;margin:16px 0 8px}.price strong{color:#e7193f;font-size:24px}.price del{color:#98a2b3}.stock{font-size:12px}.special-copy select{width:100%;height:38px;margin:8px 0;border:1px solid #d9dee7;border-radius:9px;padding:0 10px}.special-copy button{width:100%;height:44px;color:#fff;background:#e7193f;border:0;border-radius:10px;font-weight:800}.special-copy button:disabled{background:#aeb5c1}.state{padding:80px 20px;text-align:center;color:#667085}.error{color:#b42318}@media(max-width:560px){.special-card{grid-template-columns:110px 1fr}.special-card img{min-height:250px}.special-copy{padding:14px}}
</style>
