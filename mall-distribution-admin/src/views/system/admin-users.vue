<template>
  <div class="page-container">
    <div class="page-heading">
      <div><h2>{{ isMerchantUser ? '子账号与权限' : '后台账号与权限' }}</h2><p>{{ isMerchantUser ? '由商户负责人按岗位开通商品、订单、售后或财务子账号。' : '统一管理平台账号、商户负责人和各岗位权限。' }}</p></div>
    </div>
    <el-alert title="新账号和重置账号都由系统生成24小时有效的一次性临时密码；首次登录后必须立即设置自己的正式密码。" type="info" :closable="false" show-icon class="credential-alert" />
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
          <el-button type="primary" @click="openEditor()">{{ isMerchantUser ? '新增子账号' : '新增账号' }}</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="searchFeedback" :title="searchFeedback" type="warning" :closable="false" show-icon class="search-feedback" />

    <el-table :data="users" v-loading="loading" :empty-text="tableEmptyText" style="width: 100%">
      <el-table-column prop="id" label="管理员ID" width="100" />
      <el-table-column prop="username" label="登录账号" width="150" />
      <el-table-column prop="nickname" label="管理员名称" width="160" />
      <el-table-column prop="roleCode" label="管理员角色" width="140" />
      <el-table-column prop="merchantName" label="绑定商户" min-width="160"><template #default="{ row }">{{ row.merchantName || '平台后台' }}</template></el-table-column>
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
      <el-table-column label="凭据状态" width="125"><template #default="{ row }"><el-tag v-if="Number(row.mustChangePassword) === 1" type="warning">待首次改密</el-tag><el-tag v-else type="success">正式密码</el-tag><small v-if="row.credentialExpiresAt && Number(row.mustChangePassword) === 1">{{ credentialDeadline(row.credentialExpiresAt) }}</small></template></el-table-column>
      <el-table-column prop="lastLoginTime" label="最近登录时间" width="170" :formatter="formatDateTimeCell" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :disabled="isCurrentUser(row)" @click="openEditor(row)">编辑</el-button>
          <el-button type="warning" link :disabled="isCurrentUser(row)" @click="openPassword(row)">生成临时密码</el-button>
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
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="后台显示名称" />
        </el-form-item>
        <el-form-item v-if="!isMerchantUser" label="角色标识" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="例如 FINANCE / OPERATOR" />
        </el-form-item>
        <el-form-item v-if="!isMerchantUser" label="账号归属">
          <el-select v-model="form.merchantId" clearable filterable placeholder="平台后台账号" style="width:100%" @change="handleMerchantBindingChange">
            <el-option v-for="item in merchantOptions" :key="item.id" :label="`${item.merchantName}（${item.merchantNo}）`" :value="item.id" />
          </el-select>
          <div class="field-help">绑定商户后，该账号只能维护本商户商品、查看本商户货款并申请提现，不能进入平台财务处理、审核、会员或系统设置。</div>
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
        <el-form-item label="当前密码" prop="currentAdminPassword">
          <el-input v-model="form.currentAdminPassword" type="password" show-password placeholder="请输入您当前登录账号的密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="生成一次性临时密码" width="460px">
      <el-alert title="生成后该账号的全部现有会话会立即失效；临时密码24小时内有效，并且只能用于首次登录后修改正式密码。" type="warning" :closable="false" show-icon />
      <el-form :model="passwordForm" label-width="90px">
        <el-form-item label="账号">
          <el-input :model-value="currentUser.username" disabled />
        </el-form-item>
        <el-form-item label="当前密码">
          <el-input v-model="passwordForm.currentAdminPassword" type="password" show-password placeholder="当前登录管理员密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPassword">确认生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="credentialVisible" title="一次性登录凭据" width="500px" :close-on-click-modal="false">
      <el-alert title="临时密码只在本次显示，请立即安全交给账号本人；关闭后平台无法再次查看，只能重新生成。" type="warning" :closable="false" show-icon />
      <div class="credential-card"><span>登录账号</span><strong>{{ issuedCredential.username }}</strong><span>临时密码</span><strong class="temporary-password">{{ issuedCredential.temporaryPassword }}</strong><span>有效期至</span><strong>{{ formatCredentialTime(issuedCredential.expiresAt) }}</strong></div>
      <template #footer><el-button type="primary" @click="copyCredential">复制账号和临时密码</el-button><el-button @click="credentialVisible=false">我已保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  issueAdminTemporaryCredential,
  listAdminUsers,
  listAdminMerchantOptions,
  listPermissionOptions,
  saveAdminUser,
  updateAdminStatus,
  unlockAdminUser,
  updateAdminUser,
} from '@/api/adminUser'
import { useAppStore } from '@/store'
import { validateSearchKeyword } from '@/utils/searchFeedback'
import { useSearchAutoRestore } from '@/utils/searchAutoRestore'
import { formatDateTimeCell } from '@/utils/dateTime'

const store = useAppStore()
const isMerchantUser = computed(() => Boolean(store.userInfo?.merchantId))
const query = reactive({ keyword: '', status: undefined })
const users = ref([])
const permissionOptions = ref([])
const merchantOptions = ref([])
const loading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
const passwordVisible = ref(false)
const credentialVisible = ref(false)
const formRef = ref(null)
const pagination = reactive({ page: 1, size: 10, total: 0 })
const currentUser = ref({})
const passwordForm = reactive({ currentAdminPassword: '' })
const issuedCredential = reactive({ username: '', temporaryPassword: '', expiresAt: '' })
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
  nickname: '',
  roleCode: 'OPERATOR',
  permissions: ['admin:read'],
  merchantId: null,
  status: 1,
  currentAdminPassword: '',
})

const rules = computed(() => ({
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色标识', trigger: 'blur' }],
  permissions: [{ type: 'array', required: true, message: '请选择权限', trigger: 'change' }],
  currentAdminPassword: [{ required: true, min: 8, max: 64, message: '请输入当前管理员登录密码', trigger: 'blur' }],
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
    nickname: row?.nickname || '',
    roleCode: row?.roleCode || 'OPERATOR',
    permissions: splitPermissions(row?.permissions || 'admin:read'),
    merchantId: row?.merchantId || null,
    status: row?.status ?? 1,
    currentAdminPassword: '',
  })
  editorVisible.value = true
}

const submitUser = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      username: form.username,
      nickname: form.nickname,
      roleCode: form.roleCode,
      permissions: normalizePermissions(form.permissions),
      merchantId: form.merchantId || null,
      status: form.status,
      currentAdminPassword: form.currentAdminPassword,
    }
    const response = form.id ? await updateAdminUser(form.id, payload) : await saveAdminUser(payload)
    if (!form.id && response.data?.temporaryPassword) showCredential(response.data)
    ElMessage.success(form.id ? '账号设置已保存' : '账号已创建，请保存一次性登录凭据')
    editorVisible.value = false
    fetchUsers()
  } finally {
    saving.value = false
  }
}

const openPassword = (row) => {
  currentUser.value = row
  passwordForm.currentAdminPassword = ''
  passwordVisible.value = true
}

const submitPassword = async () => {
  if (!passwordForm.currentAdminPassword || passwordForm.currentAdminPassword.length < 8) {
    ElMessage.warning('请输入当前管理员登录密码')
    return
  }
  saving.value = true
  try {
    const response = await issueAdminTemporaryCredential(currentUser.value.id, {
      currentAdminPassword: passwordForm.currentAdminPassword,
    })
    showCredential(response.data || {})
    ElMessage.success('一次性临时密码已生成')
    passwordVisible.value = false
    fetchUsers()
  } finally {
    saving.value = false
  }
}

const showCredential = (value) => {
  Object.assign(issuedCredential, {
    username: value.username || form.username || currentUser.value.username || '',
    temporaryPassword: value.temporaryPassword || '',
    expiresAt: value.expiresAt || value.credentialExpiresAt || '',
  })
  credentialVisible.value = true
}

const formatCredentialTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
const credentialDeadline = (value) => value ? `${String(value).replace('T', ' ').slice(5, 16)} 到期` : '旧账号凭据'
const copyCredential = async () => {
  const text = `登录账号：${issuedCredential.username}\n一次性临时密码：${issuedCredential.temporaryPassword}\n有效期至：${formatCredentialTime(issuedCredential.expiresAt)}\n首次登录后必须立即修改正式密码。`
  await navigator.clipboard.writeText(text)
  ElMessage.success('账号和临时密码已复制')
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

const handleMerchantBindingChange = (merchantId) => {
  if (merchantId) {
    form.roleCode = 'MERCHANT'
    form.permissions = ['admin:read', 'shop:product', 'shop:order', 'shop:aftersale', 'finance:read', 'finance:manage']
  }
}

const normalizePermissions = (items) => {
  if (!items || items.length === 0) {
    return []
  }
  if (items.includes('*')) {
    return ['*']
  }
  const normalized = new Set(items)
  if (normalized.has('shop:aftersale')) normalized.add('shop:order')
  if (normalized.has('finance:manage')) normalized.add('finance:read')
  if (normalized.has('line-change:apply')) normalized.add('distribution:manage')
  return [...normalized]
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
  await Promise.all([fetchPermissionOptions(), listAdminMerchantOptions().then((res) => { merchantOptions.value = res.data || [] })])
  fetchUsers()
})
</script>

<style scoped>
.page-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.page-heading h2{margin:0}.page-heading p{margin:6px 0 0;color:#909399}.credential-alert{margin-bottom:16px}
.toolbar-right {
  float: right;
}

.permission-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}
.search-feedback { margin-bottom: 16px; }
.field-help { width:100%; margin-top:6px; color:#909399; font-size:12px; line-height:18px; }
.credential-card{display:grid;grid-template-columns:100px minmax(0,1fr);gap:14px;margin-top:18px;padding:18px;border:1px solid #e1e8f0;border-radius:10px;background:#f8fafc}.credential-card span{color:#7b8798}.credential-card strong{color:#24344d;word-break:break-all}.credential-card .temporary-password{color:#b54708;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:18px;letter-spacing:.8px}
.el-table small{display:block;margin-top:4px;color:#909399;font-size:10px}

:deep(.el-checkbox-group) {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 1fr));
  gap: 6px 12px;
}
</style>
