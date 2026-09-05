<template>
  <section class="shipping-panel" v-loading="loading">
    <div class="heading"><h3>微信发货同步台账</h3><el-button data-test="refresh" @click="load">刷新同步状态</el-button></div>
    <p>与用户订阅提醒独立；只同步已保存的物流信息，不重新发货、退款、收款或重发资金消息。</p>
    <el-alert v-if="failedCount" type="error" :closable="false" :title="`有 ${failedCount} 笔微信发货同步永久失败，请核对失败原因并处理`" />
    <el-alert v-if="!enabled" type="warning" :closable="false" title="微信发货同步运行门禁未开启；台账可查看，不能重新同步。" />
    <el-alert v-if="error" type="error" :closable="false" :title="error" />
    <div class="filter"><span>同步状态</span><el-select v-model="status" data-test="status" @change="changeStatus">
      <el-option label="全部" value=""/><el-option v-for="(label,key) in labels" :key="key" :label="label" :value="key"/>
    </el-select></div>
    <el-table :data="rows" stripe>
      <el-table-column prop="id" label="任务编号" min-width="130"/>
      <el-table-column prop="paymentNoHint" label="支付单号尾号" min-width="130"/>
      <el-table-column label="状态" min-width="110"><template #default="{row}">{{labels[row.status] || row.status}}</template></el-table-column>
      <el-table-column prop="revision" label="当前版本" width="90"/><el-table-column prop="syncedRevision" label="已处理版本" width="100"/>
      <el-table-column prop="attemptCount" label="尝试次数" width="90"/>
      <el-table-column prop="errorCode" label="脱敏失败原因" min-width="210"/>
      <el-table-column prop="nextRetryTime" label="下次自动重试" min-width="160"/>
      <el-table-column prop="updateTime" label="更新时间" min-width="160"/>
      <el-table-column label="操作" width="140"><template #default="{row}">
        <el-button v-if="enabled && row.canRetry" :data-test="`requeue-${row.id}`" :disabled="Boolean(busyId) || loading || Boolean(error)" :loading="busyId === row.id" @click="requeue(row)">重新同步</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="pageNum" :page-size="20" :total="total" layout="prev,pager,next,total" @current-change="load" />
  </section>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listShippingSynchronizations, requeueShippingSynchronization } from '@/api/messageOperation'
const labels = { PENDING:'待同步', SENDING:'同步中', SUCCESS:'已同步', RETRYABLE:'自动重试中', PERMANENT:'永久失败' }
const loading=ref(false), error=ref(''), enabled=ref(false), failedCount=ref(0), rows=ref([]), pageNum=ref(1), total=ref(0), status=ref(''), busyId=ref('')
let requestSequence=0
async function load() {
  const sequence=++requestSequence
  loading.value=true; error.value=''
  try {
    const result=(await listShippingSynchronizations({pageNum:pageNum.value,pageSize:20,status:status.value || undefined})).data || {}
    if (sequence !== requestSequence) return
    enabled.value=Boolean(result.enabled); failedCount.value=Number(result.failedCount || 0)
    rows.value=result.tasks?.list || []; total.value=Number(result.tasks?.total || 0)
  } catch (_) { if (sequence === requestSequence) error.value='微信发货同步台账读取失败，请重试' }
  finally { if (sequence === requestSequence) loading.value=false }
}
function changeStatus() { pageNum.value=1; return load() }
async function requeue(row) {
  if (busyId.value || loading.value || error.value || !enabled.value || !row.canRetry) return
  busyId.value=row.id
  try {
    await ElMessageBox.confirm('请先核对并修复失败原因。本操作只重新同步现有物流信息，不改变订单、退款、资金或消息。确认继续？','重新同步微信发货信息',{type:'warning',confirmButtonText:'确认重新同步',cancelButtonText:'暂不操作'})
    await requeueShippingSynchronization(row.id,row.revision)
    ElMessage.success('已重新排队，实际同步结果请刷新台账查看')
    await load()
  } catch (failure) { if (failure !== 'cancel' && failure !== 'close') ElMessage.error('未能重新排队，请刷新状态后重试') }
  finally { busyId.value='' }
}
onMounted(load)
</script>
<style scoped>
.shipping-panel{padding:20px;margin-bottom:18px;background:#fff;border:1px solid #e8ebf0;border-radius:14px}.heading{display:flex;justify-content:space-between;align-items:center;gap:12px}.heading h3{margin:0}.shipping-panel p{font-size:13px;color:#667085;line-height:1.6}.filter{display:flex;align-items:center;gap:12px;margin:14px 0}.filter .el-select{width:180px}.el-alert{margin:12px 0}.el-pagination{justify-content:flex-end;margin-top:14px}
</style>
