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
          <h3>售后记录</h3>
          <div v-for="sale in afterSales" :key="sale.id" class="after-sale-record">
            <div class="summary-row">
              <span>{{ sale.afterSaleNo }} · {{ afterSaleStatus(sale.status) }}</span>
              <strong>¥{{ money(sale.refundAmount) }}</strong>
            </div>
            <p class="line-sub">商品 {{ sale.refundQuantity || 0 }} 件 / 商品款 ¥{{ money(sale.productRefundAmount) }} / 运费 ¥{{ money(sale.freightRefundAmount) }}</p>
            <p v-for="line in sale.items || []" :key="line.id" class="line-sub">{{ line.productName }} {{ formatProductSpec(line) }} × {{ line.refundQuantity }}</p>
          </div>
        </div>

        <div v-if="canApplyAfterSale" class="after-sale-box">
          <h3>申请售后</h3>
          <div class="form-grid">
            <div class="form-item">
              <label>售后类型</label>
              <select v-model.number="afterSaleForm.applyType" class="field">
                <option :value="1">仅退款</option>
                <option :value="2">退货退款</option>
              </select>
            </div>
            <div class="form-item full refund-lines">
              <label>选择实际退款商品和数量</label>
              <div v-for="item in detail.items" :key="item.id" class="refund-line">
                <div>
                  <strong>{{ item.productName }}</strong>
                  <p class="line-sub">{{ formatProductSpec(item) }} · 剩余可退 {{ remainingQuantity(item) }} 件</p>
                </div>
                <input
                  v-model.number="refundQuantities[item.id]"
                  class="field quantity-field"
                  type="number"
                  min="0"
                  :max="remainingQuantity(item)"
                  step="1"
                  :disabled="remainingQuantity(item) <= 0"
                />
              </div>
            </div>
            <div class="form-item full">
              <label>原因</label>
              <textarea v-model="afterSaleForm.reason" class="textarea"></textarea>
            </div>
          </div>
          <div class="refund-estimate">
            <div class="summary-row"><span>实际退款件数</span><strong>{{ selectedRefundQuantity }} 件</strong></div>
            <div class="summary-row"><span>预计商品退款</span><strong>¥{{ money(estimatedProductRefund) }}</strong></div>
            <div class="summary-row"><span>预计运费退款</span><strong>¥{{ money(estimatedFreightRefund) }}</strong></div>
            <p class="line-sub">{{ freightPolicyText }}</p>
          </div>
          <button class="btn secondary" style="margin-top: 14px" :disabled="submittingAfterSale" @click="submitAfterSale">
            {{ submittingAfterSale ? '提交中' : '提交售后' }}
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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { UserRound } from 'lucide-vue-next'
import { applyAfterSale, cancelOrder, confirmReceive, getOrder, payOrderWithBalance } from '@/api/shop'
import { dateTime, money, statusName } from '@/utils/format'
import { formatProductSpec } from '@/utils/productSpec'

const route = useRoute()
const detail = ref({})
const loading = ref(false)
const acting = ref(false)
const submittingAfterSale = ref(false)
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
const canApplyAfterSale = computed(() => {
  if (!order.value) return false
  if ([0, 4].includes(order.value.status)) return false
  return !afterSales.value.some((item) => item.status === 0)
})
const afterSaleForm = ref({
  applyType: 1,
  reason: '',
})
const refundQuantities = ref({})

const usedQuantity = (orderItemId) => afterSales.value
  .filter((sale) => [0, 1].includes(sale.status))
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

const afterSaleStatus = (status) => ({ 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已取消' }[status] || '处理中')
const payTypeName = (value) => ({ WECHAT: '微信支付', ALIPAY: '支付宝', BALANCE: '余额' }[value] || value || '未选择')
const copyText = async (text) => { try { await navigator.clipboard.writeText(text) } catch {} }

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
  if (!afterSaleForm.value.reason) {
    error.value = '请填写售后原因'
    return
  }
  submittingAfterSale.value = true
  try {
    await applyAfterSale({
      ...afterSaleForm.value,
      orderId: order.value.id,
      items: selectedRefundItems.value,
    })
    afterSaleForm.value.reason = ''
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
.after-sale-record,
.refund-estimate {
  padding: 10px 0;
  border-bottom: 1px solid rgba(15, 118, 110, 0.12);
}

.refund-lines {
  display: grid;
  gap: 10px;
}

.refund-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px;
  border: 1px solid rgba(15, 118, 110, 0.14);
  border-radius: 12px;
}

.quantity-field {
  width: 88px;
  flex: 0 0 88px;
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
