<template>
  <div class="page">
    <div class="section-head">
      <h2>订单详情</h2>
      <RouterLink v-if="!applyingAfterSale" class="btn secondary" to="/orders">
        <UserRound :size="18" />
        我的订单
      </RouterLink>
    </div>

    <div v-if="loading" class="empty">加载中</div>
    <div v-else-if="!hasToken" class="empty">
      <div>
        <p>请先登录查看订单</p>
        <RouterLink class="btn primary" to="/login">去登录</RouterLink>
      </div>
    </div>
    <div v-else-if="!order" class="empty">订单不存在</div>
    <div v-else class="checkout-layout" :class="{ 'after-sale-mode': applyingAfterSale }">
      <section v-if="!applyingAfterSale && shipments.length" class="panel logistics-overview-panel">
        <div class="logistics-overview-head">
          <span class="logistics-overview-icon"><Truck :size="22" /></span>
          <div class="logistics-overview-status">
            <strong>{{ logisticsStatus }}</strong>
            <span>{{ logisticsStatusDescription }}</span>
          </div>
          <a v-if="trackingUrl(shipments[0])" :href="trackingUrl(shipments[0])" target="_blank" rel="noopener" class="logistics-overview-link">
            查看物流
            <ChevronRight :size="17" />
          </a>
        </div>
        <div v-for="(shipment, index) in shipments" :key="`${shipment.deliveryCompany}-${shipment.deliveryNo}-${index}`" class="logistics-package-row">
          <span class="courier-icon">{{ courierInitial(shipment.deliveryCompany) }}</span>
          <div>
            <strong>{{ shipment.deliveryCompany || '快递公司' }}</strong>
            <p>包裹 {{ index + 1 }} · 运单号 {{ shipment.deliveryNo || '-' }}</p>
          </div>
          <button v-if="shipment.deliveryNo" type="button" class="copy-btn" @click="copyText(shipment.deliveryNo)">复制</button>
        </div>
        <div class="delivery-address-row">
          <MapPin :size="20" />
          <div>
            <strong>{{ order.receiverName }} {{ order.receiverPhone }}</strong>
            <span>{{ order.receiverAddress }}</span>
          </div>
        </div>
      </section>
      <section class="panel">
        <div ref="refundItemsSection" class="product-detail-head" :class="{ 'has-validation-error': applyingAfterSale && afterSaleErrors.items }">
          <h3>{{ applyingAfterSale ? '选择商品和数量' : '商品明细' }}<span v-if="applyingAfterSale" class="required-star">*</span></h3>
          <span v-if="applyingAfterSale">已默认全选</span>
        </div>
        <div v-for="item in detail.items" :key="item.id" class="order-line">
          <img :src="item.productCover" :alt="item.productName" />
          <div class="order-line-info">
            <p class="line-title">{{ item.productName }}</p>
            <p class="line-sub">
              {{ formatProductSpec(item) }} · x {{ item.quantity }}
              <span v-if="showPv"> · PV {{ money(item.totalPv) }}</span>
            </p>
          </div>
          <div class="order-line-trailing">
            <strong class="order-line-amount">¥{{ money(item.totalAmount) }}</strong>
            <div v-if="applyingAfterSale && remainingQuantity(item) > 0" class="quantity-stepper" :aria-label="`${item.productName}退款数量`">
              <button type="button" :disabled="refundQuantities[item.id] <= 0" @click="setRefundQuantity(item, -1)">−</button>
              <output>{{ refundQuantities[item.id] || 0 }}</output>
              <button type="button" :disabled="remainingQuantity(item) <= (refundQuantities[item.id] || 0)" @click="setRefundQuantity(item, 1)">＋</button>
            </div>
            <small v-else-if="applyingAfterSale" class="refunded-label">已无可售后数量</small>
          </div>
        </div>
        <p v-if="applyingAfterSale && afterSaleErrors.items" class="after-sale-field-error" role="alert">{{ afterSaleErrors.items }}</p>

        <div v-if="afterSales.length" class="after-sale-list">
          <div class="after-sale-section-head">
            <div>
              <span class="section-eyebrow">售后进度</span>
              <h3>退款 / 售后记录</h3>
            </div>
            <span class="section-helper">进度实时更新</span>
          </div>
          <div v-for="sale in afterSales" :key="sale.id" class="after-sale-record">
            <div class="after-sale-record-head">
              <span class="after-sale-status-dot" :class="`status-${sale.status}`"></span>
              <div class="after-sale-record-title">
                <strong>{{ afterSaleStatus(sale.status) }}</strong>
                <span>申请单号 {{ sale.afterSaleNo }}</span>
              </div>
              <strong class="refund-total">¥{{ money(sale.refundAmount) }}</strong>
            </div>
            <div class="after-sale-progress" :class="`progress-${sale.status}`" aria-label="售后处理进度">
              <span class="progress-step complete">提交申请</span>
              <span class="progress-track"></span>
              <span class="progress-step" :class="{ complete: sale.status !== 0 && sale.status !== 3 }">平台审核</span>
              <span class="progress-track"></span>
              <span class="progress-step" :class="{ complete: sale.status === 1 }">处理完成</span>
            </div>
            <p class="line-sub after-sale-amounts">商品 {{ sale.refundQuantity || 0 }} 件 · 商品款 ¥{{ money(sale.productRefundAmount) }} · 运费 ¥{{ money(sale.freightRefundAmount) }}</p>
            <div v-if="sale.applyType === 2 && [4, 5].includes(sale.status)" class="after-sale-return-address">
              <strong>{{ sale.status === 4 ? '请寄回商品' : '退货物流已提交' }}</strong>
              <span>{{ sale.returnAddress || '退货地址将在审核结果中显示，请留意订单更新' }}</span>
              <div v-if="sale.status === 4" class="return-shipment-form">
                <input v-model="returnShipmentForm.deliveryCompany" class="field" placeholder="物流公司" maxlength="64" />
                <input v-model="returnShipmentForm.deliveryNo" class="field" placeholder="退货运单号" maxlength="128" />
                <button type="button" class="btn primary" :disabled="returnShipmentSaleId === sale.id" @click="submitReturnShipment(sale)">
                  {{ returnShipmentSaleId === sale.id ? '提交中…' : '提交退货物流' }}
                </button>
              </div>
              <small v-else class="return-logistics-line">
                物流公司：{{ sale.returnDeliveryCompany || '未填写' }} · 运单号：{{ sale.returnDeliveryNo || '-' }}，等待商家确认收货
                <a v-if="sale.returnDeliveryNo" :href="trackingUrl({ deliveryCompany: sale.returnDeliveryCompany, deliveryNo: sale.returnDeliveryNo })" target="_blank" rel="noopener" class="tracking-link">查看物流轨迹</a>
              </small>
            </div>
            <div v-for="line in sale.items || []" :key="line.id" class="after-sale-item-line">
              <span>{{ line.productName }} {{ formatProductSpec(line) }}</span>
              <strong>× {{ line.refundQuantity }}</strong>
            </div>
            <div v-if="sale.status === 0" class="after-sale-record-actions">
              <button type="button" class="btn secondary after-sale-cancel" :disabled="cancellingAfterSaleId === sale.id" @click="cancelAfterSale(sale.id)">
                {{ cancellingAfterSaleId === sale.id ? '取消中…' : '取消申请' }}
              </button>
            </div>
          </div>
        </div>

        <div v-if="canApplyAfterSale && applyingAfterSale" class="after-sale-box">
          <div class="after-sale-block">
            <div class="block-label">售后类型</div>
            <div class="after-sale-type-grid">
              <button type="button" class="after-sale-type" :class="{ selected: afterSaleForm.applyType === 1 }" @click="afterSaleForm.applyType = 1">
                <RotateCcw :size="21" />
                <span><strong>仅退款</strong><small>无需寄回商品</small></span>
                <CircleCheck v-if="afterSaleForm.applyType === 1" :size="18" class="type-check" />
              </button>
              <button type="button" class="after-sale-type" :class="{ selected: afterSaleForm.applyType === 2 }" @click="afterSaleForm.applyType = 2">
                <PackageCheck :size="21" />
                <span><strong>退货退款</strong><small>需要寄回商品</small></span>
                <CircleCheck v-if="afterSaleForm.applyType === 2" :size="18" class="type-check" />
              </button>
            </div>
          </div>

          <div ref="reasonSection" class="after-sale-block" :class="{ 'has-validation-error': afterSaleErrors.reason }">
            <div class="block-label">申请原因<span class="required-star">*</span></div>
            <button type="button" class="reason-select" :class="{ selected: selectedReason, invalid: afterSaleErrors.reason }" @click="reasonSheetVisible = true">
              <span>{{ selectedReason || '请选择申请原因' }}</span>
              <ChevronRight :size="18" />
            </button>
            <p v-if="afterSaleErrors.reason" class="after-sale-field-error" role="alert">{{ afterSaleErrors.reason }}</p>
            <textarea v-model="afterSaleForm.reasonDetail" class="textarea reason-detail" maxlength="170" placeholder="补充说明（选填），帮助平台更快处理"></textarea>
            <div class="reason-counter">{{ afterSaleForm.reasonDetail.length }}/170</div>
          </div>

          <div class="refund-estimate">
            <div class="estimate-head"><span>预计退款</span><strong>¥{{ money(estimatedProductRefund + estimatedFreightRefund) }}</strong></div>
          </div>
          <p v-if="afterSaleErrors.server" class="after-sale-submit-error" role="alert">{{ afterSaleErrors.server }}</p>
          <button class="btn primary after-sale-submit" :disabled="submittingAfterSale" @click="submitAfterSale">
            {{ submittingAfterSale ? '提交中…' : '提交申请' }}
          </button>
        </div>
      </section>
      <aside v-if="!applyingAfterSale" class="panel">
        <div class="summary-row">
          <span>订单状态</span>
          <strong>{{ statusName(order.status) }}</strong>
        </div>
        <div class="summary-row">
          <span>商品金额</span>
          <strong>¥{{ money(order.totalAmount) }}</strong>
        </div>
        <div class="summary-row">
          <span>实付金额</span>
          <strong>¥{{ money(order.payAmount) }}</strong>
        </div>
        <div class="summary-row">
          <span>支付方式</span>
          <strong>{{ payTypeName(order.payType) }}</strong>
        </div>
        <div class="order-info-card">
          <button type="button" class="order-info-toggle" :aria-expanded="orderInfoExpanded" @click="orderInfoExpanded = !orderInfoExpanded">
            <span>订单信息 <small>共5项</small></span>
            <span class="order-info-preview">{{ orderInfoExpanded ? '收起' : order.orderNo }}</span>
            <ChevronDown :size="19" :class="{ expanded: orderInfoExpanded }" />
          </button>
          <div v-if="orderInfoExpanded" class="order-info-details">
            <div class="order-info-row">
              <span>订单号</span>
              <strong>{{ order.orderNo }}</strong>
              <button type="button" @click="copyText(order.orderNo)">复制</button>
            </div>
            <div class="order-info-row"><span>创建时间</span><strong>{{ dateTime(order.createTime) }}</strong></div>
            <div class="order-info-row"><span>付款时间</span><strong>{{ dateTime(order.payTime) }}</strong></div>
            <div class="order-info-row"><span>发货时间</span><strong>{{ dateTime(order.deliveryTime) }}</strong></div>
            <div class="order-info-row"><span>运费</span><strong>¥{{ money(order.freightAmount) }}</strong></div>
          </div>
        </div>
        <div v-if="order.status === 0 && order.payType === 'BALANCE'" class="balance-pay-box">
          <label>支付密码</label>
          <input
            v-model="paymentPassword"
            class="field"
            type="password"
            inputmode="numeric"
            maxlength="6"
            autocomplete="off"
            placeholder="请输入6位支付密码"
          />
          <p class="line-sub">将从商城余额扣除 ¥{{ money(order.payAmount) }}，运费包含在实付金额内。</p>
        </div>
        <p v-if="order.status === 0 && order.payType !== 'BALANCE'" class="channel-tip">
          {{ payTypeName(order.payType) }}订单已保留；配置正式商户号和密钥后会唤起对应支付页面。
        </p>
        <div class="inline-actions">
          <button v-if="canApplyAfterSale && !applyingAfterSale" class="btn secondary" @click="startAfterSale">申请售后</button>
          <button v-if="order.status === 0" class="btn secondary" :disabled="acting" @click="cancel">取消订单</button>
          <button v-if="order.status === 0" class="btn primary" :disabled="acting" @click="pay">立即支付</button>
          <button v-if="order.status === 2" class="btn primary" :disabled="acting" @click="receive">确认收货</button>
        </div>
        <p v-if="error" style="color: var(--coral); line-height: 1.6">{{ error }}</p>
      </aside>
    </div>
    <div v-if="reasonSheetVisible" class="reason-sheet-backdrop" @click.self="reasonSheetVisible = false">
      <section class="reason-sheet" role="dialog" aria-modal="true" aria-labelledby="reason-sheet-title">
        <div class="reason-sheet-head">
          <h3 id="reason-sheet-title">请选择申请原因</h3>
          <button type="button" aria-label="关闭" @click="reasonSheetVisible = false">×</button>
        </div>
        <button v-for="reason in afterSaleReasons" :key="reason" type="button" class="reason-option" :class="{ selected: selectedReason === reason }" @click="selectAfterSaleReason(reason)">
          <span>{{ reason }}</span>
          <CircleCheck v-if="selectedReason === reason" :size="19" />
        </button>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronDown, ChevronRight, CircleCheck, MapPin, PackageCheck, RotateCcw, Truck, UserRound } from 'lucide-vue-next'
import { applyAfterSale, cancelAfterSale as cancelAfterSaleRequest, cancelOrder, confirmReceive, getOrder, payOrderWithBalance, submitAfterSaleReturnShipment } from '@/api/shop'
import { dateTime, money, statusName } from '@/utils/format'
import { formatProductSpec } from '@/utils/productSpec'

const route = useRoute()
const detail = ref({})
const loading = ref(false)
const acting = ref(false)
const cancellingAfterSaleId = ref(null)
const returnShipmentSaleId = ref(null)
const returnShipmentForm = ref({ deliveryCompany: '', deliveryNo: '' })
const submittingAfterSale = ref(false)
const reasonSheetVisible = ref(false)
const selectedReason = ref('')
const orderInfoExpanded = ref(false)
const refundItemsSection = ref(null)
const reasonSection = ref(null)
const afterSaleErrors = ref({ items: '', reason: '', server: '' })
const afterSaleReasons = ['不想要了', '与商品描述不符', '质量问题', '收到商品少件 / 漏发', '商品破损或污渍', '商家发错货', '其他原因']
const error = ref('')
const hasToken = ref(Boolean(localStorage.getItem('shop_token')))
const paymentPassword = ref('')
const applyingAfterSale = ref(route.query.applyAfterSale === '1')
const order = computed(() => detail.value.order)
const shipments = computed(() => {
  if (detail.value.shipments?.length) return detail.value.shipments
  if (order.value?.deliveryNo) {
    return [{
      deliveryCompany: order.value.deliveryCompany,
      deliveryNo: order.value.deliveryNo,
      shipmentQuantity: (detail.value.items || []).reduce((sum, item) => sum + Number(item?.quantity || 0), 0),
      deliveryTime: order.value.deliveryTime,
    }]
  }
  return []
})
const logisticsStatus = computed(() => {
  if (order.value?.receiveTime || Number(order.value?.status) === 3) return '已签收'
  if (Number(order.value?.status) === 2) return '运输中'
  return '已发货'
})
const logisticsStatusDescription = computed(() => {
  if (logisticsStatus.value === '已签收') return order.value?.receiveTime ? `签收时间 ${dateTime(order.value.receiveTime)}` : '包裹已签收'
  if (logisticsStatus.value === '运输中') return '包裹正在运输，请留意物流更新'
  return order.value?.deliveryTime ? `发货时间 ${dateTime(order.value.deliveryTime)}` : '商家已发出商品'
})
const afterSales = computed(() => detail.value.afterSales || [])
const displayConfig = computed(() => detail.value.displayConfig || {})
const showPv = computed(() => Number(displayConfig.value.showPv || 0) === 1)
const afterSaleDeadline = computed(() => {
  const created = Date.parse(String(order.value?.createTime || '').replace(' ', 'T'))
  return Number.isFinite(created) ? created + 7 * 24 * 60 * 60 * 1000 : Number.POSITIVE_INFINITY
})
const canApplyAfterSale = computed(() => {
  if (!order.value) return false
  if ([0, 4].includes(order.value.status)) return false
  if (Date.now() >= afterSaleDeadline.value) return false
  if (afterSales.value.some((item) => [0, 4, 5, 6].includes(item.status))) return false
  return totalRemainingQuantity.value > 0
})
const afterSaleForm = ref({
  applyType: 1,
  reason: '',
  reasonDetail: '',
})
const refundQuantities = ref({})

const usedQuantity = (orderItemId) => afterSales.value
  .filter((sale) => [0, 1, 4, 5, 6].includes(sale.status))
  .flatMap((sale) => sale.items || [])
  .filter((item) => item.orderItemId === orderItemId)
  .reduce((sum, item) => sum + Number(item.refundQuantity || 0), 0)

const remainingQuantity = (item) => Math.max(0, Number(item.quantity || 0) - usedQuantity(item.id))
const selectedRefundItems = computed(() => (detail.value.items || [])
  .map((item) => ({ orderItemId: item.id, quantity: Math.max(0, Math.min(remainingQuantity(item), Math.trunc(Number(refundQuantities.value[item.id] || 0)))) }))
  .filter((item) => item.quantity > 0))
const selectedRefundQuantity = computed(() => selectedRefundItems.value.reduce((sum, item) => sum + item.quantity, 0))
const totalRemainingQuantity = computed(() => (detail.value.items || []).reduce((sum, item) => sum + remainingQuantity(item), 0))
const refundAllRemaining = computed(() => selectedRefundQuantity.value > 0 && selectedRefundQuantity.value === totalRemainingQuantity.value)
const approvedProductRefund = computed(() => afterSales.value
  .filter((sale) => sale.status === 1)
  .reduce((sum, sale) => sum + Number(sale.productRefundAmount || 0), 0))
const productBase = computed(() => Math.max(0, Number(order.value?.totalAmount || 0) - Number(order.value?.discountAmount || 0)))
const estimatedProductRefund = computed(() => {
  const remainingAmount = Math.max(0, productBase.value - approvedProductRefund.value)
  if (refundAllRemaining.value) return remainingAmount
  const grossTotal = (detail.value.items || []).reduce((sum, item) => sum + Number(item.totalAmount || 0), 0)
  if (!grossTotal) return 0
  const amount = selectedRefundItems.value.reduce((sum, selected) => {
    const item = (detail.value.items || []).find((line) => line.id === selected.orderItemId)
    if (!item || !item.quantity) return sum
    return sum + Number(item.totalAmount || 0) * selected.quantity / Number(item.quantity)
  }, 0) * productBase.value / grossTotal
  return Math.min(remainingAmount, amount)
})
const notShipped = computed(() => order.value?.status === 1 && !order.value?.deliveryTime)
const estimatedFreightRefund = computed(() => notShipped.value && refundAllRemaining.value ? Number(order.value?.freightAmount || 0) : 0)
const setRefundQuantity = (item, delta) => {
  const current = Number(refundQuantities.value[item.id] || 0)
  refundQuantities.value[item.id] = Math.max(0, Math.min(remainingQuantity(item), current + delta))
  if (selectedRefundItems.value.length) afterSaleErrors.value.items = ''
}

const selectAllRefundableItems = () => {
  refundQuantities.value = Object.fromEntries((detail.value.items || [])
    .map((item) => [item.id, remainingQuantity(item)]))
}

const startAfterSale = () => {
  selectAllRefundableItems()
  afterSaleErrors.value = { items: '', reason: '', server: '' }
  applyingAfterSale.value = true
}

const selectAfterSaleReason = (reason) => {
  selectedReason.value = reason
  afterSaleForm.value.reason = reason
  afterSaleErrors.value.reason = ''
  reasonSheetVisible.value = false
}

const scrollToAfterSaleError = async (target) => {
  await nextTick()
  target?.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

const afterSaleStatus = (status) => ({ 0: '待审核', 1: '退款完成', 2: '已拒绝', 3: '已取消', 4: '审核通过，待寄回', 5: '已寄回，待收货', 6: '已收货，退款中' }[status] || '处理中')
const payTypeName = (value) => ({ WECHAT: '微信支付', ALIPAY: '支付宝', BALANCE: '余额' }[value] || value || '未选择')
const copyText = async (text) => { try { await navigator.clipboard.writeText(text) } catch {} }

const cancelAfterSale = async (id) => {
  if (cancellingAfterSaleId.value) return
  if (!window.confirm('确定取消这笔售后申请吗？取消后不会产生退款，仍可在售后期限内重新申请。')) return
  cancellingAfterSaleId.value = id
  error.value = ''
  try {
    await cancelAfterSaleRequest(id)
    await fetchOrder()
  } catch (e) {
    error.value = e.message || '取消售后申请失败'
  } finally {
    cancellingAfterSaleId.value = null
  }
}

const submitReturnShipment = async (sale) => {
  const deliveryCompany = returnShipmentForm.value.deliveryCompany.trim()
  const deliveryNo = returnShipmentForm.value.deliveryNo.trim()
  if (!deliveryCompany || !deliveryNo) {
    error.value = '请填写物流公司和退货运单号'
    return
  }
  returnShipmentSaleId.value = sale.id
  error.value = ''
  try {
    await submitAfterSaleReturnShipment(sale.id, { deliveryCompany, deliveryNo })
    returnShipmentForm.value = { deliveryCompany: '', deliveryNo: '' }
    await fetchOrder()
  } catch (e) {
    error.value = e.message || '提交退货物流失败'
  } finally {
    returnShipmentSaleId.value = null
  }
}

const courierInitial = (company) => {
  if (!company) return '递'
  const map = { '顺丰': '丰', '中通': '中', '圆通': '圆', '韵达': '韵', '申通': '申', '京东': '京', '邮政': '政', '极兔': '极', '德邦': '德', '百世': '百', 'EMS': 'E' }
  for (const [key, val] of Object.entries(map)) {
    if (company.includes(key)) return val
  }
  return company.slice(0, 2)
}

const trackingUrl = (shipment) => {
  const no = shipment.deliveryNo
  if (!no) return null
  // Kuaidi100 auto-detects the carrier from the waybill number. The carrier
  // name is shown in our UI, while the external query stays compatible with
  // both Chinese carrier names and carrier codes.
  return `https://m.kuaidi100.com/result.jsp?nu=${encodeURIComponent(no)}`
}

const fetchOrder = async () => {
  if (!hasToken.value) return
  loading.value = true
  error.value = ''
  try {
    const res = await getOrder(route.params.id)
    detail.value = res.data || {}
    selectAllRefundableItems()
    if (!canApplyAfterSale.value) applyingAfterSale.value = false
    selectedReason.value = ''
    afterSaleForm.value.reason = ''
    afterSaleForm.value.reasonDetail = ''
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

const cancel = async () => {
  acting.value = true
  try {
    await cancelOrder(order.value.id)
    await fetchOrder()
  } catch (e) {
    error.value = e.message || '取消失败'
  } finally {
    acting.value = false
  }
}

const pay = async () => {
  if (acting.value) return
  if (order.value.payType !== 'BALANCE') {
    error.value = `${payTypeName(order.value.payType)}尚未配置正式商户参数；当前可选择余额支付进行完整测试`
    return
  }
  if (!/^\d{6}$/.test(paymentPassword.value)) {
    error.value = '请输入6位支付密码'
    return
  }
  acting.value = true
  error.value = ''
  try {
    await payOrderWithBalance(order.value.id, paymentPassword.value)
    paymentPassword.value = ''
    await fetchOrder()
  } catch (e) {
    error.value = e.message || '支付失败'
  } finally {
    acting.value = false
  }
}

const receive = async () => {
  acting.value = true
  try {
    await confirmReceive(order.value.id)
    await fetchOrder()
  } catch (e) {
    error.value = e.message || '确认失败'
  } finally {
    acting.value = false
  }
}

const submitAfterSale = async () => {
  afterSaleErrors.value = { items: '', reason: '', server: '' }
  if (!selectedRefundItems.value.length) {
    afterSaleErrors.value.items = '退款商品数量不能为 0，请至少选择 1 件商品'
    scrollToAfterSaleError(refundItemsSection)
    return
  }
  if (!selectedReason.value) {
    afterSaleErrors.value.reason = '请选择申请原因'
    scrollToAfterSaleError(reasonSection)
    return
  }
  submittingAfterSale.value = true
  try {
    await applyAfterSale({
      ...afterSaleForm.value,
      reason: [selectedReason.value, afterSaleForm.value.reasonDetail.trim()].filter(Boolean).join('：'),
      orderId: order.value.id,
      items: selectedRefundItems.value,
    })
    afterSaleForm.value.reason = ''
    afterSaleForm.value.reasonDetail = ''
    selectedReason.value = ''
    applyingAfterSale.value = false
    await fetchOrder()
  } catch (e) {
    afterSaleErrors.value.server = e.message || '提交售后失败，请稍后重试'
  } finally {
    submittingAfterSale.value = false
  }
}

onMounted(fetchOrder)
</script>

<style scoped>
.product-detail-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.product-detail-head h3 { margin-bottom: 14px; }
.product-detail-head span { margin-bottom: 14px; color: var(--brand-primary, #e7193f); font-size: 11px; }
.product-detail-head h3 .required-star { margin: 0 0 0 4px; color: #e53232; font-size: 14px; }
.required-star { margin-left: 4px; color: #e53232; font-size: 14px; }
.product-detail-head.has-validation-error { margin: -7px -8px 4px; padding: 7px 8px 0; background: #fff5f5; border: 1px solid #ef4444; border-radius: 10px; }
.order-line { align-items: start; }
.order-line-info { min-width: 0; }
.order-line-trailing { display: grid; justify-items: end; gap: 10px; }
.order-line-amount { color: var(--brand-primary, #e7193f); font-size: 18px; font-weight: 900; white-space: nowrap; }
.refunded-label { color: #9aa3ad; font-size: 11px; white-space: nowrap; }
.checkout-layout.after-sale-mode { grid-template-columns: minmax(0, 760px); justify-content: center; }
.after-sale-list { padding-top: 22px; }
.after-sale-section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.after-sale-section-head h3 { margin: 3px 0 0; font-size: 18px; }
.section-eyebrow { color: var(--brand-primary, #e7193f); font-size: 11px; font-weight: 800; letter-spacing: .08em; }
.section-helper { color: #9aa3ad; font-size: 12px; }
.after-sale-record { padding: 16px; background: linear-gradient(145deg, #fff, #fff8f8); border: 1px solid #f3dce1; border-radius: 16px; box-shadow: 0 8px 20px rgba(231, 25, 63, .06); }
.after-sale-record + .after-sale-record { margin-top: 10px; }
.after-sale-record-head { display: flex; align-items: center; gap: 10px; }
.after-sale-status-dot { width: 10px; height: 10px; flex: 0 0 10px; border-radius: 50%; background: #f59e0b; box-shadow: 0 0 0 4px #fff3d6; }
.after-sale-status-dot.status-1 { background: #16a34a; box-shadow: 0 0 0 4px #dcfce7; }
.after-sale-status-dot.status-2, .after-sale-status-dot.status-3 { background: #9ca3af; box-shadow: 0 0 0 4px #f1f5f9; }
.after-sale-record-title { display: grid; gap: 3px; min-width: 0; }
.after-sale-record-title strong { color: var(--ink); font-size: 15px; }
.after-sale-record-title span { overflow: hidden; color: #8a939e; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.refund-total { margin-left: auto; color: var(--brand-primary, #e7193f); font-size: 18px; }
.after-sale-progress { display: flex; align-items: center; margin: 18px 0 12px; color: #9aa3ad; font-size: 11px; }
.progress-step { white-space: nowrap; }
.progress-step.complete { color: var(--brand-primary, #e7193f); font-weight: 800; }
.progress-track { height: 1px; flex: 1; margin: 0 7px; background: #e4e8ec; }
.progress-1 .progress-track:first-of-type { background: var(--brand-primary, #e7193f); }
.after-sale-amounts { margin: 0 0 9px; }
.after-sale-return-address { display: grid; gap: 4px; margin: 10px 0 4px; padding: 10px 12px; color: #8a4b12; background: #fff8ed; border: 1px solid #f5d7ad; border-radius: 9px; font-size: 12px; line-height: 1.5; }
.after-sale-return-address strong { color: #7a3f0a; font-size: 13px; }
.return-shipment-form { display: grid; grid-template-columns: 1fr 1.2fr auto; gap: 8px; margin-top: 8px; }
.return-shipment-form .field { min-height: 34px; padding: 0 9px; border: 1px solid #e8c996; border-radius: 7px; background: #fff; }
.return-shipment-form .btn { min-height: 34px; padding: 0 11px; border-radius: 7px; font-size: 12px; white-space: nowrap; }
.return-logistics-line { display: block; }
.return-logistics-line .tracking-link { margin-left: 8px; color: var(--teal); text-decoration: none; }
.after-sale-item-line { display: flex; justify-content: space-between; gap: 12px; padding-top: 7px; color: #69737e; font-size: 12px; }
.after-sale-item-line strong { color: #3d4650; font-size: 12px; }
.after-sale-record-actions { display: flex; justify-content: flex-end; margin-top: 12px; padding-top: 11px; border-top: 1px solid #f1e3e6; }
.after-sale-cancel { min-height: 32px; padding: 0 13px; border-radius: 8px; font-size: 12px; }
.after-sale-block { padding: 17px 0; border-top: 1px solid #edf0f2; }
.block-label { margin-bottom: 10px; color: var(--ink); font-size: 14px; font-weight: 800; }
.after-sale-type-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.after-sale-type { position: relative; display: flex; align-items: flex-start; gap: 10px; min-height: 76px; padding: 13px 12px; color: #8b96a1; text-align: left; background: #fff; border: 1px solid #e1e6ea; border-radius: 12px; }
.after-sale-type svg { flex: 0 0 auto; color: #e97947; }
.after-sale-type span { display: grid; gap: 4px; min-width: 0; }
.after-sale-type strong { color: var(--ink); font-size: 13px; }
.after-sale-type small { color: #8b96a1; font-size: 11px; line-height: 1.45; }
.after-sale-type.selected { background: var(--brand-primary-soft, #fff1f3); border-color: var(--brand-primary, #e7193f); box-shadow: 0 0 0 2px rgba(231, 25, 63, .08); }
.after-sale-type.selected > svg { color: var(--brand-primary, #e7193f); }
.type-check { position: absolute; top: 9px; right: 9px; color: var(--brand-primary, #e7193f) !important; }
.quantity-stepper { display: inline-flex; align-items: center; flex: 0 0 auto; overflow: hidden; border: 1px solid #dfe5e8; border-radius: 8px; background: #fff; }
.quantity-stepper button, .quantity-stepper output { width: 28px; height: 28px; display: grid; place-items: center; border: 0; background: #fff; color: var(--ink); font-size: 16px; }
.quantity-stepper button { color: var(--brand-primary, #e7193f); }
.quantity-stepper button:disabled { color: #c8d0d6; cursor: not-allowed; }
.quantity-stepper output { border-left: 1px solid #edf0f2; border-right: 1px solid #edf0f2; font-size: 13px; }
.reason-select { display: flex; align-items: center; justify-content: space-between; width: 100%; min-height: 48px; padding: 0 13px; color: #a0a9b2; text-align: left; background: #fff; border: 1px solid #dfe5e8; border-radius: 10px; }
.reason-select.selected { color: var(--ink); border-color: var(--brand-primary, #e7193f); }
.reason-select.invalid { color: #b42318; border-color: #ef4444; background: #fff8f8; box-shadow: 0 0 0 2px rgba(239, 68, 68, .08); }
.reason-select svg { flex: 0 0 auto; color: #a4adb5; }
.reason-detail { width: 100%; min-height: 92px; margin-top: 10px; border-color: #e3e8eb; font-size: 13px; }
.reason-counter { margin-top: 5px; color: #a2abb3; text-align: right; font-size: 11px; }
.after-sale-field-error { margin: 8px 0 0; color: #d92d20; font-size: 12px; font-weight: 700; line-height: 1.5; }
.after-sale-submit-error { margin: 12px 0 0; padding: 10px 12px; color: #b42318; background: #fff1f0; border: 1px solid #fecdca; border-radius: 9px; font-size: 12px; font-weight: 700; line-height: 1.5; }
.refund-estimate { margin-top: 4px; padding: 15px; background: #fff7f8; border: 1px solid #f4dbe0; border-radius: 12px; }
.estimate-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.estimate-head span { color: #7b858f; font-size: 13px; }
.estimate-head strong { color: var(--brand-primary, #e7193f); font-size: 22px; }
.after-sale-submit { width: 100%; margin-top: 14px; min-height: 46px; border-radius: 11px; font-weight: 800; }
.reason-sheet-backdrop { position: fixed; inset: 0; z-index: 60; display: flex; align-items: flex-end; background: rgba(15, 23, 42, .48); }
.reason-sheet { width: min(100%, 540px); max-height: 78vh; margin: 0 auto; overflow: auto; background: #fff; border-radius: 18px 18px 0 0; box-shadow: 0 -14px 40px rgba(15, 23, 42, .18); }
.reason-sheet-head { display: flex; align-items: center; justify-content: space-between; padding: 18px 18px 14px; border-bottom: 1px solid #edf0f2; }
.reason-sheet-head h3 { margin: 0; font-size: 17px; }
.reason-sheet-head button { width: 30px; height: 30px; padding: 0; color: #9aa3ad; background: #f4f6f7; border: 0; border-radius: 50%; font-size: 23px; line-height: 1; }
.reason-option { display: flex; align-items: center; justify-content: space-between; width: 100%; min-height: 52px; padding: 0 18px; color: var(--ink); text-align: left; background: #fff; border: 0; border-bottom: 1px solid #f1f3f4; font-size: 14px; }
.reason-option.selected { color: var(--brand-primary, #e7193f); font-weight: 800; }
.reason-option svg { color: var(--brand-primary, #e7193f); }

@media (max-width: 600px) {
  .return-shipment-form { grid-template-columns: 1fr; }
  .after-sale-type { min-height: 68px; }
  .after-sale-progress { font-size: 10px; }
  .product-detail-head { align-items: flex-start; }
  .product-detail-head span { max-width: 130px; text-align: right; line-height: 1.4; }
  .order-line { grid-template-columns: 64px minmax(0, 1fr) auto; gap: 10px; }
  .order-line-amount { font-size: 16px; }
  .order-line-trailing { gap: 8px; }
  .quantity-stepper button, .quantity-stepper output { width: 25px; height: 26px; }
}

.balance-pay-box,
.channel-tip {
  margin-top: 14px;
  padding: 12px;
  border-radius: 12px;
  background: #f7faf8;
  border: 1px solid rgba(15, 118, 110, 0.14);
}

.logistics-overview-panel { grid-column: 1 / -1; padding: 0; overflow: hidden; }
.logistics-overview-head { display: flex; align-items: center; gap: 12px; padding: 16px 18px; background: linear-gradient(135deg, var(--brand-primary-soft, #fff1f3), #fff); border-bottom: 1px solid #f0e3e6; }
.logistics-overview-icon { width: 42px; height: 42px; display: grid; place-items: center; flex: 0 0 42px; color: #fff; background: var(--brand-primary, #e7193f); border-radius: 50%; }
.logistics-overview-status { display: grid; gap: 3px; min-width: 0; }
.logistics-overview-status strong { color: var(--brand-primary, #e7193f); font-size: 17px; }
.logistics-overview-status span { overflow: hidden; color: #7a838d; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.logistics-overview-link { display: inline-flex; align-items: center; gap: 2px; margin-left: auto; color: var(--brand-primary, #e7193f); font-size: 12px; font-weight: 800; text-decoration: none; white-space: nowrap; }
.logistics-package-row { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 10px; margin: 0 18px; padding: 13px 0; border-bottom: 1px solid #edf0f2; }
.logistics-package-row + .logistics-package-row { padding-top: 0; }
.logistics-package-row .courier-icon { width: 36px; height: 36px; display: grid; place-items: center; color: #fff; background: #2f3540; border-radius: 10px; font-size: 13px; font-weight: 800; }
.logistics-package-row strong { display: block; color: var(--ink); font-size: 13px; }
.logistics-package-row p { overflow: hidden; margin: 4px 0 0; color: #8b949e; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.delivery-address-row { display: flex; align-items: flex-start; gap: 10px; padding: 14px 18px 16px; }
.delivery-address-row > svg { flex: 0 0 auto; margin-top: 2px; color: #5e6873; }
.delivery-address-row div { display: grid; gap: 5px; min-width: 0; }
.delivery-address-row strong { color: var(--ink); font-size: 13px; }
.delivery-address-row span { color: #7a838d; font-size: 12px; line-height: 1.55; }
.copy-btn { padding: 4px 9px; color: var(--brand-primary, #e7193f); background: #fff; border: 1px solid var(--brand-primary-soft, #f8ccd5); border-radius: 999px; font-size: 11px; cursor: pointer; }

.order-info-card { margin-top: 14px; border-top: 1px solid #edf0f2; border-bottom: 1px solid #edf0f2; }
.order-info-toggle { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 9px; width: 100%; min-height: 52px; padding: 0; color: var(--ink); background: transparent; border: 0; text-align: left; }
.order-info-toggle > span:first-child { font-size: 15px; font-weight: 900; white-space: nowrap; }
.order-info-toggle small { color: #9aa3ad; font-size: 12px; font-weight: 500; }
.order-info-preview { overflow: hidden; color: #8a939e; font-size: 11px; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.order-info-toggle svg { color: #8a939e; transition: transform .2s ease; }
.order-info-toggle svg.expanded { transform: rotate(180deg); }
.order-info-details { padding: 4px 0 12px; border-top: 1px solid #f1f3f4; }
.order-info-row { display: grid; grid-template-columns: 70px minmax(0, 1fr) auto; align-items: start; gap: 8px; padding: 7px 0; color: #8a939e; font-size: 12px; }
.order-info-row strong { overflow-wrap: anywhere; color: #59636e; font-weight: 600; text-align: right; }
.order-info-row button { padding: 0; color: var(--brand-primary, #e7193f); background: transparent; border: 0; font-size: 12px; }

.balance-pay-box label { display: block; margin-bottom: 8px; font-weight: 700; }
.balance-pay-box .line-sub, .channel-tip { line-height: 1.6; }
</style>
