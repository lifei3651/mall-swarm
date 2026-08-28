<template>
  <main class="detail-page" :aria-busy="loading">
    <header><RouterLink to="/support" aria-label="返回客服工单"><ChevronLeft :size="23" /></RouterLink><h1>工单详情</h1><span></span></header>
    <section v-if="ticket.id" class="summary-card">
      <div><span>{{ typeName(ticket.type) }}</span><em :class="ticket.status.toLowerCase()">{{ statusName(ticket.status) }}</em></div>
      <h2>{{ ticket.subject }}</h2>
      <p>{{ ticket.nextActionHint }}</p>
      <dl>
        <div><dt>工单编号</dt><dd>{{ ticket.ticketNo }}</dd></div>
        <div v-if="ticket.orderNo"><dt>关联订单</dt><dd><RouterLink v-if="hasOrderRoute" :to="`/orders/${ticket.orderId}`">{{ ticket.orderNo }}</RouterLink><span v-else>{{ ticket.orderNo }}</span></dd></div>
        <div v-if="ticket.afterSaleNo"><dt>关联售后</dt><dd>{{ ticket.afterSaleNo }}</dd></div>
        <div><dt>首次响应目标</dt><dd :class="{overdue:ticket.firstResponseOverdue}">{{ time(ticket.firstResponseDeadline) }}{{ ticket.firstResponseOverdue ? '（已超时，优先处理）' : '' }}</dd></div>
      </dl>
    </section>
    <section v-if="ticket.id" class="conversation">
      <article v-for="replyItem in replies" :key="replyItem.id" :class="['reply', replyItem.senderType.toLowerCase()]">
        <div><strong>{{ replyItem.senderLabel }}</strong><time>{{ time(replyItem.createTime) }}</time></div><p>{{ replyItem.content }}</p>
      </article>
    </section>
    <section v-if="ticket.id && ticket.status !== 'CLOSED'" class="reply-box">
      <textarea v-model.trim="content" maxlength="1000" rows="5" placeholder="补充问题或回复客服"></textarea>
      <div><span>{{ content.length }}/1000</span><button type="button" :disabled="submitting || !content" @click="reply">{{ submitting ? '发送中…' : '发送回复' }}</button></div>
      <button type="button" class="close-button" @click="closeConfirmVisible=true">问题已解决，关闭工单</button>
    </section>
    <p v-if="ticket.status === 'CLOSED'" class="closed-hint">该工单已关闭；如有新的问题，请重新提交工单。</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <ConfirmDialog :visible="closeConfirmVisible" title="确认关闭工单？" message="关闭后不能继续回复；有新问题时可以重新提交工单。" confirm-text="确认关闭" :busy="closing" @confirm="closeTicket" @cancel="closeConfirmVisible=false" />
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft } from 'lucide-vue-next'
import { closeServiceTicket, getServiceTicket, replyServiceTicket } from '@/api/shop'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { createIdempotencyKey } from '@/utils/idempotency'

const route = useRoute()
const router = useRouter()
const ticket = ref({})
const replies = ref([])
const content = ref('')
const loading = ref(false)
const submitting = ref(false)
const closing = ref(false)
const closeConfirmVisible = ref(false)
const error = ref('')
let poll

const hasOrderRoute = computed(() => router.hasRoute('OrderDetail'))
const typeName = (value) => ({ CONSULTATION: '咨询', COMPLAINT: '投诉', AFTER_SALE_DISPUTE: '售后争议', ACCOUNT: '账号问题', OTHER: '其他' }[value] || '其他')
const statusName = (value) => ({ OPEN: '待处理', PROCESSING: '处理中', WAITING_MEMBER: '待我补充', RESOLVED: '已答复', CLOSED: '已关闭' }[value] || value)
const time = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-'
const applyDetail = (detail) => { ticket.value = detail?.ticket || {}; replies.value = detail?.replies || [] }

const load = async (silent = false) => {
  if (!silent) loading.value = true
  error.value = ''
  try { applyDetail((await getServiceTicket(route.params.id)).data) }
  catch (e) { error.value = e.message || '工单加载失败' }
  finally { loading.value = false }
}

const reply = async () => {
  if (!content.value) return
  submitting.value = true
  error.value = ''
  try {
    applyDetail((await replyServiceTicket(route.params.id, { content: content.value }, createIdempotencyKey('service-ticket-reply'))).data)
    content.value = ''
  } catch (e) { error.value = e.message || '回复发送失败' }
  finally { submitting.value = false }
}

const closeTicket = async () => {
  closing.value = true
  error.value = ''
  try { applyDetail((await closeServiceTicket(route.params.id)).data); closeConfirmVisible.value = false }
  catch (e) { error.value = e.message || '工单关闭失败' }
  finally { closing.value = false }
}

onMounted(() => { load(); poll = setInterval(() => load(true), 30000) })
onBeforeUnmount(() => clearInterval(poll))
</script>

<style scoped>
.detail-page{width:min(760px,calc(100% - 24px));margin:0 auto;padding:16px 0 100px;color:#1d2939}.detail-page>header{display:grid;grid-template-columns:42px 1fr 42px;align-items:center}.detail-page>header a{display:grid;place-items:center;width:40px;height:40px;color:#1d2939;background:#fff;border-radius:12px}.detail-page>header h1{margin:0;text-align:center;font-size:19px}.summary-card,.conversation,.reply-box,.closed-hint{margin-top:14px;background:#fff;border:1px solid #edf0f3;border-radius:18px}.summary-card{padding:20px}.summary-card>div{display:flex;justify-content:space-between}.summary-card>div span{color:var(--brand-primary,#e7193f);font-size:12px}.summary-card em{padding:4px 9px;background:#f2f4f7;border-radius:999px;color:#475467;font-size:11px;font-style:normal}.summary-card h2{margin:13px 0 7px}.summary-card>p{color:#667085}.summary-card dl{margin:18px 0 0;border-top:1px solid #edf0f3}.summary-card dl div{display:grid;grid-template-columns:100px 1fr;gap:12px;padding:11px 0;border-bottom:1px solid #f2f4f7}.summary-card dt{color:#98a2b3}.summary-card dd{margin:0;word-break:break-all}.summary-card a{color:var(--brand-primary,#e7193f)}.overdue{color:#b42318}.conversation{display:grid;gap:13px;padding:17px}.reply{max-width:86%;padding:13px 14px;background:#f4f6f8;border-radius:14px}.reply.member{justify-self:end;background:color-mix(in srgb,var(--brand-primary,#e7193f) 10%,white)}.reply.admin{justify-self:start}.reply.system{justify-self:center;max-width:100%;color:#667085;background:#f9fafb}.reply div{display:flex;justify-content:space-between;gap:25px}.reply time{color:#98a2b3;font-size:11px}.reply p{margin:8px 0 0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.65}.reply-box{display:grid;gap:10px;padding:17px}.reply-box textarea{box-sizing:border-box;width:100%;padding:12px;border:1px solid #dfe3e8;border-radius:12px;resize:vertical;font:inherit}.reply-box>div{display:flex;justify-content:space-between;align-items:center}.reply-box span{color:#98a2b3;font-size:11px}.reply-box button{border:0;padding:10px 15px;color:#fff;background:var(--brand-primary,#e7193f);border-radius:11px}.reply-box button:disabled{opacity:.5}.reply-box .close-button{justify-self:start;padding:6px 0;color:#667085;background:transparent}.closed-hint,.error{padding:18px;text-align:center;color:#667085}.error{color:#b42318}@media(max-width:620px){.detail-page{width:calc(100% - 20px)}.reply{max-width:92%}}
</style>
