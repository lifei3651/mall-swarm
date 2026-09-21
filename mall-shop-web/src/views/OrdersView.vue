<template>
  <div class="page orders-page">
    <div class="orders-head">
      <RouterLink class="back-link" to="/profile" aria-label="返回个人中心"><ChevronLeft :size="21" /></RouterLink>
      <h2>我的订单</h2>
      <button class="refresh-btn" :class="{ spinning: refreshing }" :disabled="refreshing" @click="refreshOrders" aria-label="刷新"><RefreshCw :size="18" /></button>
    </div>

    <div class="order-tabs-shell">
      <nav ref="orderTabs" class="order-tabs" aria-label="订单状态">
        <RouterLink
          v-for="tab in tabs"
          :key="tab.key"
          :data-order-tab="tab.key"
          :to="tab.key === 'all' ? '/orders' : `/orders?tab=${tab.key}`"
          :class="{ active: activeTab === tab.key }"
        >
          {{ tab.label }}
          <em v-if="tab.count">{{ tab.count > 99 ? '99+' : tab.count }}</em>
        </RouterLink>
      </nav>
    </div>

    <div v-if="loading" class="empty compact-empty">订单加载中</div>
    <div v-else-if="error" class="empty compact-empty">
      <div><p>{{ error }}</p><button class="btn primary" @click="fetchOrders">重新加载</button></div>
    </div>
    <div v-else-if="filteredOrders.length === 0" class="empty compact-empty">
      <div><PackageOpen :size="44" /><p>这里还没有订单</p><RouterLink class="btn primary" to="/">去逛逛</RouterLink></div>
    </div>

    <section v-else class="order-card-list">
      <article v-for="item in filteredOrders" :key="item.order.id" class="order-card ui-card ui-order-card">
        <RouterLink :to="`/orders/${item.order.id}`" class="ui-order-header">
          <span class="ui-order-title"><strong>{{ item.order.merchantName || '商城订单' }}</strong><span>{{ item.order.orderNo }}</span></span>
          <strong class="ui-status-pill ui-order-status" :class="orderStateClass(item)">{{ orderDisplayStatus(item) }}</strong>
        </RouterLink>
        <RouterLink :to="`/orders/${item.order.id}`" class="ui-order-product">
          <img :src="firstOrderItem(item).productCover" :alt="firstOrderItem(item).productName" @error="applyImageFallback" />
          <span class="ui-order-product-copy">{{ firstOrderItem(item).productName }}<template v-if="formatProductSpec(firstOrderItem(item))"> · {{ formatProductSpec(firstOrderItem(item)) }}</template></span>
          <span class="ui-order-product-meta"><em v-if="remainingProductKinds(item)" class="ui-order-product-more">等{{ item.items.length }}种</em><span>×{{ firstOrderItem(item).quantity || 0 }}</span></span>
        </RouterLink>
        <div class="ui-order-logistics">
          <span>{{ orderLogisticsText(item) }}</span>
          <RouterLink v-if="item.order.status === 2 && item.autoReceiveEnabled && !isAfterSale(item) && canApplyAfterSale(item)" class="ui-order-logistics-action" :to="`/orders/${item.order.id}?applyAfterSale=1`">未收到 / 拒收</RouterLink>
        </div>
        <div class="ui-order-summary">
          <span>共 {{ totalQuantity(item) }} 件</span>
          <span>实付 <strong class="ui-price">¥{{ money(item.order.payAmount) }}</strong></span>
        </div>
        <div class="order-actions ui-action-bar ui-order-actions">
          <RouterLink class="order-action btn secondary ui-action-button ui-order-action" :to="`/orders/${item.order.id}`">查看详情</RouterLink>
          <button v-if="item.order.status === 0 && isTradeActionOwner(item)" class="order-action btn secondary ui-action-button ui-order-action" :disabled="actingId === item.order.id" @click="requestOrderAction('cancel', item.order.id)">取消订单</button>
          <RouterLink v-if="canApplyAfterSale(item)" class="order-action btn secondary ui-action-button ui-order-action" :to="`/orders/${item.order.id}?applyAfterSale=1`">申请售后</RouterLink>
          <RouterLink v-if="Number(item.pendingReviewCount || 0) > 0" class="order-action btn secondary ui-action-button ui-order-action" :to="reviewLink(item)">去评价</RouterLink>
          <RouterLink v-if="item.order.status === 0 && isTradeActionOwner(item)" class="order-action btn primary ui-action-button ui-action-button--primary ui-order-action ui-order-action--primary" :to="`/orders/${item.order.id}`">立即支付</RouterLink>
          <button v-if="item.order.status === 2 && !isAfterSale(item)" class="order-action btn primary ui-action-button ui-action-button--primary ui-order-action ui-order-action--primary" :disabled="actingId === item.order.id" @click="requestOrderAction('receive', item.order.id)">确认收货</button>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, PackageOpen, RefreshCw } from 'lucide-vue-next'
import { cancelOrder, confirmReceive, getProfileOrderSummary, listMyOrders } from '@/api/shop'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { dateTime, money, statusName } from '@/utils/format'
import { formatProductSpec } from '@/utils/productSpec'
import { connectOrderRealtime } from '@/utils/orderRealtime'
import { applyImageFallback } from '@/utils/imageFallback'
import { isTradeActionOwner as ownsTradeAction, canApplyAfterSale } from '@/utils/orderListRules'

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
const orderTabs = ref(null)
let requestSequence = 0
let stopOrderRealtime = null
let fallbackPollTimer = null
let realtimeRefreshTimer = null
let disposed = false
const validTabs = new Set(['all', 'pending-payment', 'pending-shipment', 'pending-receipt', 'pending-review', 'after-sale'])
const activeTab = computed(() => validTabs.has(route.query.tab) ? route.query.tab : 'all')

const activeAfterSales = (item) => (item.afterSales || [])
  .filter((sale) => [0, 4, 5, 6, 7, 8].includes(Number(sale.status)))
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
const isTradeActionOwner = (item) => ownsTradeAction(item, filteredOrders.value)
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
  title: pendingOrder.value?.tradeId ? '取消全部联合订单？' : '取消这笔订单？',
  message: pendingOrder.value?.tradeId
    ? `本次会取消联合支付“${pendingOrder.value?.tradeNo || ''}”下的全部商户子订单，并释放全部库存，此操作无法恢复。`
    : `取消订单“${pendingOrder.value?.orderNo || ''}”后，已占用库存会自动释放，此操作无法恢复。`,
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

const scheduleRealtimeRefresh = () => {
  window.clearTimeout(realtimeRefreshTimer)
  realtimeRefreshTimer = window.setTimeout(() => refreshOrders(), 250)
}

const setRealtimeConnected = (connected) => {
  window.clearInterval(fallbackPollTimer)
  fallbackPollTimer = null
  if (!connected && !disposed) {
    fallbackPollTimer = window.setInterval(() => {
      if (document.visibilityState === 'visible') refreshOrders()
    }, 30000)
  }
}

const totalQuantity = (item) => (item.items || []).reduce((sum, line) => sum + Number(line.quantity || 0), 0)
const firstOrderItem = (item) => item.items?.[0] || { productCover: '', productName: '订单商品', skuName: '', quantity: 0 }
const remainingProductKinds = (item) => Math.max(0, Number(item.items?.length || 0) - 1)
const reviewLink = (item) => ({
  path: `/order-review/${item.pendingReviewProductId}`,
  query: item.pendingReviewOrderItemId ? { orderItemId: item.pendingReviewOrderItemId } : {},
})
const afterSaleStatus = (status, applyType) => {
  if (Number(applyType) === 3 && Number(status) === 1) return '换货完成'
  return ({ 0: '待审核', 1: '退款完成', 2: '已拒绝', 3: '已取消', 4: '待客户寄回', 5: '待商家收货', 6: '退款处理中', 7: '待商家换货发出', 8: '换货已发出' }[status] || '处理中')
}
const orderDisplayStatus = (item) => {
  if (isAfterSale(item)) {
    const sale = activeAfterSales(item)[0]
    return `售后 · ${afterSaleStatus(sale?.status, sale?.applyType)}`
  }
  if (Number(item.pendingReviewCount || 0) > 0) return '待评价'
  return statusName(item.order?.status)
}
const orderStateClass = (item) => {
  if (Number(item.order?.status) === 4) return 'is-cancelled'
  if (Number(item.order?.status) === 3 && !isAfterSale(item) && Number(item.pendingReviewCount || 0) === 0) return 'is-completed'
  return 'is-active'
}
const orderLogisticsText = (item) => {
  if (item.afterSales?.length) return `售后进度：${afterSaleStatus(item.afterSales[0]?.status, item.afterSales[0]?.applyType)}`
  if (Number(item.order?.status) === 2 && item.autoReceiveEnabled) return item.autoReceiveDeadline ? `预计 ${dateTime(item.autoReceiveDeadline)} 自动确认收货` : `发货满 ${Number(item.autoReceiveDays || 15)} 天自动确认收货`
  return ({ 0: '付款后将安排发货', 1: '商家正在准备商品', 2: '包裹已发出，可查看物流进度', 3: '订单已完成', 4: '订单已取消' }[Number(item.order?.status)] || '订单处理中')
}
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

const revealActiveTab = () => nextTick(() => {
  if (disposed || !orderTabs.value) return
  const target = orderTabs.value.querySelector(`[data-order-tab="${activeTab.value}"]`)
  if (!target) return
  const left = target.offsetLeft - (orderTabs.value.clientWidth - target.offsetWidth) / 2
  orderTabs.value.scrollTo({ left: Math.max(0, left), behavior: 'smooth' })
})

watch(activeTab, () => { revealActiveTab(); fetchOrders() })
onMounted(() => {
  revealActiveTab()
  fetchOrderSummary()
  fetchOrders()
  stopOrderRealtime = connectOrderRealtime({
    onEvent: scheduleRealtimeRefresh,
    onStatus: setRealtimeConnected,
  })
})
onBeforeUnmount(() => {
  disposed = true
  stopOrderRealtime?.()
  window.clearInterval(fallbackPollTimer)
  window.clearTimeout(realtimeRefreshTimer)
})
</script>

<style scoped>
.orders-page { width: min(760px, calc(100% - 40px)); max-width: 760px; min-width: 0; overflow-x: hidden; }
.orders-head { display: grid; grid-template-columns: 34px 1fr 34px; align-items: center; margin-bottom: 10px; }
.orders-head h2 { margin: 0; text-align: center; font-size: 21px; }
.back-link { display: grid; place-items: center; width: 34px; height: 34px; color: var(--ink); background: #f1f3f6; border: 1px solid #e3e7ed; border-radius: 50%; }
.refresh-btn { display: grid; place-items: center; width: 34px; height: 34px; padding: 0; color: var(--ink); background: #f1f3f6; border: 1px solid #e3e7ed; border-radius: 50%; cursor: pointer; }
.refresh-btn:disabled { opacity: .5; cursor: not-allowed; }
.refresh-btn.spinning svg { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.order-tabs-shell { position: sticky; top: 0; z-index: 5; width: 100%; min-width: 0; max-width: 100%; overflow: hidden; background: #fff; border: 1px solid var(--line); border-radius: 10px; }
.order-tabs { display: flex; width: 100%; max-width: 100%; gap: 3px; padding: 0 6px; overflow-x: auto; overflow-y: hidden; overscroll-behavior-x: contain; background: #fff; scrollbar-width: none; -webkit-overflow-scrolling: touch; }
.order-tabs::-webkit-scrollbar { display: none; }
.order-tabs a { position: relative; flex: 1 0 auto; min-width: 62px; padding: 13px 7px 11px; color: var(--muted); text-align: center; font-size: 13px; white-space: nowrap; border-bottom: 2px solid transparent; }
.order-tabs a.active { color: var(--accent, #e7193f); border-bottom-color: var(--accent, #e7193f); font-weight: 800; }
.order-tabs em { position: absolute; top: 4px; margin-left: 1px; color: var(--accent, #e7193f); font-size: 9px; font-style: normal; }
.order-card-list { display: grid; gap: 8px; margin-top: 10px; }
.order-card { overflow: hidden; }
.order-actions :disabled { opacity: .55; }
.load-more-orders { width:100%; padding:12px; color:var(--accent,#e7193f); background:var(--brand-primary-soft,#fff1f4); border:1px solid var(--brand-primary-soft,#f8ccd5); border-radius:10px; font-weight:700; cursor:pointer; }
.load-more-orders:disabled { opacity:.55; cursor:not-allowed; }
.compact-empty { min-height: 280px; margin-top: 11px; }
.compact-empty svg { color: #aab2ae; }

@media (max-width: 920px) {
  .orders-page { width: 100%; max-width: 100%; padding-top: 10px; }
  .orders-head { padding: 0 12px; }
  .order-tabs-shell { border-width: 1px 0; border-radius: 0; }
  .order-card-list { padding: 0 9px; }
}
</style>
