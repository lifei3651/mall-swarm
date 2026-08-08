<template>
  <div class="page">
    <div class="section-head">
      <div>
        <h2>订单详情</h2>
        <p>{{ order?.orderNo }}</p>
      </div>
      <RouterLink class="btn secondary" to="/orders">
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
    <div v-else class="checkout-layout">
      <section class="panel">
        <h3>商品明细</h3>
        <div v-for="item in detail.items" :key="item.id" class="order-line">
          <img :src="item.productCover" :alt="item.productName" />
          <div>
            <p class="line-title">{{ item.productName }}</p>
            <p class="line-sub">
              {{ formatProductSpec(item) }} · x {{ item.quantity }}
              <span v-if="showPv"> · PV {{ money(item.totalPv) }}</span>
            </p>
          </div>
          <strong>¥{{ money(item.totalAmount) }}</strong>
        </div>

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
              <small v-else>物流：{{ sale.returnDeliveryCompany }} {{ sale.returnDeliveryNo }}，等待商家确认收货</small>
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

        <div v-if="canApplyAfterSale" class="after-sale-box">
          <div class="after-sale-section-head apply-head">
            <div>
              <span class="section-eyebrow">售后服务</span>
              <h3>申请退款 / 售后</h3>
              <p>选择商品和原因，平台会尽快为你处理</p>
            </div>
          </div>
          <div class="after-sale-tip"><ShieldCheck :size="18" /><span>退款金额以实际支付金额和审核结果为准，处理进度可在订单详情查看。</span></div>

          <div class="after-sale-block">
            <div class="block-label">售后类型</div>
            <div class="after-sale-type-grid">
              <button type="button" class="after-sale-type" :class="{ selected: afterSaleForm.applyType === 1 }" @click="afterSaleForm.applyType = 1">
                <RotateCcw :size="21" />
                <span><strong>仅退款</strong><small>未收到货或与商家协商退款</small></span>
                <CircleCheck v-if="afterSaleForm.applyType === 1" :size="18" class="type-check" />
              </button>
              <button type="button" class="after-sale-type" :class="{ selected: afterSaleForm.applyType === 2 }" @click="afterSaleForm.applyType = 2">
                <PackageCheck :size="21" />
                <span><strong>退货退款</strong><small>需要寄回已收到的商品</small></span>
                <CircleCheck v-if="afterSaleForm.applyType === 2" :size="18" class="type-check" />
              </button>
            </div>
          </div>

          <div class="after-sale-block">
            <div class="block-label">选择商品和数量</div>
            <div class="refund-lines">
              <div v-for="item in detail.items" :key="item.id" class="refund-line">
                <img :src="item.productCover" :alt="item.productName" />
                <div class="refund-line-info">
                  <strong>{{ item.productName }}</strong>
                  <p class="line-sub">{{ formatProductSpec(item) }} · 可退 {{ remainingQuantity(item) }} 件</p>
                </div>
                <div class="quantity-stepper" :aria-label="`${item.productName}退款数量`">
                  <button type="button" :disabled="refundQuantities[item.id] <= 0" @click="setRefundQuantity(item, -1)">−</button>
                  <output>{{ refundQuantities[item.id] || 0 }}</output>
                  <button type="button" :disabled="remainingQuantity(item) <= (refundQuantities[item.id] || 0)" @click="setRefundQuantity(item, 1)">＋</button>
                </div>
              </div>
            </div>
          </div>

          <div class="after-sale-block">
            <div class="block-label">申请原因</div>
            <button type="button" class="reason-select" :class="{ selected: selectedReason }" @click="reasonSheetVisible = true">
              <span>{{ selectedReason || '请选择申请原因' }}</span>
              <ChevronRight :size="18" />
            </button>
            <textarea v-model="afterSaleForm.reasonDetail" class="textarea reason-detail" maxlength="170" placeholder="补充说明（选填），帮助平台更快处理"></textarea>
            <div class="reason-counter">{{ afterSaleForm.reasonDetail.length }}/170</div>
          </div>

          <div class="refund-estimate">
            <div class="estimate-head"><span>预计退款</span><strong>¥{{ money(estimatedProductRefund + estimatedFreightRefund) }}</strong></div>
            <div class="estimate-meta">{{ selectedRefundQuantity }} 件商品 · {{ freightPolicyText }}</div>
          </div>
          <button class="btn primary after-sale-submit" :disabled="submittingAfterSale" @click="submitAfterSale">
            {{ submittingAfterSale ? '提交中…' : '提交申请' }}
          </button>
        </div>
      </section>
      <aside class="panel">
        <h3>金额</h3>
        <div class="summary-row">
          <span>订单状态</span>
          <strong>{{ statusName(order.status) }}</strong>
        </div>
        <div class="summary-row">
          <span>商品金额</span>
          <strong>¥{{ money(order.totalAmount) }}</strong>
        </div>
        <div class="summary-row">
          <span>运费</span>
          <strong>¥{{ money(order.freightAmount) }}</strong>
        </div>
        <div class="summary-row">
          <span>实付金额</span>
          <strong>¥{{ money(order.payAmount) }}</strong>
        </div>
        <div class="summary-row">
          <span>支付方式</span>
          <strong>{{ payTypeName(order.payType) }}</strong>
        </div>
        <div class="summary-row">
          <span>收货人</span>
          <strong>{{ order.receiverName }}</strong>
        </div>
        <div v-if="shipments.length" class="logistics-section">
          <h3>物流信息</h3>
          <div v-for="(shipment, index) in shipments" :key="`${shipment.deliveryCompany}-${shipment.deliveryNo}-${index}`" class="shipment-timeline">
            <div class="timeline-header">
              <div class="courier-info">
                <span class="courier-icon">{{ courierInitial(shipment.deliveryCompany) }}</span>
                <div>
                  <strong>{{ shipment.deliveryCompany || '快递公司' }}</strong>
                  <p>运单号 {{ shipment.deliveryNo }}
                    <button class="copy-btn" @click="copyText(shipment.deliveryNo)">复制</button>
                  </p>
                </div>
              </div>
              <a v-if="trackingUrl(shipment)" :href="trackingUrl(shipment)" target="_blank" rel="noopener" class="tracking-link">查看物流</a>
            </div>

            <div class="timeline-steps">
              <div class="timeline-step completed">
                <div class="step-dot"></div>
                <div class="step-line"></div>
                <div class="step-content">
                  <strong>已发货</strong>
                  <p>{{ shipment.deliveryTime ? dateTime(shipment.deliveryTime) : (order.payTime ? dateTime(order.payTime) : '-') }}</p>
                  <small>包裹 {{ index + 1 }} · {{ shipment.shipmentQuantity || 0 }} 件</small>
                </div>
              </div>
              <div class="timeline-step" :class="{ completed: order.status >= 2 }">
                <div class="step-dot"></div>
                <div class="step-line"></div>
                <div class="step-content">
                  <strong>运输中</strong>
                  <p>{{ shipment.deliveryCompany || '快递公司' }}承运</p>
                  <small v-if="estimatedDelivery(shipment)">预计 {{ estimatedDelivery(shipment) }} 送达</small>
                </div>
              </div>
              <div class="timeline-step" :class="{ completed: order.status >= 3 }">
                <div class="step-dot"></div>
                <div class="step-content">
                  <strong>已签收</strong>
                  <p v-if="order.receiveTime">{{ dateTime(order.receiveTime) }}</p>
                  <p v-else>等待收货</p>
                </div>
              </div>
            </div>
          </div>
        </div>
        <p class="line-sub" style="line-height: 1.7">{{ order.receiverAddress }}</p>
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
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronRight, CircleCheck, PackageCheck, RotateCcw, ShieldCheck, UserRound } from 'lucide-vue-next'
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
const afterSaleReasons = ['不想要了', '与商品描述不符', '质量问题', '收到商品少件 / 漏发', '商品破损或污渍', '商家发错货', '其他原因']
const error = ref('')
const hasToken = ref(Boolean(localStorage.getItem('shop_token')))
const paymentPassword = ref('')
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
  return !afterSales.value.some((item) => [0, 4, 5, 6].includes(item.status))
})
const afterSaleForm = ref({
  applyType: 1,
  reason: '',
  reasonDetail: '',
})
const refundQuantities = ref({})

const usedQuantity = (orderItemId) => afterSales.value
  .filter((sale) => [0, 4, 5, 6].includes(sale.status))
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
const freightPolicyText = computed(() => notShipped.value
  ? (refundAllRemaining.value ? '未发货整单退完：原运费一并退还。' : '未发货部分退款：运费暂不退；退完剩余全部商品时才退原运费。')
  : '订单已经发货：原发货运费不退。')

const setRefundQuantity = (item, delta) => {
  const current = Number(refundQuantities.value[item.id] || 0)
  refundQuantities.value[item.id] = Math.max(0, Math.min(remainingQuantity(item), current + delta))
}

const selectAfterSaleReason = (reason) => {
  selectedReason.value = reason
  afterSaleForm.value.reason = reason
  reasonSheetVisible.value = false
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
  return `https://m.kuaidi100.com/result.jsp?nu=${encodeURIComponent(no)}`
}

const estimatedDelivery = (shipment) => {
  if (!shipment.deliveryTime) return null
  const shipped = new Date(shipment.deliveryTime)
  if (isNaN(shipped.getTime())) return null
  const est = new Date(shipped)
  est.setDate(est.getDate() + 3)
  return `${est.getMonth() + 1}月${est.getDate()}日`
}

const fetchOrder = async () => {
  if (!hasToken.value) return
  loading.value = true
  error.value = ''
  try {
    const res = await getOrder(route.params.id)
    detail.value = res.data || {}
    refundQuantities.value = Object.fromEntries((detail.value.items || []).map((item) => [item.id, 0]))
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
  if (!selectedRefundItems.value.length) {
    error.value = '请选择实际退款商品和数量'
    return
  }
  if (!selectedReason.value) {
    error.value = '请填写售后原因'
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
    await fetchOrder()
  } catch (e) {
    error.value = e.message || '提交售后失败'
  } finally {
    submittingAfterSale.value = false
  }
}

onMounted(fetchOrder)
</script>

<style scoped>
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
.after-sale-item-line { display: flex; justify-content: space-between; gap: 12px; padding-top: 7px; color: #69737e; font-size: 12px; }
.after-sale-item-line strong { color: #3d4650; font-size: 12px; }
.after-sale-record-actions { display: flex; justify-content: flex-end; margin-top: 12px; padding-top: 11px; border-top: 1px solid #f1e3e6; }
.after-sale-cancel { min-height: 32px; padding: 0 13px; border-radius: 8px; font-size: 12px; }
.apply-head { align-items: flex-start; margin-bottom: 12px; }
.apply-head p { margin: 6px 0 0; color: var(--muted); font-size: 12px; }
.after-sale-tip { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 18px; padding: 11px 12px; color: #8b5e12; background: #fff8e8; border: 1px solid #f8dfae; border-radius: 10px; font-size: 12px; line-height: 1.6; }
.after-sale-tip svg { flex: 0 0 auto; color: #e69b20; }
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
.refund-lines { display: grid; gap: 9px; }
.refund-line { display: flex; align-items: center; gap: 10px; padding: 10px; background: #fbfcfd; border: 1px solid #e7ebee; border-radius: 12px; }
.refund-line img { width: 54px; height: 54px; flex: 0 0 54px; object-fit: cover; background: #f2f4f5; border-radius: 9px; }
.refund-line-info { min-width: 0; flex: 1; }
.refund-line-info strong { display: block; overflow: hidden; color: var(--ink); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.refund-line-info .line-sub { margin-top: 5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.quantity-stepper { display: inline-flex; align-items: center; flex: 0 0 auto; overflow: hidden; border: 1px solid #dfe5e8; border-radius: 8px; background: #fff; }
.quantity-stepper button, .quantity-stepper output { width: 28px; height: 28px; display: grid; place-items: center; border: 0; background: #fff; color: var(--ink); font-size: 16px; }
.quantity-stepper button { color: var(--brand-primary, #e7193f); }
.quantity-stepper button:disabled { color: #c8d0d6; cursor: not-allowed; }
.quantity-stepper output { border-left: 1px solid #edf0f2; border-right: 1px solid #edf0f2; font-size: 13px; }
.reason-select { display: flex; align-items: center; justify-content: space-between; width: 100%; min-height: 48px; padding: 0 13px; color: #a0a9b2; text-align: left; background: #fff; border: 1px solid #dfe5e8; border-radius: 10px; }
.reason-select.selected { color: var(--ink); border-color: var(--brand-primary, #e7193f); }
.reason-select svg { flex: 0 0 auto; color: #a4adb5; }
.reason-detail { width: 100%; min-height: 92px; margin-top: 10px; border-color: #e3e8eb; font-size: 13px; }
.reason-counter { margin-top: 5px; color: #a2abb3; text-align: right; font-size: 11px; }
.refund-estimate { margin-top: 4px; padding: 15px; background: #fff7f8; border: 1px solid #f4dbe0; border-radius: 12px; }
.estimate-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.estimate-head span { color: #7b858f; font-size: 13px; }
.estimate-head strong { color: var(--brand-primary, #e7193f); font-size: 22px; }
.estimate-meta { margin-top: 6px; color: #8c969f; font-size: 11px; line-height: 1.6; }
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
  .after-sale-type-grid { grid-template-columns: 1fr; }
  .return-shipment-form { grid-template-columns: 1fr; }
  .after-sale-type { min-height: 68px; }
  .after-sale-progress { font-size: 10px; }
  .refund-line { align-items: flex-start; }
  .quantity-stepper { margin-top: 5px; }
}

.balance-pay-box,
.channel-tip {
  margin-top: 14px;
  padding: 12px;
  border-radius: 12px;
  background: #f7faf8;
  border: 1px solid rgba(15, 118, 110, 0.14);
}

.logistics-section { margin-top: 14px; padding: 16px; background: #f8faf8; border: 1px solid #dcfce7; border-radius: 14px; }
.logistics-section h3 { margin: 0 0 14px; font-size: 14px; color: #15803d; }

.shipment-timeline + .shipment-timeline { margin-top: 18px; padding-top: 18px; border-top: 1px dashed #bbf7d0; }
.timeline-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.courier-info { display: flex; align-items: center; gap: 10px; min-width: 0; }
.courier-icon { width: 42px; height: 42px; display: grid; place-items: center; flex-shrink: 0; color: #fff; background: var(--brand-primary, #0f766e); border-radius: 50%; font-size: 16px; font-weight: 800; }
.courier-info strong { font-size: 14px; display: block; }
.courier-info p { margin: 3px 0 0; color: #6b7280; font-size: 12px; }
.tracking-link { flex-shrink: 0; padding: 6px 12px; color: var(--brand-primary, #0f766e); border: 1px solid var(--brand-primary, #0f766e); border-radius: 14px; font-size: 12px; font-weight: 700; text-decoration: none; }

.timeline-steps { position: relative; }
.timeline-step { display: grid; grid-template-columns: 32px minmax(0, 1fr); gap: 12px; padding-bottom: 16px; position: relative; }
.timeline-step:last-child { padding-bottom: 0; }
.step-dot { width: 16px; height: 16px; justify-self: center; border-radius: 50%; background: #d1d5db; border: 3px solid #e5e7eb; flex-shrink: 0; }
.timeline-step.completed .step-dot { background: var(--brand-primary, #0f766e); border-color: var(--brand-primary-soft, #ccfbf1); }
.step-line { position: absolute; left: 15px; top: 28px; width: 2px; height: calc(100% - 16px); background: #e5e7eb; }
.timeline-step.completed .step-line { background: var(--brand-primary, #0f766e); }
.timeline-step:last-child .step-line { display: none; }
.step-content { padding-top: 1px; }
.step-content strong { font-size: 14px; }
.step-content p { margin: 4px 0 0; color: #6b7280; font-size: 12px; }
.step-content small { display: block; margin-top: 3px; color: #9ca3af; font-size: 11px; }

.copy-btn { padding: 2px 8px; color: var(--brand-primary, #0f766e); background: none; border: 1px solid var(--brand-primary, #0f766e); border-radius: 4px; font-size: 11px; cursor: pointer; }

.balance-pay-box label { display: block; margin-bottom: 8px; font-weight: 700; }
.balance-pay-box .line-sub, .channel-tip { line-height: 1.6; }
</style>
