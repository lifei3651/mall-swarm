import axios from 'axios'
import { ElMessageBox } from 'element-plus'
import { encryptSensitiveRequest } from '@/utils/payloadEncryption'

const rawClient = axios.create({ baseURL: '/api', timeout: 15000, withCredentials: true })

export const requestAdminStepUpToken = async (requestConfig) => {
  const path = String(requestConfig.url || '').split('?', 1)[0]
  const method = String(requestConfig.method || 'get').toUpperCase()
  let password = ''
  try {
    const result = await ElMessageBox.prompt(
      requestConfig.adminStepUp?.message || '该操作影响账号、资金或业务关系，请输入当前管理员密码后继续。',
      requestConfig.adminStepUp?.title || '管理员二次验证',
      {
        confirmButtonText: '验证并继续',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '请输入当前管理员密码',
        inputValidator: (value) => (String(value || '').length >= 8 ? true : '请输入至少8位的管理员密码'),
        closeOnClickModal: false,
        closeOnPressEscape: true,
      },
    )
    password = result.value
    const encryptedConfig = await encryptSensitiveRequest({
      url: '/distribution/admin-auth/step-up',
      method: 'post',
      data: { password, method, path },
      headers: { 'X-Admin-Client': 'admin-web' },
    })
    const response = await rawClient.request(encryptedConfig)
    if (Number(response.data?.code) !== 200 || !response.data?.data?.token) {
      throw new Error(response.data?.message || '管理员二次验证失败')
    }
    return response.data.data.token
  } catch (error) {
    if (error === 'cancel' || error === 'close' || error?.toString?.().includes('cancel')) {
      const cancelled = new Error('已取消敏感操作')
      cancelled.isAdminStepUpCancelled = true
      throw cancelled
    }
    throw error
  } finally {
    password = ''
  }
}
