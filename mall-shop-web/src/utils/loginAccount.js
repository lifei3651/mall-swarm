export const LOGIN_ACCOUNT_PATTERN = /^[A-Za-z][A-Za-z0-9_]{3,19}$/

export const normalizeLoginAccountInput = (value) => String(value ?? '')
  .replace(/[^A-Za-z0-9_]/g, '')
  .slice(0, 20)

export const validateLoginAccount = (value) => {
  const account = String(value ?? '').trim()
  if (!account) return '请输入登录账号'
  if (account.length < 4) return '登录账号至少4位'
  if (!/^[A-Za-z]/.test(account)) return '登录账号必须以英文字母开头'
  if (!LOGIN_ACCOUNT_PATTERN.test(account)) return '仅支持英文字母、数字和下划线'
  return ''
}

export const resolveRegistrationErrorField = (message) => {
  const text = String(message || '')
  if (/手机号/.test(text)) return 'phone'
  if (/登录账号|用户名|账号/.test(text)) return 'username'
  if (/邀请码|邀请人/.test(text)) return 'inviteCode'
  if (/短信验证码|验证码/.test(text)) return 'smsCode'
  if (/登录密码|密码/.test(text)) return 'password'
  return ''
}
