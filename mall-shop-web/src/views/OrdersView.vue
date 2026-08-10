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
          <button v-if="item.order.status === 0" class="order-action" :disabled="actingId === item.order.id" @click="requestOrderAction('cancel', item.order.id)">取消订单</button>
          <RouterLink v-if="item.order.status === 0" class="order-action primary-action" :to="`/orders/${item.order.id}`">立即支付</RouterLink>
          <button v-if="item.order.status === 2" class="order-action primary-action" :disabled="actingId === item.order.id" @click="requestOrderAction('receive', item.order.id)">确认收货</button>
          <RouterLink v-if="Number(item.pendingReviewCount || 0) > 0" class="order-action primary-action" :to="`/product/${item.items?.[0]?.productId}`">去评价</RouterLink>
          <RouterLink v-if="canApplyAfterSale(item)" class="order-action" :to="`/orders/${item.order.id}?applyAfterSale=1`">申请售后</RouterLink>
        </div>
      </article>
      <button v-if="hasMore" class="load-more-orders" :disabled="loadingMore" @click="loadMore">
        {{ loadingMore ? '正在加载...' : '加载更多订单' }}
      </button>
    </section>
    <ConfirmDialog
      :visible="Boolean(pendingOrderAction.type)"
      :title="orderActionDialog.title"
      :message="orderActionDialog.message"
      :confirm-text="orderActionDialog.confirmText"
      :cancel-text="orderActionDialog.cancelText"
      :loading-text="orderActionDialog.loadingText"
      :icon-type="orderActionDialog.iconType"
      :is-danger="orderActionDialog.isDanger"
      :busy="Boolean(actingId)"
      @confirm="confirmPendingOrderAction"
      @cancel="closeOrderAction"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, ChevronRight, PackageOpen, RefreshCw } from 'lucide-vue-next'
import { cancelOrder, confirmReceive, getProfileOrderSummary, listMyOrders } from '@/api/shop'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { money, statusName } from '@/utils/format'
import { formatProductSpec } from '@/utils/productSpec'

const route = useRoute()
const loading = ref(false)
const loadingMore = ref(false)
const refreshing = ref(false)
const error = ref('')
const orders = ref([])
const orderSummary = ref({})
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const actingId = ref(null)
const pendingOrderAction = ref({ type: '', id: null })
let requestSequence = 0
const validTabs = new Set(['all', 'pending-payment', 'pending-shipment', 'pending-receipt', 'pending-review', 'after-sale'])
const activeTab = computed(() => validTabs.has(route.query.tab) ? route.query.tab : 'all')

const activeAfterSales = (item) => (item.afterSales || [])
  .filter((sale) => [0, 4, 5, 6].includes(Number(sale.status)))
const isAfterSale = (item) => item.order?.status === 5 || activeAfterSales(item).length > 0
const tabs = computed(() => [
  { key: 'all', label: '全部', count: 0 },
  { key: 'pending-payment', label: '待支付', count: Number(orderSummary.value.pendingPayment || 0) },
  { key: 'pending-shipment', label: '待发货', count: Number(orderSummary.value.pendingShipment || 0) },
  { key: 'pending-receipt', label: '待收货', count: Number(orderSummary.value.pendingReceipt || 0) },
  { key: 'pending-review', label: '待评价', count: Number(orderSummary.value.pendingReview || 0) },
  { key: 'after-sale', label: '退款/售后', count: Number(orderSummary.value.afterSale || 0) },
])
const filteredOrders = computed(() => orders.value)
const hasMore = computed(() => orders.value.length < total.value)
const pendingOrder = computed(() => orders.value.find((item) => item.order?.id === pendingOrderAction.value.id)?.order)
const orderActionDialog = computed(() => pendingOrderAction.value.type === 'receive' ? {
  title: '确认已收到商品？',
  message: `请确认订单“${pendingOrder.value?.orderNo || ''}”已经签收且商品数量无误。`,
  confirmText: '确认收货',
  cancelText: '暂未收到',
  loadingText: '确认中…',
  iconType: 'receive',
  isDanger: false,
} : {
  title: '取消这笔订单？',
  message: `取消订单“${pendingOrder.value?.orderNo || ''}”后，已占用库存会自动释放，此操作无法恢复。`,
  confirmText: '确认取消',
  cancelText: '保留订单',
  loadingText: '取消中…',
  iconType: 'cancel',
  isDanger: true,
})
const orderStateMap = {
  'pending-payment': 'PENDING_PAYMENT',
  'pending-shipment': 'PENDING_SHIPMENT',
  'pending-receipt': 'PENDING_RECEIPT',
  'pending-review': 'PENDING_REVIEW',
  'after-sale': 'AFTER_SALE',
}

const fetchOrderSummary = async () => {
  try { orderSummary.value = (await getProfileOrderSummary()).data || {} }
  catch { orderSummary.value = {} }
}

const fetchOrders = async ({ append = false } = {}) => {
  const sequence = ++requestSequence
  if (append) loadingMore.value = true
  else {
    loading.value = true
    loadingMore.value = false
  }
  error.value = ''
  const targetPage = append ? pageNum.value + 1 : 1
  try {
    const res = await listMyOrders({
      pageNum: targetPage,
      pageSize,
      orderState: orderStateMap[activeTab.value],
    })
    if (sequence !== requestSequence) return
    const nextRows = res.data?.list || []
    orders.value = append ? [...orders.value, ...nextRows] : nextRows
    pageNum.value = targetPage
    total.value = Number(res.data?.total || 0)
  } catch (e) {
    if (sequence !== requestSequence) return
    error.value = e.message || '订单加载失败'
  } finally {
    if (sequence === requestSequence) {
      if (append) loadingMore.value = false
      else loading.value = false
    }
  }
}

const loadMore = () => {
  if (!loadingMore.value && hasMore.value) fetchOrders({ append: true })
}

const refreshOrders = async () => {
  refreshing.value = true
  await Promise.all([fetchOrders(), fetchOrderSummary()])
  refreshing.value = false
}

const totalQuantity = (item) => (item.items || []).reduce((sum, line) => sum + Number(line.quantity || 0), 0)
const afterSaleStatus = (status) => ({ 0: '待审核', 1: '退款完成', 2: '已拒绝', 3: '已取消', 4: '待客户寄回', 5: '待商家收货', 6: '退款处理中' }[status] || '处理中')
const afterSaleDeadline = (order) => {
  const created = Date.parse(String(order?.createTime || '').replace(' ', 'T'))
  return Number.isFinite(created) ? created + 7 * 24 * 60 * 60 * 1000 : Number.POSITIVE_INFINITY
}
const unavailableAfterSaleQuantity = (item) => (item.afterSales || [])
  .filter((sale) => [0, 1, 4, 5, 6].includes(Number(sale.status)))
  .flatMap((sale) => sale.items || [])
  .reduce((sum, line) => sum + Number(line.refundQuantity || 0), 0)
const orderQuantity = (item) => (item.items || []).reduce((sum, line) => sum + Number(line.quantity || 0), 0)
const orderDisplayStatus = (item) => {
  if (isAfterSale(item)) return `退款/售后 · ${afterSaleStatus(activeAfterSales(item)[0]?.status)}`
  if (Number(item.pendingReviewCount || 0) > 0) return '待评价'
  return statusName(item.order?.status)
}
const canApplyAfterSale = (item) => ![0, 4].includes(item.order?.status)
  && Date.now() < afterSaleDeadline(item.order)
  && !(item.afterSales || []).some((sale) => [0, 4, 5, 6].includes(sale.status))
  && unavailableAfterSaleQuantity(item) < orderQuantity(item)

const requestOrderAction = (type, id) => {
  if (actingId.value) return
  pendingOrderAction.value = { type, id }
}

const closeOrderAction = () => {
  if (!actingId.value) pendingOrderAction.value = { type: '', id: null }
}

const confirmPendingOrderAction = () => {
  const { type, id } = pendingOrderAction.value
  if (!id) return closeOrderAction()
  return type === 'receive' ? receive(id) : cancel(id)
}

const cancel = async (id) => {
  actingId.value = id
  error.value = ''
  try {
    await cancelOrder(id)
    await Promise.all([fetchOrders(), fetchOrderSummary()])
  } catch (e) { error.value = e.message || '取消订单失败' }
  finally { actingId.value = null; pendingOrderAction.value = { type: '', id: null } }
}

const receive = async (id) => {
  actingId.value = id
  error.value = ''
  try {
    await confirmReceive(id)
    await Promise.all([fetchOrders(), fetchOrderSummary()])
  } catch (e) { error.value = e.message || '确认收货失败' }
  finally { actingId.value = null; pendingOrderAction.value = { type: '', id: null } }
}

watch(activeTab, () => fetchOrders())
onMounted(() => {
  fetchOrderSummary()
  fetchOrders()
})
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
.load-more-orders { width:100%; padding:12px; color:var(--accent,#e7193f); background:#fff; border:1px solid var(--line); border-radius:10px; font-weight:700; cursor:pointer; }
.load-more-orders:disabled { opacity:.55; cursor:not-allowed; }
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
