<template>
  <div class="page-container">
    <div class="search-container">
      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="账号 / 昵称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="handleSearch">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
        <el-form-item class="toolbar-right">
          <el-button type="primary" @click="openEditor()">新增账号</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <el-table :data="users" v-loading="loading" :empty-text="tableEmptyText" style="width: 100%">
      <el-table-column prop="id" label="管理员ID" width="100" />
      <el-table-column prop="username" label="登录账号" width="150" />
      <el-table-column prop="nickname" label="管理员名称" width="160" />
      <el-table-column prop="roleCode" label="管理员角色" width="140" />
      <el-table-column label="功能权限" min-width="260">
        <template #default="{ row }">
          <el-tag
            v-for="item in splitPermissions(row.permissions)"
            :key="item"
            class="permission-tag"
            size="small"
          >
            {{ permissionLabel(item) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账号状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="登录锁定" width="100"><template #default="{ row }"><el-tag :type="row.lockTime ? 'danger' : 'success'">{{ row.lockTime ? '已锁定' : '正常' }}</el-tag></template></el-table-column>
      <el-table-column prop="lastLoginTime" label="最近登录时间" width="170" :formatter="formatDateTimeCell" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openEditor(row)">编辑</el-button>
          <el-button type="warning" link @click="openPassword(row)">重置密码</el-button>
          <el-button v-if="row.lockTime" type="success" link @click="unlock(row)">解除锁定</el-button>
          <el-button
            :type="row.status === 1 ? 'danger' : 'success'"
            link
            :disabled="isCurrentUser(row)"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
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
      @current-change="fetchUsers"
      @size-change="fetchUsers"
    />

    <el-dialog v-model="editorVisible" :title="form.id ? '编辑账号' : '新增账号'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录账号" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="8 至 64 位" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="后台显示名称" />
        </el-form-item>
        <el-form-item label="角色标识" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="例如 FINANCE / OPERATOR" />
        </el-form-item>
        <el-form-item label="权限" prop="permissions">
          <el-checkbox-group v-model="form.permissions" @change="handlePermissionChange">
            <el-checkbox
              v-for="item in permissionOptions"
              :key="item.value"
              :label="item.value"
            >
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="重置密码" width="420px">
      <el-form :model="passwordForm" label-width="90px">
        <el-form-item label="账号">
          <el-input :model-value="currentUser.username" disabled />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.password" type="password" show-password placeholder="8 至 64 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listAdminUsers,
  listPermissionOptions,
  saveAdminUser,
  updateAdminPassword,
  updateAdminStatus,
  unlockAdminUser,
  updateAdminUser,
} from '@/api/adminUser'
import { useAppStore } from '@/store'
import { validateSearchKeyword } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { formatDateTimeCell } from '@/utils/dateTime'

const store = useAppStore()
const query = reactive({ keyword: '', status: undefined })
const users = ref([])
const permissionOptions = ref([])
const loading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
const passwordVisible = ref(false)
const formRef = ref(null)
const pagination = reactive({ page: 1, size: 10, total: 0 })
const currentUser = ref({})
const passwordForm = reactive({ password: '' })
const searchFeedback = ref('')
const tableEmptyText = ref('暂无后台管理员账号')
const { markSearchApplied: markKeywordSearchApplied } = useSearchAutoRestore(
  () => query.keyword,
  () => {
    pagination.page = 1
    fetchUsers()
  },
)

const form = reactive({
  id: null,
  username: '',
  password: '',
  nickname: '',
  roleCode: 'OPERATOR',
  permissions: ['admin:read'],
  status: 1,
})

const rules = computed(() => ({
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: form.id ? [] : [{ required: true, min: 8, max: 64, message: '请输入8至64位密码', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色标识', trigger: 'blur' }],
  permissions: [{ type: 'array', required: true, message: '请选择权限', trigger: 'change' }],
}))

const fetchUsers = async () => {
  const validation = validateSearchKeyword(query.keyword, { label: '管理员关键词', maxLength: 64 })
  if (!validation.valid) {
    users.value = []
    pagination.total = 0
    searchFeedback.value = validation.message
    tableEmptyText.value = '请修改搜索内容后重新查询'
    return
  }
  query.keyword = validation.keyword
  markKeywordSearchApplied(validation.keyword)
  searchFeedback.value = ''
  tableEmptyText.value = validation.keyword
    ? `未找到与“${validation.keyword}”匹配的管理员账号`
    : '暂无后台管理员账号'
  loading.value = true
  try {
    const res = await listAdminUsers({
      keyword: query.keyword || undefined,
      status: query.status,
      pageNum: pagination.page,
      pageSize: pagination.size,
    })
    users.value = res.data?.list || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const fetchPermissionOptions = async () => {
  const res = await listPermissionOptions()
  permissionOptions.value = res.data || []
}

const handleSearch = () => {
  pagination.page = 1
  fetchUsers()
}

const resetQuery = () => {
  query.keyword = ''
  query.status = undefined
  pagination.page = 1
  fetchUsers()
}

const openEditor = (row) => {
  Object.assign(form, {
    id: row?.id || null,
    username: row?.username || '',
    password: '',
    nickname: row?.nickname || '',
    roleCode: row?.roleCode || 'OPERATOR',
    permissions: splitPermissions(row?.permissions || 'admin:read'),
    status: row?.status ?? 1,
  })
  editorVisible.value = true
}

const submitUser = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      username: form.username,
      password: form.password || undefined,
      nickname: form.nickname,
      roleCode: form.roleCode,
      permissions: normalizePermissions(form.permissions),
      status: form.status,
    }
    if (form.id) {
      await updateAdminUser(form.id, payload)
    } else {
      await saveAdminUser(payload)
    }
    ElMessage.success('保存成功')
    editorVisible.value = false
    fetchUsers()
  } finally {
    saving.value = false
  }
}

const openPassword = (row) => {
  currentUser.value = row
  passwordForm.password = ''
  passwordVisible.value = true
}

const submitPassword = async () => {
  if (!passwordForm.password || passwordForm.password.length < 8 || passwordForm.password.length > 64) {
    ElMessage.warning('请输入8至64位密码')
    return
  }
  saving.value = true
  try {
    await updateAdminPassword(currentUser.value.id, { password: passwordForm.password })
    ElMessage.success('密码已重置')
    passwordVisible.value = false
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row) => {
  const nextStatus = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确认${nextStatus === 1 ? '启用' : '禁用'}账号 ${row.username}？`, '确认操作', {
    type: 'warning',
  })
  await updateAdminStatus(row.id, nextStatus)
  ElMessage.success('状态已更新')
  fetchUsers()
}

const unlock = async (row) => {
  await ElMessageBox.confirm(`确认解除账号 ${row.username} 的密码错误锁定？`, '解除锁定', { type: 'warning' })
  await unlockAdminUser(row.id)
  ElMessage.success('账号已解除锁定')
  fetchUsers()
}

const handlePermissionChange = () => {
  form.permissions = normalizePermissions(form.permissions)
}

const normalizePermissions = (items) => {
  if (!items || items.length === 0) {
    return []
  }
  if (items.includes('*')) {
    return ['*']
  }
  return [...new Set(items)]
}

const splitPermissions = (permissions) => {
  if (!permissions) {
    return []
  }
  return permissions.split(',').map((item) => item.trim()).filter(Boolean)
}

const permissionLabel = (permission) => {
  const option = permissionOptions.value.find((item) => item.value === permission)
  return option?.label || permission
}

const isCurrentUser = (row) => String(row.id) === String(store.userInfo.id)

onMounted(async () => {
  await fetchPermissionOptions()
  fetchUsers()
})
</script>

<style scoped>
.toolbar-right {
  float: right;
}

.permission-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}
.search-feedback { margin-bottom: 16px; }

:deep(.el-checkbox-group) {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 1fr));
  gap: 6px 12px;
}
</style>
