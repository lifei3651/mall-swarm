<template>
  <div class="page orders-page">
    <div class="orders-head">
      <RouterLink class="back-link" to="/profile" aria-label="返回个人中心"><ChevronLeft :size="21" /></RouterLink>
      <h2>我的订单</h2>
      <button class="refresh-btn" :class="{ spinning: refreshing }" :disabled="refreshing" @click="refreshOrders" aria-label="刷新"><RefreshCw :size="18" /></button>
    </div>

    <nav class="order-tabs" aria-label="订单状态">
      <RouterLink
        v-for="tab in tabs"
        :key="tab.key"
        :to="tab.key === 'all' ? '/orders' : `/orders?tab=${tab.key}`"
        :class="{ active: activeTab === tab.key }"
      >
        {{ tab.label }}
        <em v-if="tab.count">{{ tab.count > 99 ? '99+' : tab.count }}</em>
      </RouterLink>
    </nav>

    <div v-if="loading" class="empty compact-empty">订单加载中</div>
    <div v-else-if="error" class="empty compact-empty">
      <div><p>{{ error }}</p><button class="btn primary" @click="fetchOrders">重新加载</button></div>
    </div>
    <div v-else-if="filteredOrders.length === 0" class="empty compact-empty">
      <div><PackageOpen :size="44" /><p>这里还没有订单</p><RouterLink class="btn primary" to="/">去逛逛</RouterLink></div>
    </div>

    <section v-else class="order-card-list">
      <article v-for="item in filteredOrders" :key="item.order.id" class="order-card">
        <RouterLink :to="`/orders/${item.order.id}`" class="order-card-head">
          <span>{{ item.order.orderNo }}</span>
          <strong>{{ orderDisplayStatus(item) }} <ChevronRight :size="15" /></strong>
        </RouterLink>
        <RouterLink :to="`/orders/${item.order.id}`" class="order-products">
          <div v-for="line in item.items || []" :key="line.id" class="order-product">
            <img :src="line.productCover" :alt="line.productName" />
            <div>
              <h3>{{ line.productName }}</h3>
              <p>{{ formatProductSpec(line) }}</p>
            </div>
            <span>×{{ line.quantity }}</span>
          </div>
        </RouterLink>
        <div class="order-total">
          <span>共 {{ totalQuantity(item) }} 件</span>
          <span>实付 <strong>¥{{ money(item.order.payAmount) }}</strong></span>
        </div>
        <div v-if="item.afterSales?.length" class="after-sale-summary">
          售后申请 {{ item.afterSales.length }} 条 · {{ afterSaleStatus(item.afterSales[0]?.status) }}
        </div>
        <div class="order-actions">
          <RouterLink class="order-action" :to="`/orders/${item.order.id}`">查看详情</RouterLink>
          <button v-if="item.order.status === 0" class="order-action" :disabled="actingId === item.order.id" @click="cancel(item.order.id)">取消订单</button>
          <RouterLink v-if="item.order.status === 0" class="order-action primary-action" :to="`/orders/${item.order.id}`">立即支付</RouterLink>
          <button v-if="item.order.status === 2" class="order-action primary-action" :disabled="actingId === item.order.id" @click="receive(item.order.id)">确认收货</button>
          <RouterLink v-if="Number(item.pendingReviewCount || 0) > 0" class="order-action primary-action" :to="`/product/${item.items?.[0]?.productId}`">去评价</RouterLink>
          <RouterLink v-if="canApplyAfterSale(item)" class="order-action" :to="`/orders/${item.order.id}`">申请售后</RouterLink>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, ChevronRight, PackageOpen, RefreshCw } from 'lucide-vue-next'
import { cancelOrder, confirmReceive, listMyOrders } from '@/api/shop'
import { money, statusName } from '@/utils/format'
import { formatProductSpec } from '@/utils/productSpec'

const route = useRoute()
const loading = ref(false)
const refreshing = ref(false)
const error = ref('')
const orders = ref([])
const actingId = ref(null)
const validTabs = new Set(['all', 'pending-payment', 'pending-shipment', 'pending-receipt', 'pending-review', 'after-sale'])
const activeTab = computed(() => validTabs.has(route.query.tab) ? route.query.tab : 'all')

const isAfterSale = (item) => item.order?.status === 5 || (item.afterSales || []).length > 0
const matchTab = (item, tab) => ({
  all: true,
  'pending-payment': item.order?.status === 0,
  'pending-shipment': item.order?.status === 1,
  'pending-receipt': item.order?.status === 2,
  'pending-review': Number(item.pendingReviewCount || 0) > 0,
  'after-sale': isAfterSale(item),
}[tab] === true)

const countTab = (tab) => orders.value.filter((item) => matchTab(item, tab)).length
const tabs = computed(() => [
  { key: 'all', label: '全部', count: 0 },
  { key: 'pending-payment', label: '待支付', count: countTab('pending-payment') },
  { key: 'pending-shipment', label: '待发货', count: countTab('pending-shipment') },
  { key: 'pending-receipt', label: '待收货', count: countTab('pending-receipt') },
  { key: 'pending-review', label: '待评价', count: countTab('pending-review') },
  { key: 'after-sale', label: '退款/售后', count: countTab('after-sale') },
])
const filteredOrders = computed(() => orders.value.filter((item) => matchTab(item, activeTab.value)))

const fetchOrders = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await listMyOrders({ pageNum: 1, pageSize: 500 })
    orders.value = res.data?.list || []
  } catch (e) {
    error.value = e.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

const refreshOrders = async () => {
  refreshing.value = true
  await fetchOrders()
  refreshing.value = false
}

const totalQuantity = (item) => (item.items || []).reduce((sum, line) => sum + Number(line.quantity || 0), 0)
const afterSaleStatus = (status) => ({ 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已取消' }[status] || '处理中')
const afterSaleDeadline = (order) => {
  const created = Date.parse(String(order?.createTime || '').replace(' ', 'T'))
  return Number.isFinite(created) ? created + 7 * 24 * 60 * 60 * 1000 : Number.POSITIVE_INFINITY
}
const orderDisplayStatus = (item) => {
  if (isAfterSale(item)) return `退款/售后 · ${afterSaleStatus(item.afterSales?.[0]?.status)}`
  if (Number(item.pendingReviewCount || 0) > 0) return '待评价'
  return statusName(item.order?.status)
}
const canApplyAfterSale = (item) => ![0, 4].includes(item.order?.status)
  && Date.now() < afterSaleDeadline(item.order)
  && !(item.afterSales || []).some((sale) => sale.status === 0)

const cancel = async (id) => {
  actingId.value = id
  error.value = ''
  try {
    await cancelOrder(id)
    await fetchOrders()
  } catch (e) { error.value = e.message || '取消订单失败' }
  finally { actingId.value = null }
}

const receive = async (id) => {
  actingId.value = id
  error.value = ''
  try {
    await confirmReceive(id)
    await fetchOrders()
  } catch (e) { error.value = e.message || '确认收货失败' }
  finally { actingId.value = null }
}

onMounted(fetchOrders)
</script>

<style scoped>
.orders-page { max-width: 760px; }
.orders-head { display: grid; grid-template-columns: 34px 1fr 34px; align-items: center; margin-bottom: 10px; }
.orders-head h2 { margin: 0; text-align: center; font-size: 21px; }
.back-link { display: grid; place-items: center; width: 34px; height: 34px; color: var(--ink); background: #fff; border: 1px solid var(--line); border-radius: 50%; }
.refresh-btn { display: grid; place-items: center; width: 34px; height: 34px; padding: 0; color: var(--ink); background: #fff; border: 1px solid var(--line); border-radius: 50%; cursor: pointer; }
.refresh-btn:disabled { opacity: .5; cursor: not-allowed; }
.refresh-btn.spinning svg { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.order-tabs { position: sticky; top: 0; z-index: 5; display: flex; gap: 3px; padding: 0 6px; overflow-x: auto; background: #fff; border: 1px solid var(--line); border-radius: 10px; scrollbar-width: none; }
.order-tabs::-webkit-scrollbar { display: none; }
.order-tabs a { position: relative; flex: 1 0 auto; min-width: 62px; padding: 13px 7px 11px; color: var(--muted); text-align: center; font-size: 13px; white-space: nowrap; border-bottom: 2px solid transparent; }
.order-tabs a.active { color: var(--accent, #e7193f); border-bottom-color: var(--accent, #e7193f); font-weight: 800; }
.order-tabs em { position: absolute; top: 4px; margin-left: 1px; color: var(--accent, #e7193f); font-size: 9px; font-style: normal; }
.order-card-list { display: grid; gap: 11px; margin-top: 11px; }
.order-card { overflow: hidden; background: #fff; border: 1px solid var(--line); border-radius: 12px; }
.order-card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; color: var(--muted); font-size: 12px; border-bottom: 1px solid #f0f2f1; }
.order-card-head strong { display: inline-flex; align-items: center; color: var(--accent, #e7193f); }
.order-products { display: block; padding: 2px 14px; color: inherit; }
.order-product { display: grid; grid-template-columns: 68px minmax(0, 1fr) auto; gap: 10px; align-items: start; padding: 10px 0; }
.order-product + .order-product { border-top: 1px solid #f1f2f2; }
.order-product img { width: 68px; height: 68px; object-fit: cover; background: #f3f5f4; border-radius: 8px; }
.order-product h3 { display: -webkit-box; margin: 2px 0 7px; overflow: hidden; font-size: 14px; line-height: 1.35; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.order-product p, .order-product > span { margin: 0; color: var(--muted); font-size: 12px; }
.order-total { display: flex; justify-content: flex-end; gap: 12px; padding: 4px 14px 10px; color: var(--muted); font-size: 12px; }
.order-total strong { color: var(--ink); font-size: 16px; }
.after-sale-summary { margin: 0 14px 9px; padding: 8px 10px; color: #a65a16; background: #fff7ed; border-radius: 7px; font-size: 12px; }
.order-actions { display: flex; justify-content: flex-end; gap: 8px; padding: 10px 14px 12px; border-top: 1px solid #f0f2f1; }
.order-action { min-width: 76px; padding: 7px 11px; color: var(--ink); background: #fff; border: 1px solid #d7ddda; border-radius: 999px; text-align: center; font-size: 12px; }
.order-action.primary-action { color: var(--accent, #e7193f); border-color: var(--accent, #e7193f); font-weight: 700; }
.order-action:disabled { opacity: .55; }
.compact-empty { min-height: 280px; margin-top: 11px; }
.compact-empty svg { color: #aab2ae; }

@media (max-width: 920px) {
  .orders-page { width: 100%; padding-top: 10px; }
  .orders-head { padding: 0 12px; }
  .order-tabs { top: 0; border-width: 1px 0; border-radius: 0; }
  .order-card-list { padding: 0 9px; }
  .order-card { border-radius: 10px; }
}
</style>
