const MEMBER_SEARCH_PATTERN = /^[\p{L}\p{N}@._+\-·\s]+$/u
const UNSUPPORTED_SEARCH_CHARACTERS = /[\u0000-\u001f\u007f<>\\`{}\[\]]/

export function validateSearchKeyword(value, options = {}) {
  const { label = '关键词', maxLength = 100, required = false } = options
  const keyword = String(value ?? '').trim()
  if (!keyword) {
    return required
      ? { valid: false, keyword: '', message: `请输入${label}` }
      : { valid: true, keyword: '', message: '' }
  }
  if (keyword.length > maxLength) {
    return { valid: false, keyword, message: `${label}不能超过${maxLength}个字符` }
  }
  if (UNSUPPORTED_SEARCH_CHARACTERS.test(keyword)) {
    return { valid: false, keyword, message: `${label}含有不支持的特殊字符，请修改后重新查询` }
  }
  return { valid: true, keyword, message: '' }
}

/**
 * 统一整理会员搜索输入。查询入口只接受运营人员能够识别的会员信息，
 * 不把内部数据库 ID、控制字符或成段表达式直接提交给后端。
 */
export function validateMemberSearch(value, options = {}) {
  const { required = false, maxLength = 64 } = options
  const keyword = String(value ?? '').trim()

  if (!keyword) {
    return required
      ? { valid: false, keyword: '', message: '请输入登录账号、手机号或会员名称' }
      : { valid: true, keyword: '', message: '' }
  }
  if (keyword.length > maxLength) {
    return { valid: false, keyword, message: `搜索内容不能超过${maxLength}个字符` }
  }
  if (!MEMBER_SEARCH_PATTERN.test(keyword)) {
    return {
      valid: false,
      keyword,
      message: '搜索内容含有不支持的特殊字符，请输入登录账号、手机号或会员名称',
    }
  }
  return { valid: true, keyword, message: '' }
}

export function isMemberNotFoundError(error) {
  const message = String(error?.message || '')
  return [
    '未找到对应会员',
    '未找到会员',
    '会员不存在',
    '商城账号不存在',
    '账户不存在',
    '代理不存在',
    '尚未进入会员关系和业绩体系',
  ].some((text) => message.includes(text))
}

export function memberSearchFailureMessage(error, keyword, subject = '相关数据') {
  if (isMemberNotFoundError(error)) {
    return `未找到与“${keyword}”匹配的会员，请核对登录账号或手机号`
  }
  return `${subject}查询失败，请稍后重试；如多次出现，请联系管理员检查服务状态`
}

export function memberSearchEmptyText(keyword, subject = '记录') {
  return keyword
    ? `未找到与“${keyword}”匹配的${subject}`
    : `暂无${subject}`
}
