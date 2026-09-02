<template>
  <main class="password-page">
    <el-card class="password-card" shadow="never">
      <template #header>
        <div>
          <h1>修改后台初始密码</h1>
          <p>新建账号或密码被管理员重置后，必须先设置只有您本人知道的新密码。</p>
        </div>
      </template>
      <el-alert
        title="新密码需为10至64位，并包含大小写字母、数字、符号中的至少三类。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="password-form">
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input v-model="form.currentPassword" type="password" show-password maxlength="64" autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password maxlength="64" autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password maxlength="64" autocomplete="new-password" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="saving" class="submit" @click="submit">保存新密码并重新登录</el-button>
      </el-form>
    </el-card>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changeOwnPassword } from '@/api/auth'
import { useAppStore } from '@/store'
import { adminPortalForAccount, adminPortalLoginPath, saveAdminPortal } from '@/utils/adminPortal'

const router = useRouter()
const store = useAppStore()
const formRef = ref()
const saving = ref(false)
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const strongPassword = (value) => {
  const groups = [/[a-z]/.test(value), /[A-Z]/.test(value), /\d/.test(value), /[^A-Za-z0-9]/.test(value)]
  return groups.filter(Boolean).length >= 3
}
const rules = {
  currentPassword: [{ required: true, min: 8, max: 64, message: '请输入当前后台密码', trigger: 'blur' }],
  newPassword: [
    { required: true, min: 10, max: 64, message: '新密码需要10至64位', trigger: 'blur' },
    { validator: (_, value, done) => strongPassword(value || '') ? done() : done(new Error('请至少使用三类字符')), trigger: 'blur' },
  ],
  confirmPassword: [{ validator: (_, value, done) => value === form.newPassword ? done() : done(new Error('两次输入的新密码不一致')), trigger: 'blur' }],
}

const clearPasswords = () => {
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
}

const submit = async () => {
  if (saving.value) return
  await formRef.value?.validate()
  saving.value = true
  try {
    const portal = saveAdminPortal(adminPortalForAccount(store.userInfo))
    await changeOwnPassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
    store.logout()
    clearPasswords()
    ElMessage.success('后台密码已更新，请使用新密码重新登录')
    await router.replace(adminPortalLoginPath(portal))
  } finally {
    clearPasswords()
    saving.value = false
  }
}
</script>

<style scoped>
.password-page{min-height:100vh;display:grid;place-items:center;padding:32px;background:#eef3f9}.password-card{width:min(520px,100%);border-radius:16px}.password-card h1{margin:0;color:#1f2937;font-size:24px}.password-card p{margin:8px 0 0;color:#667085;line-height:1.65}.password-form{margin-top:22px}.submit{width:100%;margin-top:8px}
</style>
