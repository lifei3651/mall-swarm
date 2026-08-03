<template>
  <div class="page-container review-page">
    <div class="page-heading">
      <div>
        <h2>商品评价</h2>
        <p>只会产生真实确认收货评价；隐藏不会删除记录，操作会进入后台操作日志。</p>
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
        <template #default="{ row }"><div class="review-content">{{ row.content }}</div></template>
      </el-table-column>
      <el-table-column label="展示状态" width="100" align="center">
        <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '展示中' : '已隐藏' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="评价时间" width="170"><template #default="{ row }">{{ formatTime(row.createTime) }}</template></el-table-column>
      <el-table-column label="隐藏记录" min-width="230">
        <template #default="{ row }">
          <template v-if="row.hiddenTime">
            <div>{{ row.hiddenReason || '-' }}</div>
            <div class="subtle">{{ row.hiddenByName || '系统管理员' }} · {{ formatTime(row.hiddenTime) }}</div>
          </template>
          <span v-else class="subtle">无</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" type="danger" link @click="hideReview(row)">隐藏</el-button>
          <el-button v-else type="success" link @click="restoreReview(row)">恢复展示</el-button>
        </template>
      </el-table-column>
    </el-table>

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
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { listProductReviews, listShopProducts, updateProductReviewStatus } from '@/api/shop'
import { validateSearchKeyword } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'

const loading = ref(false)
const tableData = ref([])
const products = ref([])
const query = ref({ keyword: '', productId: null, rating: null, status: null })
const pagination = ref({ page: 1, size: 10, total: 0 })
const searchFeedback = ref('')
const tableEmptyText = ref('暂无商品评价')
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => query.value.keyword,
  () => search(),
)

const fetchProducts = async () => {
  const res = await listShopProducts({ pageNum: 1, pageSize: 200 })
  products.value = res.data?.list || []
}

const fetchData = async () => {
  const validation = validateSearchKeyword(query.value.keyword, { label: '评价关键词', maxLength: 200 })
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

const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'

onMounted(async () => { await Promise.all([fetchProducts(), fetchData()]) })
</script>

<style scoped>
.page-heading { display:flex; justify-content:space-between; gap:20px; align-items:flex-start; margin-bottom:18px; }
.page-heading h2 { margin:0; color:#1f2937; font-size:24px; }
.page-heading p { margin:8px 0 0; color:#6b7280; font-size:13px; }
.product-name { color:#1f2937; font-weight:700; }
.subtle { margin-top:4px; color:#909399; font-size:12px; }
.review-content { white-space:pre-wrap; line-height:1.65; word-break:break-word; }
@media (max-width: 900px) { .page-heading { flex-direction:column; } }
</style>
