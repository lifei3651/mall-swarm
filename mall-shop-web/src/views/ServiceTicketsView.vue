<template>
  <main class="support-page" :aria-busy="loading">
    <header class="page-head">
      <button type="button" aria-label="返回" @click="router.back()"><ChevronLeft :size="23" /></button>
      <div><span>服务中心</span><h1>客服工单</h1></div>
      <button type="button" class="create-button" @click="openCreate">提交问题</button>
    </header>

    <section class="contact-card">
      <div><strong>需要帮助？</strong><p>咨询、投诉、售后争议和账号问题都可以在这里提交，并查看处理进度。</p></div>
      <div class="contact-lines">
        <a v-if="legal.servicePhone" :href="`tel:${legal.servicePhone}`"><Phone :size="16" />{{ legal.servicePhone }}</a>
        <a v-if="legal.serviceEmail" :href="`mailto:${legal.serviceEmail}`"><Mail :size="16" />{{ legal.serviceEmail }}</a>
        <span><Clock3 :size="16" />{{ legal.serviceHours || '客服将按工单顺序处理' }}</span>
      </div>
    </section>

    <section v-if="creating" class="create-card">
      <div class="section-head"><div><span>新工单</span><h2>告诉我们遇到的问题</h2></div><button type="button" @click="creating=false">取消</button></div>
      <label>问题类型<select v-model="form.type"><option v-for="item in types" :key="item.key" :value="item.key">{{ item.label }}</option></select></label>
      <label>问题标题<input v-model.trim="form.subject" maxlength="100" placeholder="例如：订单售后处理进度咨询" /></label>
      <label v-if="form.type!=='ACCOUNT'">关联订单（选填）
        <select v-model="form.orderId"><option value="">不关联订单</option><option v-for="item in orders" :key="item.order.id" :value="String(item.order.id)">{{ item.order.orderNo }} · {{ orderName(item) }}</option></select>
      </label>
      <label v-if="selectedOrder?.afterSales?.length">关联售后（售后争议必选）
        <select v-model="form.afterSaleId"><option value="">不关联售后</option><option v-for="sale in selectedOrder.afterSales" :key="sale.id" :value="String(sale.id)">{{ sale.afterSaleNo }} · {{ afterSaleStatus(sale.status, sale.applyType) }}</option></select>
      </label>
      <label>问题说明<textarea v-model.trim="form.content" maxlength="1000" rows="6" placeholder="请说明发生了什么、希望如何协助处理。涉及售后图片请先在订单售后中提交凭证。"></textarea><small>{{ form.content.length }}/1000</small></label>
      <p class="safe-hint">请勿填写登录密码、支付密码、短信验证码或银行卡号。</p>
      <p v-if="formError" class="error" role="alert">{{ formError }}</p>
      <button type="button" class="submit-button" :disabled="submitting" @click="submit">{{ submitting ? '正在提交…' : '提交客服工单' }}</button>
    </section>

    <nav class="status-tabs" aria-label="工单状态">
      <button v-for="item in filters" :key="item.key" :class="{active:status===item.key}" @click="changeStatus(item.key)">{{ item.label }}</button>
    </nav>
    <section class="ticket-list">
      <RouterLink v-for="ticket in tickets" :key="ticket.id" :to="`/support/${ticket.id}`" class="ticket-card">
        <div><span>{{ typeName(ticket.type) }}</span><em :class="ticket.status.toLowerCase()">{{ statusName(ticket.status) }}</em></div>
        <h2>{{ ticket.subject }}</h2>
        <p>{{ ticket.nextActionHint }}</p>
        <footer><span>{{ ticket.ticketNo }}</span><time>{{ time(ticket.lastReplyTime) }}</time></footer>
      </RouterLink>
      <p v-if="!loading&&!tickets.length" class="empty">当前没有{{ status ? '该状态的' : '' }}客服工单</p>
      <button v-if="pageNum<totalPage" class="more" @click="load(false)">加载更多</button>
    </section>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, Clock3, Mail, Phone } from 'lucide-vue-next'
import { createServiceTicket, getLegalConfig, listMyOrders, listServiceTickets } from '@/api/shop'
import { createIdempotencyKey } from '@/utils/idempotency'

const route = useRoute()
const router = useRouter()
const types = [{ key: 'CONSULTATION', label: '咨询' }, { key: 'COMPLAINT', label: '投诉' }, { key: 'AFTER_SALE_DISPUTE', label: '售后争议' }, { key: 'ACCOUNT', label: '账号问题' }, { key: 'OTHER', label: '其他' }]
const filters = [{ key: '', label: '全部' }, { key: 'OPEN', label: '待处理' }, { key: 'PROCESSING', label: '处理中' }, { key: 'WAITING_MEMBER', label: '待我补充' }, { key: 'RESOLVED', label: '已答复' }, { key: 'CLOSED', label: '已关闭' }]
const status = ref('')
const tickets = ref([])
const orders = ref([])
const legal = ref({})
const loading = ref(false)
const submitting = ref(false)
const creating = ref(false)
const error = ref('')
const formError = ref('')
const pageNum = ref(0)
const totalPage = ref(1)
const form = reactive({ type: 'CONSULTATION', subject: '', content: '', orderId: '', afterSaleId: '' })
const selectedOrder = computed(() => orders.value.find((item) => String(item.order?.id) === String(form.orderId)))

watch(() => form.orderId, () => {
  if (!selectedOrder.value?.afterSales?.some((sale) => String(sale.id) === String(form.afterSaleId))) form.afterSaleId = ''
})

const typeName = (value) => types.find((item) => item.key === value)?.label || '其他'
const statusName = (value) => ({ OPEN: '待处理', PROCESSING: '处理中', WAITING_MEMBER: '待我补充', RESOLVED: '已答复', CLOSED: '已关闭' }[value] || value)
const afterSaleStatus = (value, applyType) => {
  if (Number(applyType) === 3 && Number(value) === 1) return '换货完成'
  return ({ 0: '待审核', 1: '退款完成', 2: '已拒绝', 3: '已取消', 4: '待寄回', 5: '待商家收货', 6: '退款处理中', 7: '待商家换货发出', 8: '换货已发出' }[value] || '处理中')
}
const orderName = (item) => item.items?.[0]?.productName || '商城订单'
const time = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : ''

const load = async (reset = true) => {
  loading.value = true
  error.value = ''
  try {
    const next = reset ? 1 : pageNum.value + 1
    const res = (await listServiceTickets({ status: status.value || undefined, pageNum: next, pageSize: 20 })).data || {}
    tickets.value = reset ? (res.list || []) : [...tickets.value, ...(res.list || [])]
    pageNum.value = Number(res.pageNum || next)
    totalPage.value = Number(res.totalPage || 1)
  } catch (e) {
    error.value = e.message || '客服工单加载失败'
  } finally {
    loading.value = false
  }
}

const changeStatus = (value) => {
  status.value = value
  load(true)
}

const loadContext = async () => {
  const [legalResult, orderResult] = await Promise.allSettled([getLegalConfig(), listMyOrders({ pageNum: 1, pageSize: 50 })])
  if (legalResult.status === 'fulfilled') legal.value = legalResult.value.data || {}
  if (orderResult.status === 'fulfilled') orders.value = orderResult.value.data?.list || []
}

const openCreate = () => {
  creating.value = true
  formError.value = ''
  if (route.query.orderId) form.orderId = String(route.query.orderId)
  if (route.query.afterSaleId) form.afterSaleId = String(route.query.afterSaleId)
  if (types.some((item) => item.key === route.query.type)) form.type = route.query.type
  if (route.query.subject) form.subject = String(route.query.subject).slice(0, 100)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const submit = async () => {
  formError.value = ''
  if (!form.subject) { formError.value = '请填写问题标题'; return }
  if (!form.content) { formError.value = '请填写问题说明'; return }
  if (form.type === 'AFTER_SALE_DISPUTE' && !form.afterSaleId) { formError.value = '售后争议必须选择一条售后记录'; return }
  submitting.value = true
  try {
    const payload = { type: form.type, subject: form.subject, content: form.content, orderId: form.orderId ? Number(form.orderId) : null, afterSaleId: form.afterSaleId ? Number(form.afterSaleId) : null }
    const detail = (await createServiceTicket(payload, createIdempotencyKey('service-ticket-create'))).data
    await router.push(`/support/${detail.ticket.id}`)
  } catch (e) {
    formError.value = e.message || '工单提交失败'
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([load(true), loadContext()])
  if (route.query.create || route.query.orderId || route.query.afterSaleId) openCreate()
})
</script>

<style scoped>
.support-page{width:min(820px,calc(100% - 24px));margin:0 auto;padding:18px 0 100px;color:#1d2939}.page-head{display:grid;grid-template-columns:44px 1fr auto;align-items:center;gap:10px}.page-head>button:first-child{width:40px;height:40px;border:0;background:#fff;border-radius:12px}.page-head span,.section-head span{color:var(--brand-primary,#e7193f);font-size:12px}.page-head h1,.section-head h2{margin:2px 0 0}.create-button,.submit-button{border:0;color:#fff;background:var(--brand-primary,#e7193f);border-radius:12px;padding:11px 15px}.contact-card,.create-card,.ticket-card{background:#fff;border:1px solid #edf0f3;border-radius:18px}.contact-card{display:flex;justify-content:space-between;gap:20px;margin-top:16px;padding:19px}.contact-card p{margin:7px 0 0;color:#667085;font-size:13px;line-height:1.7}.contact-lines{display:flex;flex-direction:column;gap:7px;align-items:flex-end}.contact-lines a,.contact-lines span{display:flex;align-items:center;gap:6px;color:#475467;font-size:12px}.create-card{display:grid;gap:14px;margin-top:14px;padding:20px}.section-head{display:flex;justify-content:space-between}.section-head button{border:0;background:transparent;color:#667085}.create-card label{display:grid;gap:7px;color:#344054;font-size:13px}.create-card input,.create-card select,.create-card textarea{width:100%;box-sizing:border-box;border:1px solid #dfe3e8;border-radius:11px;padding:11px 12px;background:#fff;font:inherit}.create-card textarea{resize:vertical;line-height:1.7}.create-card label small{justify-self:end;color:#98a2b3}.safe-hint{margin:0;color:#667085;font-size:12px}.status-tabs{display:flex;gap:7px;overflow:auto;padding:15px 0}.status-tabs button{flex:0 0 auto;border:0;padding:9px 13px;background:#fff;border-radius:999px;color:#667085}.status-tabs button.active{color:#fff;background:var(--brand-primary,#e7193f)}.ticket-list{display:grid;gap:10px}.ticket-card{display:block;padding:17px;color:inherit}.ticket-card>div,.ticket-card footer{display:flex;justify-content:space-between;gap:12px}.ticket-card div span{color:var(--brand-primary,#e7193f);font-size:12px}.ticket-card em{padding:3px 8px;border-radius:999px;background:#f2f4f7;color:#475467;font-size:11px;font-style:normal}.ticket-card em.open{color:#b54708;background:#fffaeb}.ticket-card em.processing{color:#175cd3;background:#eff8ff}.ticket-card em.waiting_member{color:#6941c6;background:#f4f3ff}.ticket-card h2{margin:11px 0 7px;font-size:16px}.ticket-card p{margin:0;color:#667085;font-size:13px}.ticket-card footer{margin-top:14px;color:#98a2b3;font-size:11px}.empty,.error{padding:24px;text-align:center;color:#667085}.error{color:#b42318}.more{justify-self:center;border:0;padding:10px 18px;border-radius:11px;background:#fff}.submit-button:disabled{opacity:.55}@media(max-width:620px){.contact-card{flex-direction:column}.contact-lines{align-items:flex-start}.page-head{grid-template-columns:40px 1fr}.create-button{grid-column:1/-1}.support-page{width:calc(100% - 20px)}}
</style>
