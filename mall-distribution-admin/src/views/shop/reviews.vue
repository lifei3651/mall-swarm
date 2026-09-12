<template>
  <div class="page-container review-page">
    <div class="page-heading">
      <div>
        <h2>商品评价</h2>
        <p>{{ isMerchant ? '查看并回复本商家订单的真实购买评价。' : '回复本商城的真实购买评价；隐藏不会删除记录。' }} 商家和平台分别回复，操作留痕。</p>
      </div>
      <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
    </div>

    <div class="search-container">
      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="商品/订单号/买家/内容" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="商品">
          <el-select v-model="query.productId" clearable filterable placeholder="全部商品" style="width:220px" @change="search">
            <el-option v-for="item in products" :key="item.id" :label="item.productName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="评分">
          <el-select v-model="query.rating" clearable placeholder="全部" style="width:110px" @change="search">
            <el-option v-for="star in [5,4,3,2,1]" :key="star" :label="`${star} 星`" :value="star" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:110px" @change="search">
            <el-option label="展示中" :value="1" />
            <el-option label="已隐藏" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <el-table v-loading="loading" :data="tableData" :empty-text="tableEmptyText" border stripe>
      <el-table-column label="商品信息" min-width="190">
        <template #default="{ row }">
          <div class="product-name">{{ row.productName }}</div>
          <div class="subtle">商品ID：{{ row.productId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="买家与订单" min-width="180">
        <template #default="{ row }">
          <div>{{ row.reviewerName }}</div>
          <div class="subtle">{{ row.orderNo }}</div>
        </template>
      </el-table-column>
      <el-table-column label="评分" width="150">
        <template #default="{ row }"><el-rate :model-value="row.rating" disabled show-score text-color="#ef4444" /></template>
      </el-table-column>
      <el-table-column label="评价内容" min-width="300">
        <template #default="{ row }">
          <div class="review-content">{{ row.content }}</div>
          <div v-if="row.merchantReply" class="review-reply">
            <strong>商家回复</strong><span class="subtle"> · {{ formatTime(row.merchantReplyTime) }}</span>
            <div class="review-content">{{ row.merchantReply }}</div>
          </div>
          <div v-if="row.platformReply" class="review-reply">
            <strong>平台回复</strong><span class="subtle"> · {{ formatTime(row.platformReplyTime) }}</span>
            <div class="review-content">{{ row.platformReply }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="展示状态" width="100" align="center">
        <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '展示中' : '已隐藏' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="评价时间" width="170"><template #default="{ row }">{{ formatTime(row.createTime) }}</template></el-table-column>
      <el-table-column v-if="!isMerchant" label="隐藏记录" min-width="230">
        <template #default="{ row }">
          <template v-if="row.hiddenTime">
            <div>{{ row.hiddenReason || '-' }}</div>
            <div class="subtle">{{ row.hiddenByName || '系统管理员' }} · {{ formatTime(row.hiddenTime) }}</div>
          </template>
          <span v-else class="subtle">无</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right" align="center">
        <template #default="{ row }">
          <div class="review-actions">
            <el-button v-if="row.status === 1" type="primary" link @click="openReply(row)">{{ ownReply(row) ? '修改回复' : '回复评价' }}</el-button>
            <template v-if="!isMerchant">
              <el-button v-if="row.status === 1" type="danger" link @click="hideReview(row)">隐藏</el-button>
              <el-button v-else type="success" link @click="restoreReview(row)">恢复展示</el-button>
            </template>
            <span v-else-if="row.status !== 1" class="subtle">已隐藏，暂不可回复</span>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="replyVisible" :title="isMerchant ? '商家回复' : '平台回复'" width="min(560px, 92vw)"
      :close-on-click-modal="false" :close-on-press-escape="!replySaving" :show-close="!replySaving">
      <div class="reply-original">{{ replyTarget?.content }}</div>
      <p class="reply-help">回复将公开展示在这条评价下。请勿填写手机号、地址等个人信息。</p>
      <el-input v-model="replyContent" type="textarea" :rows="5" maxlength="500" show-word-limit
        :disabled="replySaving" placeholder="针对买家的评价作出回复" aria-label="回复内容" />
      <p v-if="replyError" role="alert" class="reply-error">{{ replyError }}</p>
      <template #footer>
        <el-button :disabled="replySaving" @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replySaving" :disabled="!replyContent.trim()" @click="saveReply">保存回复</el-button>
      </template>
    </el-dialog>

    <el-pagination
      class="pagination-container"
      background
      layout="total, prev, pager, next, sizes"
      :total="pagination.total"
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.size"
      @current-change="fetchData"
      @size-change="fetchData"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { listProductReviews, listShopProducts, updateProductReviewStatus, replyProductReview } from '@/api/shop'
import { useAppStore } from '@/store'
import { validateSearchKeyword } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { formatDateTime as formatTime } from '@/utils/dateTime'

const loading = ref(false)
const store = useAppStore()
const isMerchant = computed(() => Boolean(store.userInfo?.merchantId))
const ownReply = (row) => (isMerchant.value ? row.merchantReply : row.platformReply) || ''
const replyVisible = ref(false)
const replySaving = ref(false)
const replyTarget = ref(null)
const replyContent = ref('')
const replyExpectedVersion = ref(0)
const replyError = ref('')

const openReply = (row) => {
  if (row.status !== 1 || replySaving.value) return
  replyTarget.value = row
  replyExpectedVersion.value = Number((isMerchant.value ? row.merchantReplyVersion : row.platformReplyVersion) || 0)
  replyContent.value = ownReply(row)
  replyError.value = ''
  replyVisible.value = true
}

const saveReply = async () => {
  if (replySaving.value || !replyTarget.value) return
  const content = replyContent.value.trim()
  if (!content || content.length > 500) {
    replyError.value = '请填写 1～500 字的回复内容'
    return
  }
  replySaving.value = true
  replyError.value = ''
  try {
    await replyProductReview(replyTarget.value.id, { content, expectedVersion: replyExpectedVersion.value })
    replyVisible.value = false
    await fetchData()
  } catch (error) {
    replyError.value = error?.message || '回复未保存，请重试'
  } finally { replySaving.value = false }
}
const tableData = ref([])
const products = ref([])
const query = ref({ keyword: '', productId: null, rating: null, status: null })
const pagination = ref({ page: 1, size: 10, total: 0 })
const searchFeedback = ref('')
const tableEmptyText = ref('暂无商品评价')
const PRODUCT_OPTION_PAGE_SIZE = 100
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => query.value.keyword,
  () => search(),
)

const fetchProducts = async () => {
  const loadedProducts = []
  let pageNum = 1
  let total = 0

  do {
    const res = await listShopProducts({ pageNum, pageSize: PRODUCT_OPTION_PAGE_SIZE })
    const pageProducts = res.data?.list || []
    loadedProducts.push(...pageProducts)
    total = Number(res.data?.total ?? loadedProducts.length)
    pageNum += 1

    if (pageProducts.length < PRODUCT_OPTION_PAGE_SIZE) break
  } while (loadedProducts.length < total)

  products.value = loadedProducts
}

const fetchData = async () => {
  const validation = validateSearchKeyword(query.value.keyword, { label: '评价关键词', maxLength: 100 })
  if (!validation.valid) {
    tableData.value = []
    pagination.value.total = 0
    searchFeedback.value = validation.message
    tableEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  query.value.keyword = validation.keyword
  markKeywordSearchApplied(validation.keyword)
  searchFeedback.value = ''
  tableEmptyText.value = validation.keyword
    ? `未找到与“${validation.keyword}”匹配的商品评价`
    : '暂无商品评价'
  loading.value = true
  try {
    const res = await listProductReviews({ ...query.value, pageNum: pagination.value.page, pageSize: pagination.value.size })
    tableData.value = res.data?.list || []
    pagination.value.total = res.data?.total || 0
  } finally { loading.value = false }
}

const search = () => { pagination.value.page = 1; fetchData() }
const resetQuery = () => {
  query.value = { keyword: '', productId: null, rating: null, status: null }
  pagination.value.page = 1
  fetchData()
}

const hideReview = async (row) => {
  const { value } = await ElMessageBox.prompt('请填写隐藏原因。评价不会被删除，后续可以恢复展示。', '隐藏商品评价', {
    confirmButtonText: '确认隐藏',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '例如：含联系方式、辱骂或与商品无关',
    inputValidator: (text) => Boolean(text?.trim()) || '隐藏原因不能为空',
  })
  await updateProductReviewStatus(row.id, { status: 0, reason: value.trim() })
  ElMessage.success('评价已隐藏，记录和操作日志均已保留')
  await fetchData()
}

const restoreReview = async (row) => {
  await ElMessageBox.confirm('恢复后，该评价会重新显示在商品详情页。', '恢复评价', { type: 'warning' })
  await updateProductReviewStatus(row.id, { status: 1, reason: '' })
  ElMessage.success('评价已恢复展示')
  await fetchData()
}


onMounted(async () => { await Promise.all([fetchProducts(), fetchData()]) })
</script>

<style scoped>
.page-heading { display:flex; justify-content:space-between; gap:20px; align-items:flex-start; margin-bottom:18px; }
.page-heading h2 { margin:0; color:#1f2937; font-size:24px; }
.page-heading p { margin:8px 0 0; color:#6b7280; font-size:13px; }
.product-name { color:#1f2937; font-weight:700; }
.subtle { margin-top:4px; color:#909399; font-size:12px; }
.review-content { white-space:pre-wrap; line-height:1.65; word-break:break-word; }
.review-reply { margin-top:12px; padding:10px 12px; background:#f6f7f8; border-radius:6px; }
.review-reply strong { font-size:13px; color:#525866; }
.review-actions { display:flex; align-items:center; flex-direction:column; gap:8px; }
.review-actions .el-button { margin-left:0; }
.reply-original { padding:12px; background:#f6f7f8; max-height:120px; overflow:auto; white-space:pre-wrap; overflow-wrap:anywhere; }
.reply-help { color:#6b7280; font-size:13px; line-height:1.6; }
.reply-error { color:#dc2626; font-size:13px; }
@media (max-width: 900px) { .page-heading { flex-direction:column; } }
</style>
