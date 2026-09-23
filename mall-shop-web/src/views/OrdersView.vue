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
      <article v-for="item in filteredOrders" :key="item.order.id" class="order-card ui-card ui-order-card" :data-order-no="item.order.orderNo">
        <div class="ui-order-header">
          <RouterLink :to="`/orders/${item.order.id}`" class="ui-order-heading">
            <strong class="ui-order-merchant">{{ item.order.merchantName || '商城订单' }}</strong>
            <strong class="ui-status-pill ui-order-status" :class="orderStateClass(item)">{{ orderDisplayStatus(item) }}</strong>
          </RouterLink>
          <button type="button" class="ui-copy-action ui-order-copy" @click="copyOrderNumber(item)">{{ copiedOrderId === item.order.id ? '已复制' : '复制订单号' }}</button>
        </div>
        <div class="ui-order-products">
          <RouterLink v-for="line in item.items" :key="line.id" :to="`/orders/${item.order.id}`" class="ui-order-product">
            <img :src="line.productCover" :alt="line.productName" @error="applyImageFallback" />
            <span class="ui-order-product-copy">
              <strong class="ui-order-product-name">{{ line.productName }}</strong>
              <span class="ui-order-product-spec"><span>{{ formatProductSpec(line) || '默认规格' }}</span><span>× {{ line.quantity || 0 }}</span></span>
              <span v-if="serviceTags(line).length" class="ui-order-service-tags"><span v-for="tag in serviceTags(line)" :key="tag">{{ tag }}</span></span>
              <span class="ui-order-product-prices"><span>零售价 ¥{{ money(line.price) }}</span><span>{{ lineAmountLabel(item) }} <strong class="ui-price">¥{{ linePaidAmount(item, line) }}</strong></span></span>
            </span>
          </RouterLink>
        </div>
        <div v-if="orderAutoReceiveText(item)" class="ui-order-logistics">
          <span>{{ orderAutoReceiveText(item) }}</span>
          <RouterLink v-if="item.order.status === 2 && item.autoReceiveEnabled && !isAfterSale(item) && canApplyAfterSale(item)" class="ui-order-logistics-action" :to="`/orders/${item.order.id}?applyAfterSale=1`">未收到 / 拒收</RouterLink>
        </div>
        <div class="ui-order-summary">
          <span>共 {{ totalQuantity(item) }} 件</span>
          <span>实付 <strong class="ui-price">¥{{ money(item.order.payAmount) }}</strong></span>
        </div>
        <div class="order-actions ui-action-bar ui-order-actions">
          <a v-if="firstTrackingUrl(item)" class="order-action btn secondary ui-action-button ui-order-action" :href="firstTrackingUrl(item)" target="_blank" rel="noopener">查看物流</a>
          <button v-if="item.order.status === 0 && isTradeActionOwner(item)" class="order-action btn secondary ui-action-button ui-order-action" :disabled="actingId === item.order.id" @click="requestOrderAction('cancel', item.order.id)">取消订单</button>
          <RouterLink v-if="canApplyAfterSale(item)" class="order-action btn secondary ui-action-button ui-order-action" :to="`/orders/${item.order.id}?applyAfterSale=1`">退换/售后</RouterLink>
          <button v-if="canRebuy(item)" class="order-action btn secondary ui-action-button ui-order-action" :disabled="Boolean(rebuyId)" @click="buyAgain(item)">{{ rebuyId === item.order.id ? '处理中…' : '再买一单' }}</button>
          <RouterLink v-if="Number(item.pendingReviewCount || 0) > 0" class="order-action btn secondary ui-action-button ui-order-action" :to="reviewLink(item)">去评价</RouterLink>
          <RouterLink v-if="item.order.status === 0 && isTradeActionOwner(item)" class="order-action btn primary ui-action-button ui-action-button--primary ui-order-action ui-order-action--primary" :to="`/orders/${item.order.id}`">立即支付</RouterLink>
          <button v-if="item.order.status === 2 && !isAfterSale(item)" class="order-action btn primary ui-action-button ui-action-button--primary ui-order-action ui-order-action--primary" :disabled="actingId === item.order.id" @click="requestOrderAction('receive', item.order.id)">确认收货</button>
        </div>
      </article>
      <button v-if="hasMore" class="load-more-orders" :disabled="loadingMore" @click="loadMore">
        {{ loadingMore ? '正在加载...' : '加载更多订单' }}
      </button>
    </section>
    <div v-if="orderActionNotice" class="order-action-toast" role="status">{{ orderActionNotice }}</div>
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
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, PackageOpen, RefreshCw } from 'lucide-vue-next'
import { cancelOrder, confirmReceive, getProduct, getProfileOrderSummary, listMyOrders } from '@/api/shop'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { dateTime, money, statusName } from '@/utils/format'
import { formatProductSpec } from '@/utils/productSpec'
import { connectOrderRealtime } from '@/utils/orderRealtime'
import { applyImageFallback } from '@/utils/imageFallback'
import { isTradeActionOwner as ownsTradeAction, canApplyAfterSale } from '@/utils/orderListRules'
import { useCart } from '@/store/cart'
import { checkCartPurchaseLimit } from '@/utils/purchaseLimit'
import { requireShopSession } from '@/utils/authNavigation'
import { cartItemKey, stockAdditionViolation } from '@/utils/stockRules'

const route = useRoute()
const router = useRouter()
const { items: cartItems, addMany } = useCart()
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
const rebuyId = ref(null)
const copiedOrderId = ref(null)
const orderActionNotice = ref('')
const pendingOrderAction = ref({ type: '', id: null })
const orderTabs = ref(null)
let requestSequence = 0
let stopOrderRealtime = null
let fallbackPollTimer = null
let realtimeRefreshTimer = null
let disposed = false
let copyTimer = null
let noticeTimer = null
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
const parseArray = (value) => {
  if (Array.isArray(value)) return value
  try { const parsed = JSON.parse(value || '[]'); return Array.isArray(parsed) ? parsed : [] } catch { return [] }
}
const serviceTags = (line) => parseArray(line?.serviceTags)
  .map((tag) => typeof tag === 'string' ? { title: tag, enabled: true } : tag)
  .filter((tag) => tag?.enabled !== false && String(tag?.title || '').trim())
  .slice(0, 2)
  .map((tag) => String(tag.title).trim())
const lineAmountLabel = (item) => [0, 4].includes(Number(item.order?.status)) ? '商品小计' : '实付款'
const linePaidAmount = (item, line) => {
  const quantity = Math.max(1, Number(line?.quantity || 1))
  const total = Number(line?.totalAmount ?? Number(line?.price || 0) * quantity)
  return money([0, 4].includes(Number(item.order?.status)) ? total : Math.max(0, total - Number(line?.couponDiscountAmount || 0)))
}
const canRebuy = (item) => Number(item.order?.status) !== 0 && (item.items || []).some((line) => line.productId)
const reviewLink = (item) => ({
  path: `/order-review/${item.pendingReviewProductId}`,
  query: item.pendingReviewOrderItemId ? { orderItemId: item.pendingReviewOrderItemId } : {},
})
const afterSaleStatus = (status, applyType) => {
  if (Number(applyType) === 3 && Number(status) === 1) return '换货完成'
  return ({ 0: '待审核', 1: '退款完成', 2: '已拒绝', 3: '已取消', 4: '待客户寄回', 5: '待商家收货', 6: '退款处理中', 7: '待商家换货发出', 8: '换货已发出' }[status] || '处理中')
}
const isRefundedOrder = (item) => Number(item.order?.status) === 4 && (item.afterSales || [])
  .some((sale) => [1, 2].includes(Number(sale.applyType)) && Number(sale.status) === 1)
const orderDisplayStatus = (item) => {
  if (isAfterSale(item)) {
    const sale = activeAfterSales(item)[0]
    return `售后 · ${afterSaleStatus(sale?.status, sale?.applyType)}`
  }
  if (Number(item.pendingReviewCount || 0) > 0) return '待评价'
  if (isRefundedOrder(item)) return '已退款'
  if (Number(item.order?.status) === 4) return '已取消'
  return statusName(item.order?.status)
}
const orderStateClass = (item) => {
  if (Number(item.order?.status) === 4) return 'is-cancelled'
  if (Number(item.order?.status) === 3 && !isAfterSale(item) && Number(item.pendingReviewCount || 0) === 0) return 'is-completed'
  return 'is-active'
}
const orderAutoReceiveText = (item) => Number(item.order?.status) === 2 && item.autoReceiveEnabled && !isAfterSale(item)
  ? (item.autoReceiveDeadline ? `预计 ${dateTime(item.autoReceiveDeadline)} 自动确认收货` : `发货满 ${Number(item.autoReceiveDays || 15)} 天自动确认收货`)
  : ''
const trackingUrl = (shipment) => shipment?.deliveryNo ? `https://m.kuaidi100.com/result.jsp?nu=${encodeURIComponent(shipment.deliveryNo)}` : ''
const firstTrackingUrl = (item) => trackingUrl((item.shipments || []).find((shipment) => shipment.deliveryNo))
const notifyAction = (message) => {
  orderActionNotice.value = message
  window.clearTimeout(noticeTimer)
  noticeTimer = window.setTimeout(() => { orderActionNotice.value = '' }, 2200)
}
const copyOrderNumber = async (item) => {
  try {
    await navigator.clipboard.writeText(String(item.order?.orderNo || ''))
    copiedOrderId.value = item.order.id
    notifyAction('订单号已复制')
    window.clearTimeout(copyTimer)
    copyTimer = window.setTimeout(() => { copiedOrderId.value = null }, 1600)
  } catch { notifyAction('复制失败，请打开订单详情后重试') }
}
const buyAgain = async (item) => {
  if (rebuyId.value || !canRebuy(item) || !requireShopSession(router, route.fullPath, '请先登录后再购买')) return
  rebuyId.value = item.order.id
  try {
    const planned = cartItems.map((line) => ({ ...line }))
    const selections = []
    for (const line of item.items || []) {
      const response = await getProduct(line.productId)
      const detail = response.data || {}
      const product = detail.product || detail
      if (!product?.id || Number(product.status ?? 1) !== 1) throw new Error(`${line.productName || '商品'}已下架`)
      const skus = Array.isArray(detail.skus) ? detail.skus : []
      const sku = line.skuId ? skus.find((row) => String(row.id) === String(line.skuId)) : null
      if (skus.length && (!sku || Number(sku.status ?? 1) !== 1)) throw new Error(`${line.productName || '商品'}的原规格已失效`)
      const quantity = Math.max(1, Number(line.quantity || 1))
      const selection = {
        id: product.id,
        skuId: sku?.id || null,
        productName: product.productName,
        skuName: sku?.skuName || '',
        skuAttrs: sku?.attrsJson || '',
        subtitle: product.subtitle || '',
        merchantName: product.merchantName || '',
        coverUrl: sku?.imageUrl || product.coverUrl,
        salePrice: Number(sku ? sku.salePrice : product.salePrice),
        marketPrice: Number(sku ? sku.marketPrice || 0 : product.marketPrice || 0),
        pvValue: Number(sku?.pvValue || product.pvValue || 0),
        stock: Number(sku ? sku.stock : product.stock),
        purchaseLimit: Number(product.purchaseLimit || 0),
        quantity,
      }
      const key = cartItemKey(selection)
      const existingSku = planned.find((row) => (row.cartKey || cartItemKey(row)) === key)
      const stockError = stockAdditionViolation(selection.stock, quantity, existingSku?.quantity || 0)
      if (stockError) throw new Error(stockError)
      const existingProductQuantity = planned.reduce((sum, row) => String(row.id) === String(product.id) ? sum + Number(row.quantity || 0) : sum, 0)
      await checkCartPurchaseLimit(product, quantity, existingProductQuantity)
      if (existingSku) existingSku.quantity = Number(existingSku.quantity || 0) + quantity
      else planned.push({ ...selection, cartKey: key })
      selections.push(selection)
    }
    addMany(selections)
    router.push('/cart')
  } catch (error) { notifyAction(error?.message || '商品信息已变化，请重新选择') }
  finally { rebuyId.value = null }
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
  window.clearTimeout(copyTimer)
  window.clearTimeout(noticeTimer)
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
.order-action-toast { position: fixed; left: 50%; bottom: calc(78px + env(safe-area-inset-bottom)); z-index: 30; transform: translateX(-50%); padding: 9px 14px; border-radius: 999px; color: #fff; background: rgba(24, 31, 42, .88); font-size: 12px; white-space: nowrap; }
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
