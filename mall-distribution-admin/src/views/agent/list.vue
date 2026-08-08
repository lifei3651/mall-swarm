<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="search-container">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="会员查询">
          <el-input v-model="searchForm.keyword" placeholder="登录账号/手机号/名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="会员卡级">
          <el-select v-model="searchForm.agentLevel" placeholder="请选择" clearable @change="handleSearch">
            <el-option label="会员" :value="1" />
            <el-option label="VIP会员" :value="2" />
            <el-option label="店铺" :value="3" />
            <el-option label="代理" :value="4" />
            <el-option label="一星董事" :value="5" />
            <el-option label="二星董事" :value="6" />
            <el-option label="三星董事" :value="7" />
            <el-option label="合伙人" :value="8" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable @change="handleSearch">
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
            <el-option label="冻结" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <!-- 操作栏 -->
    <div class="toolbar">
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        后台新增商城账号/会员
      </el-button>
      <el-button type="success" @click="handleExport">
        <el-icon><Download /></el-icon>
        导出
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" :empty-text="tableEmptyText" style="width: 100%">
      <el-table-column prop="memberAccount" label="登录账号" width="145" />
      <el-table-column prop="agentName" label="会员名称" width="120" />
      <el-table-column prop="phone" label="手机号" width="120" />
      <el-table-column prop="agentLevelName" label="会员卡级" width="100">
        <template #default="{ row }">
          <el-tag :type="getLevelType(row.agentLevel)">{{ row.agentLevelName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="parentName" label="直属上级" width="120" />
      <el-table-column prop="levelDepth" label="关系层级" width="90" />
      <el-table-column prop="inviteCode" label="邀请码" width="120" />
      <el-table-column prop="statusName" label="会员状态" width="90">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sourceTypeName" label="来源" width="100" />
      <el-table-column prop="createTime" label="注册时间" width="160" :formatter="formatDateTimeCell" />
      <el-table-column label="操作" fixed="right" width="300">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button type="success" link @click="handleChildren(row)">下级</el-button>
          <el-button type="primary" link @click="openLevelAdjust(row)">调级</el-button>
          <el-tooltip
            v-if="canApplyLineChange"
            content="该会员有待移线处理申请，暂不可再进行移线操作"
            placement="top"
            :disabled="!row.hasPendingLineChange"
          >
            <span class="action-tooltip-trigger">
              <el-button type="warning" link :disabled="row.hasPendingLineChange" @click="handleSwitchLine(row)">移线</el-button>
            </span>
          </el-tooltip>
          <el-button
            :type="row.status === 1 ? 'danger' : 'success'"
            link
            @click="handleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 切线对话框 -->
    <el-dialog v-model="switchLineVisible" title="会员移线" width="680px">
      <el-alert
        title="移线规则与数据处理说明"
        type="warning"
        :closable="false"
        style="margin-bottom: 20px"
      >
        <template #default>
          <p><strong>1. 上级关系：</strong>本人及完整下级团队整体移到新直属上级名下；本人的直属上级改变，下级成员之间原有的直属邀请关系保持不变。</p>
          <p><strong>2. 历史数据：</strong>移线前已有订单、业绩、累计件数、已结算或待结算奖金及余额流水保留原归属，不转移、不重算。</p>
          <p><strong>3. 已支付订单：</strong>移线前已支付的订单继续按支付时冻结的旧关系处理，包括确认收货、7天结算及退款。</p>
          <p><strong>4. 新产生数据：</strong>移线后支付的订单按新关系计算业绩、累计件数和奖金；移线前创建但未支付的订单，之后支付时也使用新关系。</p>
          <p><strong>5. 操作结果：</strong>提交后立即生效并保存操作人、原因和前后快照，不再经过第二人审批，且不能自动撤销。</p>
        </template>
      </el-alert>
      <el-form :model="switchLineForm" label-width="100px">
        <el-form-item label="移线会员">
          <el-input :value="switchLineForm.agentName" disabled />
        </el-form-item>
        <el-form-item label="当前上级">
          <el-input :value="switchLineForm.parentName" disabled />
        </el-form-item>
        <el-form-item label="新直属上级" required>
          <el-select
            v-model="switchLineForm.newParentAgentId"
            filterable
            remote
            :remote-method="searchAgents"
            placeholder="请输入登录账号、手机号或名称搜索"
          >
            <el-option
              v-for="item in agentOptions"
              :key="item.id"
              :label="`${item.agentName}（${item.memberAccount || '-'}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="切线原因">
          <el-input
            v-model="switchLineForm.reason"
            type="textarea"
            placeholder="请输入切线原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="switchLineVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSwitchLine" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="levelVisible" title="会员手工调级" width="520px">
      <el-alert title="无需审批，提交后立即生效并写入变更日志。历史订单、历史业绩和历史奖金不重算，新级别只用于之后产生的新订单。" type="warning" :closable="false" show-icon />
      <el-form :model="levelForm" label-width="100px" class="level-form">
        <el-form-item label="会员"><el-input :model-value="`${levelForm.agentName}（${levelForm.memberAccount || '-'}）`" disabled /></el-form-item>
        <el-form-item label="当前卡级"><el-input :model-value="levelName(levelForm.oldLevel)" disabled /></el-form-item>
        <el-form-item label="调整为" required><el-select v-model="levelForm.level" style="width:100%"><el-option v-for="item in levels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="调整原因" required><el-input v-model="levelForm.reason" type="textarea" :rows="3" maxlength="300" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="levelVisible=false">取消</el-button><el-button type="primary" :loading="levelLoading" @click="submitLevelAdjust">直接生效</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/store'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download } from '@element-plus/icons-vue'
import { adjustAgentLevel, exportAgents, listAgents, switchLine, updateAgentStatus } from '@/api/agent'
import { memberSearchEmptyText, validateMemberSearch } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { formatDateTimeCell } from '@/utils/dateTime'

const router = useRouter()
const store = useAppStore()
const canApplyLineChange = store.hasPermission('line-change:apply')
const loading = ref(false)
const submitLoading = ref(false)
const switchLineVisible = ref(false)
const levelVisible = ref(false)
const levelLoading = ref(false)
const levels = [
  { value: 1, label: '会员' }, { value: 2, label: 'VIP会员' }, { value: 3, label: '店铺' }, { value: 4, label: '代理' },
  { value: 5, label: '一星董事' }, { value: 6, label: '二星董事' }, { value: 7, label: '三星董事' }, { value: 8, label: '合伙人' },
]
const levelForm = ref({ id: null, agentName: '', memberAccount: '', oldLevel: 1, level: 1, reason: '' })
const searchFeedback = ref('')
const tableEmptyText = ref('暂无已进入奖金体系的会员')

// 搜索表单
const searchForm = ref({
  keyword: '',
  agentLevel: null,
  status: null,
})

// 分页
const pagination = ref({
  page: 1,
  size: 10,
  total: 0,
})

// 表格数据
const tableData = ref([])
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => searchForm.value.keyword,
  () => handleSearch(),
)

// 切线表单
const switchLineForm = ref({
  agentId: null,
  agentName: '',
  parentId: null,
  parentName: '',
  newParentAgentId: null,
  reason: '',
})

// 代理选项
const agentOptions = ref([])

// 获取等级类型
const getLevelType = (level) => {
  const map = { 1: 'info', 2: '', 3: 'warning', 4: 'danger' }
  return map[level] || 'info'
}

// 获取状态类型
const getStatusType = (status) => {
  const map = { 0: 'danger', 1: 'success', 2: 'warning' }
  return map[status] || 'info'
}
const levelName = (level) => levels.find((item) => item.value === Number(level))?.label || '-'

const openLevelAdjust = (row) => {
  levelForm.value = { id: row.id, agentName: row.agentName, memberAccount: row.memberAccount, oldLevel: row.agentLevel, level: row.agentLevel, reason: '' }
  levelVisible.value = true
}

const submitLevelAdjust = async () => {
  if (levelForm.value.level === levelForm.value.oldLevel) return ElMessage.warning('请选择不同的目标卡级')
  if (!levelForm.value.reason.trim()) return ElMessage.warning('请填写调整原因')
  levelLoading.value = true
  try {
    await adjustAgentLevel(levelForm.value.id, { level: levelForm.value.level, reason: levelForm.value.reason.trim() })
    ElMessage.success('卡级已直接调整，变更日志已记录')
    levelVisible.value = false
    fetchData()
  } finally { levelLoading.value = false }
}

// 搜索
const handleSearch = () => {
  const validation = validateMemberSearch(searchForm.value.keyword)
  if (!validation.valid) {
    tableData.value = []
    pagination.value.total = 0
    searchFeedback.value = validation.message
    tableEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  searchForm.value.keyword = validation.keyword
  markKeywordSearchApplied(validation.keyword)
  searchFeedback.value = ''
  tableEmptyText.value = validation.keyword
    ? memberSearchEmptyText(validation.keyword, '会员关系记录')
    : '暂无已进入奖金体系的会员'
  pagination.value.page = 1
  fetchData()
}

// 重置
const handleReset = () => {
  searchForm.value = {
    keyword: '',
    agentLevel: null,
    status: null,
  }
  handleSearch()
}

// 添加代理
const handleAdd = () => {
  router.push('/members/list?create=1')
}

// 导出
const handleExport = async () => {
  const response = await exportAgents({
    keyword: searchForm.value.keyword || undefined,
    status: searchForm.value.status,
    agentLevel: searchForm.value.agentLevel,
  })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = `会员关系_${new Date().toISOString().slice(0, 10)}.xlsx`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
  ElMessage.success('导出完成')
}

// 详情
const handleDetail = (row) => {
  router.push(`/agent/detail/${row.id}`)
}

// 下级代理
const handleChildren = (row) => {
  router.push(`/agent/tree?memberAccount=${encodeURIComponent(row.memberAccount || '')}`)
}

// 切线
const handleSwitchLine = (row) => {
  if (row.hasPendingLineChange) {
    ElMessage.warning('该会员有待移线处理申请，暂不可再进行移线操作')
    return
  }
  switchLineForm.value = {
    agentId: row.id,
    agentName: row.agentName,
    parentId: row.parentId,
    parentName: row.parentName,
    newParentAgentId: null,
    reason: '',
  }
  switchLineVisible.value = true
}

// 搜索代理
const searchAgents = async (query) => {
  if (!query) {
    agentOptions.value = []
    return
  }
  const res = await listAgents({ keyword: query, status: 1, pageNum: 1, pageSize: 20 })
  agentOptions.value = (res.data?.list || []).filter((item) => item.id !== switchLineForm.value.agentId)
}

// 提交切线
const submitSwitchLine = async () => {
  if (!switchLineForm.value.newParentAgentId) {
    ElMessage.warning('请选择新的直属上级会员')
    return
  }
  if (!switchLineForm.value.reason) {
    ElMessage.warning('请输入切线原因')
    return
  }

  const confirmMessage = [
    '确定要立即执行移线吗？',
    `移线会员：${switchLineForm.value.agentName || '-'}`,
    `当前上级：${switchLineForm.value.parentName || '-'}`,
    `新上级：${agentOptions.value.find((item) => item.id === switchLineForm.value.newParentAgentId)?.agentName || '-'}（${agentOptions.value.find((item) => item.id === switchLineForm.value.newParentAgentId)?.memberAccount || '-'}）`,
    '',
    '注意事项：',
    '1. 本人及完整下级团队立即移到新直属上级名下，下级之间的直属关系不变',
    '2. 历史数据及移线前已支付订单保持原归属，不转移、不重算',
    '3. 移线后支付的订单按新关系链计算，原上级不再承接新数据',
    '4. 系统保留操作人、原因和前后快照，操作不能自动撤销',
  ].join('\n')

  try {
    await ElMessageBox.confirm(confirmMessage, '切线确认', {
      type: 'warning',
      confirmButtonText: '确定切线',
      cancelButtonText: '取消',
    })
    submitLoading.value = true
    await switchLine({
      agentId: switchLineForm.value.agentId,
      newParentAgentId: switchLineForm.value.newParentAgentId,
      reason: switchLineForm.value.reason,
    })
    ElMessage.success('移线已执行并记录操作日志')
    switchLineVisible.value = false
    fetchData()
  } catch (e) {
    // 取消
  } finally {
    submitLoading.value = false
  }
}

// 切换状态
const handleStatus = async (row) => {
  const action = row.status === 1 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}该会员吗？`, '提示', { type: 'warning' })
    await updateAgentStatus(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success(`${action}成功`)
    fetchData()
  } catch (e) {
    // 取消
  }
}

// 分页大小变化
const handleSizeChange = (size) => {
  pagination.value.size = size
  fetchData()
}

// 页码变化
const handleCurrentChange = (page) => {
  pagination.value.page = page
  fetchData()
}

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listAgents({
      keyword: searchForm.value.keyword || undefined,
      status: searchForm.value.status,
      agentLevel: searchForm.value.agentLevel,
      pageNum: pagination.value.page,
      pageSize: pagination.value.size,
    })
    const list = res.data?.list || []
    tableData.value = list
    pagination.value.total = res.data?.total || list.length
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.toolbar {
  margin-bottom: 20px;
}
.level-form { margin-top: 20px; }
.action-tooltip-trigger { display: inline-flex; }
.search-feedback { margin-bottom: 16px; }
</style>
