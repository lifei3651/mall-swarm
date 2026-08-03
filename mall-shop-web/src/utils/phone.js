/**
 * 中国大陆手机号基础格式校验。
 * 号码是否真实可用、是否属于当前用户，仍须由短信验证码确认。
 */
export const isValidMainlandPhone = (value) => /^1[3-9]\d{9}$/.test(String(value ?? '').trim())

export const normalizeMainlandPhone = (value) => String(value ?? '')
  .replace(/\D/g, '')
  .slice(0, 11)
