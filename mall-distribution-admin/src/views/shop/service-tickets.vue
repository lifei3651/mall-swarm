<template>
  <div class="ticket-page">
    <div class="page-heading"><div><h2>客服工单</h2><p>统一处理会员咨询、投诉、售后争议和账号问题；超时只会优先排序，不自动退款或关闭交易。</p></div><el-button @click="load">刷新</el-button></div>
    <el-alert v-if="isMerchantUser" title="商户账号只显示关联本商户订单的工单；通用咨询和账号问题由平台客服处理。" type="info" :closable="false" show-icon />
    <el-form class="filters" :inline="true" @submit.prevent>
      <el-form-item label="搜索"><el-input v-model="query.keyword" clearable placeholder="工单号 / 订单号 / 标题 / 会员" @keyup.enter="search" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部状态"><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="类型"><el-select v-model="query.type" clearable placeholder="全部类型"><el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-table :data="tickets" v-loading="loading" empty-text="暂无客服工单" style="width:100%">
      <el-table-column prop="ticketNo" label="工单编号" min-width="190" />
      <el-table-column label="类型" width="105"><template #default="{ row }">{{ typeName(row.type) }}</template></el-table-column>
      <el-table-column prop="subject" label="问题标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="memberAccount" label="会员账号" min-width="130" />
      <el-table-column prop="orderNo" label="关联订单" min-width="175"><template #default="{ row }">{{ row.orderNo || '未关联' }}</template></el-table-column>
      <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusTone(row.status)">{{ statusName(row.status) }}</el-tag><el-tag v-if="row.firstResponseOverdue" class="overdue-tag" type="danger" effect="plain">首次响应超时</el-tag></template></el-table-column>
      <el-table-column label="下一责任方" min-width="150"><template #default="{ row }">{{ partyName(row.nextActionParty) }}</template></el-table-column>
      <el-table-column label="最近回复" width="165"><template #default="{ row }">{{ dateTime(row.lastReplyTime) }}</template></el-table-column>
      <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button type="primary" link @click="openDetail(row)">处理</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="total" class="pagination" background layout="total, prev, pager, next" :total="total" :page-size="query.pageSize" :current-page="query.pageNum" @current-change="changePage" />

    <el-drawer v-model="drawerVisible" title="客服工单详情" size="min(680px, 94vw)" destroy-on-close>
      <div v-loading="detailLoading" class="drawer-content">
        <section v-if="detail.ticket?.id" class="ticket-summary">
          <div class="summary-title"><div><span>{{ detail.ticket.ticketNo }}</span><h3>{{ detail.ticket.subject }}</h3></div><el-tag :type="statusTone(detail.ticket.status)">{{ statusName(detail.ticket.status) }}</el-tag></div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="问题类型">{{ typeName(detail.ticket.type) }}</el-descriptions-item>
            <el-descriptions-item label="会员账号">{{ detail.ticket.memberAccount || '-' }}</el-descriptions-item>
            <el-descriptions-item label="关联订单">{{ detail.ticket.orderNo || '未关联' }}</el-descriptions-item>
            <el-descriptions-item label="关联售后">{{ detail.ticket.afterSaleNo || '未关联' }}</el-descriptions-item>
            <el-descriptions-item label="首次响应目标"><span :class="{ overdue: detail.ticket.firstResponseOverdue }">{{ dateTime(detail.ticket.firstResponseDeadline) }}</span></el-descriptions-item>
            <el-descriptions-item label="下一责任方">{{ partyName(detail.ticket.nextActionParty) }}</el-descriptions-item>
          </el-descriptions>
          <p class="next-hint">{{ detail.ticket.nextActionHint }}</p>
        </section>
        <section class="conversation">
          <article v-for="item in detail.replies || []" :key="item.id" :class="['reply', item.senderType.toLowerCase()]">
            <div><strong>{{ item.senderLabel }}</strong><time>{{ dateTime(item.createTime) }}</time></div><p>{{ item.content }}</p>
          </article>
        </section>
        <section v-if="detail.ticket?.id && detail.ticket.status !== 'CLOSED'" class="reply-form">
          <el-input v-model="replyContent" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="请输入给会员的处理说明；不要填写内部密码、密钥或不应公开的信息。" />
          <div><el-select v-model="nextStatus" style="width:190px"><el-option v-for="item in adminStatuses" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-button type="primary" :loading="replying" @click="submitReply">回复并更新状态</el-button></div>
        </section>
        <el-empty v-else-if="!detailLoading && !detail.ticket?.id" description="工单不存在或无权查看" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/store'
import { getAdminServiceTicket, listAdminServiceTickets, replyAdminServiceTicket } from '@/api/shop'

const store = useAppStore()
const isMerchantUser = computed(() => Boolean(store.userInfo?.merchantId))
const types = [{ value: 'CONSULTATION', label: '咨询' }, { value: 'COMPLAINT', label: '投诉' }, { value: 'AFTER_SALE_DISPUTE', label: '售后争议' }, { value: 'ACCOUNT', label: '账号问题' }, { value: 'OTHER', label: '其他' }]
const statuses = [{ value: 'OPEN', label: '待处理' }, { value: 'PROCESSING', label: '处理中' }, { value: 'WAITING_MEMBER', label: '待会员补充' }, { value: 'RESOLVED', label: '已答复' }, { value: 'CLOSED', label: '已关闭' }]
const adminStatuses = statuses.filter((item) => item.value !== 'OPEN')
const query = reactive({ keyword: '', status: '', type: '', pageNum: 1, pageSize: 20 })
const tickets = ref([])
const total = ref(0)
const loading = ref(false)
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({ ticket: null, replies: [] })
const replyContent = ref('')
const nextStatus = ref('WAITING_MEMBER')
const replying = ref(false)

const typeName = (value) => types.find((item) => item.value === value)?.label || '其他'
const statusName = (value) => statuses.find((item) => item.value === value)?.label || value
const statusTone = (value) => ({ OPEN: 'warning', PROCESSING: 'primary', WAITING_MEMBER: 'info', RESOLVED: 'success', CLOSED: '' }[value] || '')
const partyName = (value) => ({ CUSTOMER_SERVICE: '商城客服', MEMBER: '会员', NONE: '无需处理' }[value] || '-')
const dateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-'
const newKey = () => `service-ticket-admin-${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`}`.slice(0, 128)

const load = async () => {
  loading.value = true
  try {
    const res = (await listAdminServiceTickets({ ...query, keyword: query.keyword || undefined, status: query.status || undefined, type: query.type || undefined })).data || {}
    tickets.value = res.list || []
    total.value = Number(res.total || 0)
  } finally { loading.value = false }
}
const search = () => { query.pageNum = 1; load() }
const reset = () => { Object.assign(query, { keyword: '', status: '', type: '', pageNum: 1 }); load() }
const changePage = (page) => { query.pageNum = page; load() }
const openDetail = async (row) => {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = { ticket: null, replies: [] }
  replyContent.value = ''
  nextStatus.value = row.status === 'RESOLVED' ? 'RESOLVED' : 'WAITING_MEMBER'
  try { detail.value = (await getAdminServiceTicket(row.id)).data || detail.value }
  finally { detailLoading.value = false }
}
const submitReply = async () => {
  if (!replyContent.value.trim()) return ElMessage.warning('请输入回复内容')
  replying.value = true
  try {
    detail.value = (await replyAdminServiceTicket(detail.value.ticket.id, { content: replyContent.value.trim(), nextStatus: nextStatus.value }, newKey())).data || detail.value
    replyContent.value = ''
    ElMessage.success('客服回复已发送，会员站内消息将同步提醒')
    await load()
  } finally { replying.value = false }
}
onMounted(load)
</script>

<style scoped>
.ticket-page{padding:2px}.page-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}.page-heading h2{margin:0}.page-heading p{margin:6px 0 0;color:#909399}.filters{margin:18px 0 5px}.filters .el-input{width:280px}.filters .el-select{width:150px}.overdue-tag{display:block;width:max-content;margin-top:5px}.pagination{justify-content:flex-end;margin-top:18px}.drawer-content{min-height:280px}.ticket-summary{padding:16px;border:1px solid #e6ebf2;border-radius:12px}.summary-title{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;margin-bottom:14px}.summary-title span{color:#909399;font-size:12px}.summary-title h3{margin:5px 0 0}.next-hint{margin:12px 0 0;color:#606266}.overdue{color:#d93838}.conversation{display:grid;gap:12px;margin:18px 0}.reply{max-width:82%;padding:13px 15px;border-radius:12px;background:#f4f6f8}.reply.member{justify-self:start}.reply.admin{justify-self:end;background:#ecf5ff}.reply.system{justify-self:center;color:#606266}.reply div{display:flex;justify-content:space-between;gap:25px}.reply time{color:#909399;font-size:12px}.reply p{margin:8px 0 0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}.reply-form{display:grid;gap:12px;padding-top:16px;border-top:1px solid #e6ebf2}.reply-form>div{display:flex;justify-content:flex-end;gap:10px}@media(max-width:760px){.page-heading{align-items:flex-start;gap:10px;flex-direction:column}.filters .el-input,.filters .el-select{width:100%}.reply{max-width:94%}}
</style>
