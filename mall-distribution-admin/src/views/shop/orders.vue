<template>
  <div class="page-container">
    <nav class="order-state-nav" aria-label="订单状态筛选">
      <button
        v-for="item in orderStateOptions"
        :key="item.value"
        type="button"
        :class="{ active: query.orderState === item.value }"
        @click="changeOrderState(item.value)"
      >
        {{ item.label }}
      </button>
    </nav>

    <div class="search-container order-search-panel">
      <el-form :inline="true" :model="query">
        <el-form-item label="订单搜索">
          <el-input v-model="query.keyword" placeholder="请输入订单号、收货人或手机号" clearable @keyup.enter="handleOrderSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="orderLoading" @click="handleOrderSearch">查询</el-button>
          <el-button :icon="Refresh" @click="resetOrderQuery">重置</el-button>
        </el-form-item>
        <el-form-item class="order-batch-actions">
          <el-button :icon="Download" :loading="exportLoading" @click="handleExportOrders">导出订单</el-button>
          <el-tooltip content="表格只处理订单号、物流公司、物流单号和发货数量" placement="top">
            <el-button type="success" plain :icon="Download" :loading="templateLoading" @click="handleDownloadShipmentTemplate">下载发货表</el-button>
          </el-tooltip>
          <el-upload
            accept=".xlsx,.xls"
            :show-file-list="false"
            :http-request="handleShipmentImport"
            :disabled="importLoading"
          >
            <el-button type="warning" plain :icon="Upload" :loading="importLoading">导入物流并发货</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <el-alert
        title="系统只读取订单号、物流公司、物流单号和发货数量。错误行会单独跳过，不影响其他正确行发货；拆成多个包裹时复制订单行，多个订单合箱时可填写相同物流信息。"
        type="info"
        :closable="false"
        show-icon
        class="shipping-workflow-tip"
      />
    </div>

    <el-alert v-if="orderSearchFeedback" :title="orderSearchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <el-table class="order-table" :data="orders" v-loading="orderLoading" :empty-text="orderEmptyText" style="width: 100%">
          <el-table-column label="商品名称" min-width="190">
            <template #default="{ row }">
              <div class="order-item-cell-list">
                <div v-for="item in row.items || []" :key="item.id" class="order-item-cell product-name-item">
                  <strong>{{ item.productName || '商品' }}</strong>
                </div>
              </div>
              <div v-if="row.afterSales?.length" class="inline-after-sales">
                <div v-for="sale in row.afterSales" :key="sale.id" class="inline-after-sale-item">
                  <div>
                    <el-tag size="small" :type="afterSaleTag(sale.status)">{{ afterSaleStatus(sale.status) }}</el-tag>
                    <span>{{ sale.afterSaleNo }} · {{ sale.reason || '未填写原因' }}</span>
                    <small>{{ sale.refundQuantity || 0 }}件 / ¥{{ money(sale.refundAmount) }}</small>
                  </div>
                  <div v-if="sale.status === 0" class="inline-after-sale-actions">
                    <el-button type="success" link @click.stop="openAudit(sale, 1)">通过退款</el-button>
                    <el-button type="danger" link @click.stop="openAudit(sale, 2)">拒绝</el-button>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="商品规格" min-width="125">
            <template #default="{ row }">
              <div class="order-item-cell-list">
                <div v-for="item in row.items || []" :key="item.id" class="order-item-cell product-spec-item">
                  {{ formatProductSpec(item) }}
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="商品数量" width="82" align="center">
            <template #default="{ row }">
              <div class="order-item-cell-list">
                <div v-for="item in row.items || []" :key="item.id" class="order-item-cell product-quantity-item">
                  {{ Number(item.quantity || 0) }} 件
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="订单编号" min-width="175">
            <template #default="{ row }">
              <div class="order-no">{{ row.order?.orderNo }}</div>
              <div class="sub">登录账号 {{ row.memberAccount || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="订单状态" width="100">
            <template #default="{ row }">
              <el-tag :type="orderDisplayTag(row)">{{ orderDisplayStatus(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="订单总金额" width="110">
            <template #default="{ row }">
              <span>¥{{ money(row.order?.payAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="奖金总拨出" width="110">
            <template #default="{ row }">
              <span :class="{ danger: payoutExceeded(row.order?.payAmount, row.finance?.bonusAmount) }">
                ¥{{ money(row.finance?.bonusAmount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="物流信息" width="155">
            <template #default="{ row }">
              <div v-if="shipmentRows(row).length" class="shipment-list">
                <div v-for="(shipment, index) in shipmentRows(row)" :key="`${shipment.deliveryCompany}-${shipment.deliveryNo}-${index}`">
                  <span>{{ shipment.deliveryCompany || '-' }}</span>
                  <div class="sub">{{ shipment.deliveryNo || '-' }}</div>
                  <div class="sub">发货 {{ shipment.shipmentQuantity || 0 }} 件</div>
                </div>
              </div>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="下单时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.order?.createTime) }}</template>
          </el-table-column>
          <el-table-column label="收货信息" min-width="190">
            <template #default="{ row }">
              <div>{{ row.order?.receiverName }} {{ row.order?.receiverPhone }}</div>
              <div class="sub">{{ row.order?.receiverAddress }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="160">
            <template #default="{ row }">
              <el-button type="success" link @click="openBonusFlows(row.order?.id, row.order?.orderNo, row.memberAccount)">
                奖金去向
              </el-button>
              <el-button type="primary" link :disabled="!canShipOrder(row)" @click="openShip(row)">
                {{ shipmentRows(row).length ? '继续发货' : '发货' }}
              </el-button>
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
      @current-change="fetchOrders"
      @size-change="fetchOrders"
    />

    <el-dialog v-model="shipDialogVisible" :title="currentOrder?.order?.status === 2 ? '添加物流包裹' : '订单发货'" width="520px">
      <el-form :model="shipForm" label-width="92px">
        <el-form-item label="订单号">
          <el-input :model-value="currentOrder?.order?.orderNo" disabled />
        </el-form-item>
        <el-form-item v-if="shipmentRows(currentOrder).length" label="已有包裹">
          <div class="existing-shipments">
            <div v-for="(shipment, index) in shipmentRows(currentOrder)" :key="`${shipment.deliveryCompany}-${shipment.deliveryNo}-${index}`">
              包裹{{ index + 1 }}：{{ shipment.deliveryCompany }} / {{ shipment.deliveryNo }}
              / {{ shipment.shipmentQuantity || 0 }}件
            </div>
          </div>
        </el-form-item>
        <el-form-item label="物流公司" required>
          <el-input v-model="shipForm.deliveryCompany" placeholder="例如 顺丰速运" />
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model="shipForm.deliveryNo" />
        </el-form-item>
        <el-form-item label="发货数量" required>
          <el-input-number v-model="shipForm.shipmentQuantity" :min="1" :max="Math.max(1, remainingShipmentQuantity(currentOrder))" :step="1" step-strictly />
          <span class="remaining-tip">剩余可发 {{ remainingShipmentQuantity(currentOrder) }} 件</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitShip">{{ currentOrder?.order?.status === 2 ? '确认添加' : '确认发货' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shipmentResultVisible" title="批量发货导入结果" width="720px">
      <el-alert
        :title="shipmentResult.message || '导入完成'"
        :type="shipmentResult.failedCount > 0 ? 'warning' : 'success'"
        :closable="false"
        show-icon
      />
      <el-descriptions :column="4" border class="shipment-result-summary">
        <el-descriptions-item label="表格数据">{{ shipmentResult.totalRows || 0 }} 条</el-descriptions-item>
        <el-descriptions-item label="新增包裹记录">{{ shipmentResult.shippedCount || 0 }} 条</el-descriptions-item>
        <el-descriptions-item label="重复跳过">{{ shipmentResult.skippedCount || 0 }} 条</el-descriptions-item>
        <el-descriptions-item label="错误">{{ shipmentResult.failedCount || 0 }} 条</el-descriptions-item>
      </el-descriptions>
      <el-table
        v-if="shipmentResult.errors?.length"
        :data="shipmentResult.errors"
        max-height="360"
        class="shipment-error-table"
      >
        <el-table-column prop="rowNumber" label="Excel行号" width="100" />
        <el-table-column prop="orderNo" label="订单号" width="210">
          <template #default="{ row }">{{ row.orderNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="message" label="错误原因" min-width="280" />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="shipmentResultVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="auditDialogVisible" :title="auditForm.status === 1 ? '通过售后' : '拒绝售后'" width="460px">
      <el-form :model="auditForm" label-width="92px">
        <el-form-item label="售后号">
          <el-input :model-value="currentAfterSale?.afterSaleNo" disabled />
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input v-model="auditForm.auditRemark" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button :type="auditForm.status === 1 ? 'success' : 'danger'" @click="submitAudit">
          确认{{ auditForm.status === 1 ? '通过' : '拒绝' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bonusDialogVisible" title="订单奖金去向" width="960px" destroy-on-close>
      <div v-loading="bonusLoading">
        <el-descriptions :column="4" border class="bonus-summary">
          <el-descriptions-item label="订单编号">{{ bonusOrder.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下单会员账号">{{ bonusOrder.memberAccount || '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单总金额">
            ¥{{ money(bonusFinance.finance?.payAmount) }}
          </el-descriptions-item>
          <el-descriptions-item label="奖金总拨出">
            <span :class="{ danger: payoutExceeded(bonusFinance.finance?.payAmount, bonusFinance.finance?.bonusAmount) }">
              ¥{{ money(bonusFinance.finance?.bonusAmount) }}
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="payoutExceeded(bonusFinance.finance?.payAmount, bonusFinance.finance?.bonusAmount)"
          title="风险提醒：该订单奖金总拨出已经超过订单总金额"
          type="error"
          :closable="false"
          show-icon
          class="bonus-alert"
        />

        <el-table :data="bonusFinance.bonusFlows || []" style="width: 100%" empty-text="该订单暂未产生奖金记录">
          <el-table-column prop="recordNo" label="奖金记录号" min-width="180" />
          <el-table-column prop="agentMemberAccount" label="获奖会员账号" width="145" />
          <el-table-column prop="agentName" label="获奖会员" width="130" />
          <el-table-column label="奖金类型" width="180">
            <template #default="{ row }">{{ bonusTypeName(row) }}</template>
          </el-table-column>
          <el-table-column prop="commissionLevel" label="关系深度" width="95" />
          <el-table-column label="奖金比例" width="100">
            <template #default="{ row }">{{ percent(row.commissionRate) }}</template>
          </el-table-column>
          <el-table-column label="奖金金额" width="120">
            <template #default="{ row }">¥{{ money(row.commissionAmount) }}</template>
          </el-table-column>
          <el-table-column prop="statusName" label="奖金状态" width="100" />
          <el-table-column label="产生时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  auditShopAfterSale,
  downloadOrderShipmentTemplate,
  exportShopOrders,
  importOrderShipments,
  listShopOrders,
  shipShopOrder,
} from '@/api/shop'
import { getOrderFinance } from '@/api/audit'
import { formatProductSpec } from '@/utils/productSpec'
import { validateSearchKeyword } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'

const orderLoading = ref(false)
const exportLoading = ref(false)
const templateLoading = ref(false)
const importLoading = ref(false)
const orders = ref([])
const orderStateOptions = [
  { label: '全部', value: '' },
  { label: '待付款', value: 'PENDING_PAYMENT' },
  { label: '待发货', value: 'PENDING_SHIPMENT' },
  { label: '售后中', value: 'AFTER_SALE' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已退款', value: 'REFUNDED' },
]
const query = ref({ keyword: '', orderState: '' })
const pagination = ref({ page: 1, size: 10, total: 0 })
const shipDialogVisible = ref(false)
const shipmentResultVisible = ref(false)
const shipmentResult = ref({ success: false, totalRows: 0, shippedCount: 0, skippedCount: 0, failedCount: 0, errors: [] })
const auditDialogVisible = ref(false)
const bonusDialogVisible = ref(false)
const bonusLoading = ref(false)
const bonusFinance = ref({})
const bonusOrder = ref({ orderNo: '', memberAccount: '' })
const currentOrder = ref(null)
const currentAfterSale = ref(null)
const shipForm = ref({ deliveryCompany: '', deliveryNo: '', shipmentQuantity: 1 })
const auditForm = ref({ status: 1, auditRemark: '', auditUserId: 1, auditUserName: 'admin' })
const orderSearchFeedback = ref('')
const orderEmptyText = ref('暂无订单记录')
const { markSearchApplied: markOrderSearchApplied } = useSearchAutoRestore(
  () => query.value.keyword,
  () => {
    pagination.value.page = 1
    fetchOrders()
  },
)
const money = (value) => Number(value || 0).toFixed(2)
const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
const payoutExceeded = (orderAmount, bonusAmount) => Number(bonusAmount || 0) > Number(orderAmount || 0)
const bonusTypeName = (row) => row.bonusType === 'DIRECT_REWARD'
  ? '直推奖'
  : row.bonusType === 'DIRECTOR_SHARE' ? '董事团队分红' : '历史奖金'
const afterSaleStatus = (status) => ({ 0: '待审核', 1: '已通过', 2: '已拒绝' }[status] || '处理中')
const afterSaleTag = (status) => ({ 0: 'warning', 1: 'success', 2: 'info' }[status] || 'info')
const hasPendingAfterSale = (row) => (row?.afterSales || []).some((item) => item.status === 0)
const hasApprovedRefund = (row) => (row?.afterSales || []).some((item) => item.status === 1)
const orderDisplayStatus = (row) => {
  if (hasPendingAfterSale(row)) return '售后中'
  if (hasApprovedRefund(row)) return '已退款'
  return ({ 0: '待付款', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已关闭' }[row?.order?.status] || '处理中')
}
const orderDisplayTag = (row) => {
  if (hasPendingAfterSale(row)) return 'warning'
  if (hasApprovedRefund(row)) return 'danger'
  return ({ 0: 'info', 1: 'warning', 2: 'primary', 3: 'success', 4: 'info' }[row?.order?.status] || 'info')
}
const shipmentRows = (row) => {
  if (row?.shipments?.length) return row.shipments
  if (row?.order?.deliveryNo) {
    return [{
      deliveryCompany: row.order.deliveryCompany,
      deliveryNo: row.order.deliveryNo,
      shipmentQuantity: (row.items || []).reduce((sum, item) => sum + Number(item?.quantity || 0), 0),
      deliveryTime: row.order.deliveryTime,
    }]
  }
  return []
}
const orderedQuantity = (row) => (row?.items || []).reduce((sum, item) => sum + Number(item?.quantity || 0), 0)
const shippedQuantity = (row) => shipmentRows(row).reduce((sum, item) => sum + Number(item?.shipmentQuantity || 0), 0)
const remainingShipmentQuantity = (row) => Math.max(0, orderedQuantity(row) - shippedQuantity(row))
const canShipOrder = (row) => !hasPendingAfterSale(row)
  && [1, 2].includes(row?.order?.status)
  && remainingShipmentQuantity(row) > 0

const fetchOrders = async () => {
  const validation = validateSearchKeyword(query.value.keyword, { label: '订单关键词' })
  if (!validation.valid) {
    orders.value = []
    pagination.value.total = 0
    orderSearchFeedback.value = validation.message
    orderEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  query.value.keyword = validation.keyword
  markOrderSearchApplied(validation.keyword)
  orderSearchFeedback.value = ''
  orderEmptyText.value = validation.keyword
    ? `未找到与“${validation.keyword}”匹配的订单`
    : '暂无订单记录'
  orderLoading.value = true
  try {
    const res = await listShopOrders({
      ...query.value,
      pageNum: pagination.value.page,
      pageSize: pagination.value.size,
    })
    orders.value = res.data?.list || []
    pagination.value.total = res.data?.total || 0
  } finally {
    orderLoading.value = false
  }
}

const handleOrderSearch = () => {
  pagination.value.page = 1
  fetchOrders()
}

const changeOrderState = (orderState) => {
  if (query.value.orderState === orderState) return
  query.value.orderState = orderState
  pagination.value.page = 1
  fetchOrders()
}

const resetOrderQuery = () => {
  query.value.keyword = ''
  pagination.value.page = 1
  fetchOrders()
}

const downloadBlobResponse = (response, fallbackName) => {
  const blob = response.data instanceof Blob ? response.data : new Blob([response.data])
  const disposition = response.headers?.['content-disposition'] || ''
  const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  let filename = fallbackName
  if (encodedName) {
    try { filename = decodeURIComponent(encodedName) } catch (e) { filename = fallbackName }
  }
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

const handleExportOrders = async () => {
  exportLoading.value = true
  try {
    const response = await exportShopOrders(query.value)
    downloadBlobResponse(response, '商城订单.xlsx')
    ElMessage.success('订单表格已导出')
  } finally {
    exportLoading.value = false
  }
}

const handleDownloadShipmentTemplate = async () => {
  templateLoading.value = true
  try {
    const response = await downloadOrderShipmentTemplate({ keyword: query.value.keyword })
    downloadBlobResponse(response, '待发货订单物流回填.xlsx')
    ElMessage.success('发货表已下载；拆单可复制订单行，合箱可共用物流单号')
  } finally {
    templateLoading.value = false
  }
}

const handleShipmentImport = async ({ file }) => {
  try {
    await ElMessageBox.confirm(
      `确认导入“${file.name}”吗？系统只读取订单号、物流公司、物流单号和发货数量。`,
      '导入物流并发货',
      { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '取消' },
    )
  } catch (action) {
    return
  }
  importLoading.value = true
  try {
    const response = await importOrderShipments(file)
    shipmentResult.value = response.data || {}
    shipmentResultVisible.value = true
    if (Number(shipmentResult.value.shippedCount || 0) > 0) await fetchOrders()
  } finally {
    importLoading.value = false
  }
}

const openShip = (row) => {
  currentOrder.value = row
  shipForm.value = {
    deliveryCompany: '',
    deliveryNo: '',
    shipmentQuantity: remainingShipmentQuantity(row),
  }
  shipDialogVisible.value = true
}

const openBonusFlows = async (orderId, orderNo, memberAccount) => {
  if (!orderId) return ElMessage.warning('订单信息不完整，无法查询奖金去向')
  bonusOrder.value = { orderNo, memberAccount }
  bonusFinance.value = {}
  bonusDialogVisible.value = true
  bonusLoading.value = true
  try {
    const res = await getOrderFinance(orderId)
    bonusFinance.value = res.data || {}
  } finally {
    bonusLoading.value = false
  }
}

const submitShip = async () => {
  if (!shipForm.value.deliveryCompany || !shipForm.value.deliveryNo || !Number.isInteger(shipForm.value.shipmentQuantity) || shipForm.value.shipmentQuantity <= 0) {
    ElMessage.warning('请填写物流公司、物流单号和正确的发货数量')
    return
  }
  await shipShopOrder(currentOrder.value.order.id, shipForm.value)
  ElMessage.success(currentOrder.value.order.status === 2 ? '物流包裹已添加' : '发货成功')
  shipDialogVisible.value = false
  await fetchOrders()
}

const openAudit = (row, status) => {
  currentAfterSale.value = row
  auditForm.value = {
    status,
    auditRemark: '',
    auditUserId: 1,
    auditUserName: 'admin',
  }
  auditDialogVisible.value = true
}

const submitAudit = async () => {
  await auditShopAfterSale(currentAfterSale.value.id, auditForm.value)
  ElMessage.success('审核完成')
  auditDialogVisible.value = false
  await fetchOrders()
}

onMounted(fetchOrders)
</script>

<style scoped>
.order-state-nav {
  display: flex;
  gap: 28px;
  margin-bottom: 18px;
  padding: 0 18px;
  overflow-x: auto;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  scrollbar-width: none;
}

.order-state-nav::-webkit-scrollbar {
  display: none;
}

.order-state-nav button {
  position: relative;
  flex: 0 0 auto;
  padding: 15px 3px 14px;
  color: #606266;
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 15px;
}

.order-state-nav button::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  background: transparent;
  border-radius: 3px 3px 0 0;
  content: '';
}

.order-state-nav button:hover,
.order-state-nav button.active {
  color: #409eff;
  font-weight: 600;
}

.order-state-nav button.active::after {
  background: #409eff;
}

.order-search-panel {
  margin-bottom: 18px;
}

.order-search-panel :deep(.el-input) {
  width: 320px;
}

.order-item-cell-list {
  display: grid;
}

.order-table :deep(.el-table__cell) {
  padding: 10px 6px;
}

.order-table :deep(.cell) {
  padding: 0 4px;
}

.order-item-cell {
  display: flex;
  min-height: 38px;
  align-items: center;
  padding: 7px 0;
}

.product-name-item strong {
  color: #303133;
  line-height: 1.45;
}

.product-spec-item {
  color: #909399;
  font-size: 13px;
  line-height: 1.45;
}

.product-quantity-item {
  justify-content: center;
  color: #303133;
  font-weight: 600;
}

.order-item-cell + .order-item-cell {
  border-top: 1px dashed #ebeef5;
}

.inline-after-sales {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.inline-after-sale-item {
  padding: 8px;
  color: #606266;
  background: #fff7ed;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.7;
}

.inline-after-sale-item span {
  margin-left: 6px;
}

.inline-after-sale-item small {
  display: block;
  color: #a65a16;
}

.inline-after-sale-actions {
  display: flex;
  gap: 8px;
}

.order-no {
  font-weight: 600;
  color: #303133;
}

.sub {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}

.danger {
  color: #f56c6c;
  font-weight: 600;
}

.bonus-summary {
  margin-bottom: 16px;
}

.bonus-alert {
  margin-bottom: 16px;
}

.order-batch-actions :deep(.el-form-item__content) {
  display: flex;
  gap: 8px;
}

.order-batch-actions .el-button + .el-button {
  margin-left: 0;
}

.shipping-workflow-tip {
  margin: -2px 0 16px;
}

.search-feedback {
  margin-bottom: 16px;
}

.shipment-result-summary {
  margin-top: 16px;
}

.shipment-error-table {
  margin-top: 16px;
}

.shipment-list > div + div {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #dcdfe6;
}

.existing-shipments {
  width: 100%;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f5f7fa;
  color: #606266;
  line-height: 1.8;
}

.remaining-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 13px;
}
</style>
