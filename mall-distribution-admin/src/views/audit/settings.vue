<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <span>业绩查看设置</span>
      </template>
      <el-form label-width="180px">
        <el-form-item label="团队业绩默认可见">
          <el-switch
            v-model="settings.teamPerformanceVisibleAll"
            active-text="所有代理可见"
            inactive-text="仅白名单可见"
            @change="saveVisibility"
          />
        </el-form-item>
        <el-form-item label="直销累计模式">
          <el-switch v-model="settings.directSalesMode" disabled active-text="已启用" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="block">
      <template #header>
        <span>单账号业绩查看白名单</span>
      </template>
      <el-form :inline="true" :model="permissionForm">
        <el-form-item label="登录账号">
          <el-input v-model="permissionForm.memberKey" placeholder="登录账号/手机号" clearable />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="permissionForm.remark" placeholder="备注" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitPermission">添加/更新</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="permissionFeedback" :title="permissionFeedback" type="warning" :closable="false" show-icon class="permission-feedback" />

      <el-table :data="settings.permissions || []" v-loading="loading" style="width: 100%">
        <el-table-column prop="memberAccount" label="登录账号" width="145" />
        <el-table-column prop="agentName" label="会员名称" width="160" />
        <el-table-column prop="enabled" label="查看权限状态" width="130">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">
              {{ row.enabled === 1 ? '可查看' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="danger" link @click="removePermission(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteViewPermission, getAuditSettings, saveViewPermission, updateVisibility } from '@/api/audit'
import { memberSearchFailureMessage, validateMemberSearch } from '@/utils/searchFeedback'

const loading = ref(false)
const settings = ref({
  teamPerformanceVisibleAll: true,
  directSalesMode: true,
  permissions: [],
})
const permissionForm = ref({
  memberKey: '',
  remark: '',
})
const permissionFeedback = ref('')

const fetchSettings = async () => {
  loading.value = true
  try {
    const res = await getAuditSettings()
    settings.value = res.data || settings.value
  } finally {
    loading.value = false
  }
}

const saveVisibility = async () => {
  const res = await updateVisibility({
    teamPerformanceVisibleAll: settings.value.teamPerformanceVisibleAll,
  })
  settings.value = res.data || settings.value
  ElMessage.success('设置已保存')
}

const submitPermission = async () => {
  const validation = validateMemberSearch(permissionForm.value.memberKey, { required: true })
  if (!validation.valid) {
    permissionFeedback.value = validation.message
    return
  }
  permissionFeedback.value = ''
  try {
    await saveViewPermission({
      memberKey: validation.keyword,
      enabled: 1,
      remark: permissionForm.value.remark,
    })
    permissionForm.value = { memberKey: '', remark: '' }
    ElMessage.success('已保存')
    fetchSettings()
  } catch (error) {
    permissionFeedback.value = memberSearchFailureMessage(error, validation.keyword, '业绩查看权限')
  }
}

const removePermission = async (row) => {
  await ElMessageBox.confirm('确定删除这个账号的查看权限吗？', '提示', { type: 'warning' })
  await deleteViewPermission(row.id)
  ElMessage.success('已删除')
  fetchSettings()
}

onMounted(fetchSettings)
</script>

<style scoped>
.block {
  margin-top: 16px;
}
.permission-feedback { margin-bottom:16px; }
</style>
