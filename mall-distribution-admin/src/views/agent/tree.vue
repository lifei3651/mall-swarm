<template>
  <div class="page-container">
    <el-page-header @back="handleBack">
      <template #content>
        <span>会员关系树</span>
      </template>
    </el-page-header>

    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>已进入奖金体系的会员层级关系</span>
          <div class="tree-toolbar">
            <el-select v-model="viewMode" size="small" style="width: 140px" aria-label="关系树着色方式">
              <el-option label="按结构查看" value="structure" />
              <el-option label="按当月业绩着色" value="performance" />
              <el-option label="按累计奖金着色" value="commission" />
            </el-select>
            <el-input
              v-model="searchMemberKey"
              placeholder="登录账号或手机号；留空显示全部根节点"
              clearable
              style="width: 250px; margin-right: 10px"
              size="small"
              @keyup.enter="() => fetchTree()"
            />
            <el-button type="primary" size="small" @click="() => fetchTree()">查询</el-button>
          </div>
        </div>
      </template>

      <el-alert
        title="只有完成首笔有效订单或由后台直接设定级别、已经进入奖金体系的会员才会出现在这里。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-alert
        v-if="searchFeedback"
        :title="searchFeedback"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <div class="tree-container" v-loading="loading">
        <el-tree
          v-if="treeData.length"
          :data="treeData"
          :props="treeProps"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ node, data }">
            <div class="tree-node" :style="nodeStyle(data)">
              <div class="node-info">
                <el-tag :type="getLevelType(data.agentLevel)" size="small">
                  {{ data.agentLevelName }}
                </el-tag>
                <span class="node-name">{{ data.agentName }}</span>
                <span class="node-code">({{ data.memberAccount || '-' }})</span>
                <span class="node-metric">本月业绩 ¥{{ formatMoney(data.currentMonthPerformance) }}</span>
                <span class="node-metric">累计奖金 ¥{{ formatMoney(data.totalCommission) }}</span>
                <span class="node-metric">团队 {{ Number(data.teamMemberCount || 0) }} 人</span>
              </div>
              <div class="node-actions">
                <el-button type="primary" link size="small" @click="handleDetail(data)">
                  详情
                </el-button>
                <el-button type="success" link size="small" @click="handlePerformance(data)">
                  业绩
                </el-button>
                <el-button type="warning" link size="small" @click="handleSwitchLine(data)">
                  移线
                </el-button>
              </div>
            </div>
          </template>
        </el-tree>
        <el-empty v-else-if="!loading" :description="treeEmptyText" />
      </div>
    </el-card>

    <!-- 会员详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="会员详情" size="400px">
      <el-descriptions :column="1" border v-if="currentAgent">
        <el-descriptions-item label="登录账号">{{ currentAgent.memberAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="会员名称">{{ currentAgent.agentName }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ currentAgent.phone }}</el-descriptions-item>
        <el-descriptions-item label="会员卡级">
          <el-tag :type="getLevelType(currentAgent.agentLevel)">
            {{ currentAgent.agentLevelName }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentAgent.status)">
            {{ currentAgent.statusName }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="邀请码">{{ currentAgent.inviteCode }}</el-descriptions-item>
        <el-descriptions-item label="本月团队业绩">¥{{ formatMoney(currentAgent.currentMonthPerformance) }}</el-descriptions-item>
        <el-descriptions-item label="累计奖金">¥{{ formatMoney(currentAgent.totalCommission) }}</el-descriptions-item>
        <el-descriptions-item label="团队人数">{{ Number(currentAgent.teamMemberCount || 0) }} 人</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ formatDateTime(currentAgent.createTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resolveAgent, getAllDescendants, getRootAgents } from '@/api/agent'
import { memberSearchFailureMessage, validateMemberSearch } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { formatDateTime } from '@/utils/dateTime'

const router = useRouter()
const route = useRoute()
// 搜索框展示运营可识别的登录账号；agentId 仅用于兼容旧链接。
const searchMemberKey = ref(route.query.memberAccount || '')
const legacyAgentId = route.query.agentId || ''
const drawerVisible = ref(false)
const currentAgent = ref(null)
const loading = ref(false)
const searchFeedback = ref('')
const treeEmptyText = ref('当前还没有进入奖金体系的会员')
const viewMode = ref('structure')
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => searchMemberKey.value,
  () => fetchTree(),
)

// 树属性
const treeProps = {
  children: 'children',
  label: 'agentName',
}

// 树数据
const treeData = ref([])

const flatNodes = computed(() => {
  const result = []
  const visit = (nodes) => nodes.forEach((node) => { result.push(node); visit(node.children || []) })
  visit(treeData.value)
  return result
})
const maxPerformance = computed(() => Math.max(0, ...flatNodes.value.map((item) => Number(item.currentMonthPerformance || 0))))
const maxCommission = computed(() => Math.max(0, ...flatNodes.value.map((item) => Number(item.totalCommission || 0))))
const formatMoney = (value) => Number(value || 0).toFixed(2)
const nodeStyle = (data) => {
  if (viewMode.value === 'structure') return {}
  const isPerformance = viewMode.value === 'performance'
  const value = Number(isPerformance ? data.currentMonthPerformance : data.totalCommission) || 0
  const max = isPerformance ? maxPerformance.value : maxCommission.value
  const intensity = max > 0 ? 0.07 + (value / max) * 0.2 : 0.04
  return { backgroundColor: isPerformance ? `rgba(103, 194, 58, ${intensity})` : `rgba(230, 162, 60, ${intensity})` }
}

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

// 返回
const handleBack = () => {
  router.back()
}

// 获取树数据
const fetchTree = async (presetRoot = null) => {
  const validation = validateMemberSearch(searchMemberKey.value)
  if (!presetRoot && !validation.valid) {
    treeData.value = []
    searchFeedback.value = validation.message
    treeEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  loading.value = true
  searchFeedback.value = ''
  try {
    let roots = []
    const memberKey = validation.keyword
    searchMemberKey.value = memberKey
    markKeywordSearchApplied(memberKey)
    if (presetRoot) {
      roots = [presetRoot]
    } else if (memberKey) {
      const rootRes = await resolveAgent(memberKey)
      roots = rootRes.data ? [rootRes.data] : []
    } else {
      const rootsRes = await getRootAgents()
      roots = rootsRes.data || []
    }

    treeData.value = await Promise.all(roots.map(async (rootAgent) => {
      const descendantsRes = await getAllDescendants(rootAgent.id)
      const root = { ...rootAgent, children: [] }
      const descendants = (descendantsRes.data?.list || []).map((item) => ({ ...item, children: [] }))
      const map = new Map([[root.id, root], ...descendants.map((item) => [item.id, item])])
      descendants.forEach((item) => {
        const parent = map.get(item.parentId)
        if (parent) parent.children.push(item)
      })
      return root
    }))
    treeEmptyText.value = memberKey
      ? `未找到与“${memberKey}”匹配的会员关系`
      : '当前还没有进入奖金体系的会员'
  } catch (error) {
    treeData.value = []
    const memberKey = validation.keyword
    searchFeedback.value = memberSearchFailureMessage(error, memberKey, '会员关系树')
    treeEmptyText.value = '未能完成关系树查询，请核对搜索内容后重试'
  } finally {
    loading.value = false
  }
}

// 详情
const handleDetail = (data) => {
  currentAgent.value = data
  drawerVisible.value = true
}

// 业绩
const handlePerformance = (data) => {
  router.push(`/performance/overview?memberAccount=${encodeURIComponent(data.memberAccount || '')}`)
}

// 切线
const handleSwitchLine = (data) => {
  router.push('/members/list')
  ElMessage.info('请在会员列表对应会员的操作栏点击“移线”')
}

onMounted(async () => {
  if (!searchMemberKey.value && legacyAgentId) {
    loading.value = true
    try {
      const rootRes = await resolveAgent(String(legacyAgentId))
      const root = rootRes.data
      if (root) {
        searchMemberKey.value = root.memberAccount || ''
        const { agentId, ...otherQuery } = route.query
        await router.replace({ path: route.path, query: { ...otherQuery, memberAccount: root.memberAccount || undefined } })
        await fetchTree(root)
        return
      }
    } catch (error) {
      searchFeedback.value = memberSearchFailureMessage(error, String(legacyAgentId), '会员关系')
    } finally {
      loading.value = false
    }
  }
  await fetchTree()
})
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.tree-toolbar { display: flex; align-items: center; gap: 10px; }

.tree-container {
  min-height: 400px;
}

.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding: 5px 8px;
  border-radius: 6px;
  transition: background-color .18s ease;

  .node-info {
    display: flex;
    align-items: center;
    gap: 10px;

    .node-name {
      font-weight: bold;
    }

    .node-code {
      color: #909399;
      font-size: 12px;
    }

    .node-metric {
      color: #606266;
      font-size: 12px;
      white-space: nowrap;
    }
  }

  .node-actions {
    display: flex;
    gap: 5px;
  }
}

@media (max-width: 900px) {
  .card-header { align-items: flex-start; flex-direction: column; gap: 12px; }
  .tree-toolbar { width: 100%; flex-wrap: wrap; }
  .tree-node { align-items: flex-start; flex-direction: column; gap: 6px; }
  .node-info { flex-wrap: wrap; }
}
</style>
