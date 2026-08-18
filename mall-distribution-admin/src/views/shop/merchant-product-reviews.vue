<template>
  <div class="page-container review-page">
    <div class="page-heading">
      <div><h2>商户商品审核</h2><p>确认商品资料、销售价和结算价；通过后自动上架，驳回后由商户修改再提交。</p></div>
      <el-tag type="warning" effect="plain">待审核 {{ pendingCount }} 件</el-tag>
    </div>

    <el-alert title="结算价是平台应付给商户的单件货款。审核通过时同时确认本版销售价和结算价；后续改价必须先下架并重新审核。" type="warning" :closable="false" show-icon />
    <div class="toolbar">
      <el-input v-model="query.keyword" clearable placeholder="商品名称、编号或商户" @keyup.enter="search" />
      <el-select v-model="query.status" clearable placeholder="全部审核状态" @change="search">
        <el-option label="待审核" value="PENDING" /><el-option label="已通过" value="APPROVED" /><el-option label="已驳回" value="REJECTED" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe :empty-text="query.status === 'PENDING' ? '暂无待审核商品' : '暂无审核记录'">
      <el-table-column prop="merchantName" label="商户" min-width="150" />
      <el-table-column label="商品" min-width="240">
        <template #default="{ row }"><strong>{{ row.productName }}</strong><small>{{ row.productNo || '未设置编号' }} · 第 {{ row.reviewVersion }} 版</small></template>
      </el-table-column>
      <el-table-column label="提交类型" width="105"><template #default="{ row }">{{ row.reviewType === 'INITIAL' ? '首次上架' : '变更重审' }}</template></el-table-column>
      <el-table-column label="销售价" width="125"><template #default="{ row }"><span class="sale-price">¥{{ money(row.salePrice) }}</span></template></el-table-column>
      <el-table-column label="结算价" width="135"><template #default="{ row }"><span class="settlement-price">¥{{ money(row.settlementPrice) }}</span></template></el-table-column>
      <el-table-column prop="skuCount" label="启用SKU" width="95" />
      <el-table-column label="审核状态" width="105"><template #default="{ row }"><el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag></template></el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="170" :formatter="formatDateTimeCell" />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetail(row)">查看详情</el-button>
          <el-button v-if="row.status === 'PENDING'" type="success" link @click="openDecision(row)">审核</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pagination-container" background layout="total, prev, pager, next, sizes" :total="pagination.total" v-model:current-page="pagination.page" v-model:page-size="pagination.size" @current-change="load" @size-change="load" />

    <el-dialog v-model="detailVisible" title="商品审核详情" width="820px">
      <div v-if="current.id" class="detail-grid">
        <div><span>商户</span><strong>{{ current.merchantName }}</strong></div><div><span>商品</span><strong>{{ current.productName }}</strong></div>
        <div class="price-card"><span>本版销售价</span><strong>¥{{ money(current.salePrice) }}</strong></div>
        <div class="price-card settlement"><span>本版结算价</span><strong>¥{{ money(current.settlementPrice) }}</strong></div>
        <div><span>售后窗口结束后结算等待</span><strong>{{ snapshot.effectiveSettlementDays || 0 }} 天</strong></div>
        <div><span>提交人</span><strong>{{ current.submitterName || '系统' }}</strong></div><div><span>提交时间</span><strong>{{ dateTime(current.submittedAt) }}</strong></div>
      </div>
      <el-table v-if="snapshotSkus.length" :data="snapshotSkus" border size="small" class="sku-table">
        <el-table-column prop="skuName" label="规格" min-width="150" />
        <el-table-column label="销售价" width="130"><template #default="{ row }">¥{{ money(row.salePrice) }}</template></el-table-column>
        <el-table-column label="结算价" width="130"><template #default="{ row }"><strong class="settlement-price">¥{{ money(row.costAmount) }}</strong></template></el-table-column>
        <el-table-column prop="stock" label="库存" width="100" />
      </el-table>
      <el-descriptions v-if="current.status !== 'PENDING'" :column="1" border class="review-result">
        <el-descriptions-item label="审核人">{{ current.reviewerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核说明">{{ current.reviewRemark || '审核通过' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="decisionVisible" title="审核商户商品" width="560px">
      <el-alert :title="`请确认：销售价 ¥${money(current.salePrice)}，结算价 ¥${money(current.settlementPrice)}，售后窗口结束后再等待 ${snapshot.effectiveSettlementDays || 0} 天`" type="warning" :closable="false" show-icon />
      <el-form label-width="90px" class="decision-form">
        <el-form-item label="审核结果"><el-radio-group v-model="decision.approved"><el-radio-button :value="true">通过并上架</el-radio-button><el-radio-button :value="false">驳回修改</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="审核说明" :required="decision.approved === false"><el-input v-model="decision.remark" type="textarea" :rows="4" maxlength="500" show-word-limit :placeholder="decision.approved ? '可选：填写审核说明' : '请明确填写驳回原因，方便商户修改'" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="decisionVisible=false">取消</el-button><el-button :type="decision.approved ? 'success' : 'danger'" :loading="saving" @click="submitDecision">确认{{ decision.approved ? '通过并上架' : '驳回' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { decideMerchantProductReview, listMerchantProductReviews } from '@/api/shop'
import { formatDateTimeCell } from '@/utils/dateTime'

const rows = ref([]); const loading = ref(false); const saving = ref(false)
const detailVisible = ref(false); const decisionVisible = ref(false); const current = ref({})
const query = reactive({ keyword: '', status: 'PENDING' }); const pagination = reactive({ page: 1, size: 20, total: 0 })
const decision = reactive({ approved: true, remark: '' })
const pendingCount = computed(() => query.status === 'PENDING' ? pagination.total : rows.value.filter((item) => item.status === 'PENDING').length)
const money = (value) => Number(value || 0).toFixed(2)
const dateTime = (value) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
const statusMeta = (value) => ({ PENDING: { label: '待审核', type: 'warning' }, APPROVED: { label: '已通过', type: 'success' }, REJECTED: { label: '已驳回', type: 'danger' } }[value] || { label: value || '-', type: 'info' })
const snapshot = computed(() => { try { return JSON.parse(current.value.productSnapshot || '{}') } catch { return {} } })
const snapshotSkus = computed(() => (snapshot.value.skus || []).filter((item) => Number(item.status) === 1))
const load = async () => { loading.value = true; try { const res = await listMerchantProductReviews({ ...query, pageNum: pagination.page, pageSize: pagination.size }); rows.value = res.data?.list || []; pagination.total = res.data?.total || 0 } finally { loading.value = false } }
const search = () => { pagination.page = 1; load() }
const reset = () => { query.keyword = ''; query.status = 'PENDING'; search() }
const openDetail = (row) => { current.value = row; detailVisible.value = true }
const openDecision = (row) => { current.value = row; decision.approved = true; decision.remark = ''; decisionVisible.value = true }
const submitDecision = async () => {
  if (!decision.approved && !decision.remark.trim()) return ElMessage.warning('请填写驳回原因')
  const action = decision.approved ? '通过并自动上架' : '驳回并保持下架'
  await ElMessageBox.confirm(`确认${action}“${current.value.productName}”？`, '确认审核', { type: 'warning' })
  saving.value = true
  try { await decideMerchantProductReview(current.value.id, { approved: decision.approved, remark: decision.remark.trim() || null }); ElMessage.success(decision.approved ? '审核通过，商品已自动上架' : '已驳回，等待商户修改后重新提交'); decisionVisible.value = false; await load() } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.page-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.page-heading h2{margin:0}.page-heading p{margin:6px 0 0;color:#909399}.toolbar{display:flex;gap:10px;width:min(720px,100%);margin:16px 0}.toolbar .el-input{flex:1}.toolbar .el-select{width:170px}.el-table small{display:block;margin-top:5px;color:#909399}.sale-price{font-weight:700;color:#e65d00}.settlement-price{font-weight:800;color:#1b6f3a}.detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:18px}.detail-grid>div{display:flex;flex-direction:column;gap:7px;padding:14px;border:1px solid #ebeef5;border-radius:8px}.detail-grid span{color:#909399;font-size:13px}.price-card strong{font-size:24px;color:#e65d00}.price-card.settlement{border-color:#b7dfc4;background:#f0f9f3}.price-card.settlement strong{color:#1b6f3a}.sku-table,.review-result{margin-top:16px}.decision-form{margin-top:20px}
</style>
