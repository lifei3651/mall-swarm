import { ref } from 'vue'

const createEmptyForm = () => ({
  phone: '',
  username: '',
  nickname: '',
  password: '',
  smsCode: '',
  inviteCode: '',
})

// 只在当前商城页面生命周期内保留注册草稿，避免查看协议后返回时丢失内容。
// 不写入 localStorage/sessionStorage，密码和验证码不会被持久保存。
const registerForm = ref(createEmptyForm())
const agreeTerms = ref(false)

export const useRegisterDraft = () => {
  const clearRegisterDraft = () => {
    registerForm.value = createEmptyForm()
    agreeTerms.value = false
  }

  return { registerForm, agreeTerms, clearRegisterDraft }
}
