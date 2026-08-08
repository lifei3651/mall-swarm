const ALLOWED_NICKNAME = /^[\u3400-\u9fffA-Za-z0-9·_\- ]{2,20}$/

export const normalizeNicknameInput = (value) => String(value ?? '')
  .replace(/[^\u3400-\u9fffA-Za-z0-9·_\- ]/g, '')
  .replace(/\s{2,}/g, ' ')
  .slice(0, 20)

export const validateNickname = (value) => {
  const nickname = String(value ?? '').trim().replace(/\s+/g, ' ')
  if (!nickname) return '请输入昵称'
  if (nickname.length < 2 || nickname.length > 20) return '昵称需为2至20个字符'
  if (!ALLOWED_NICKNAME.test(nickname)) return '昵称仅支持中文、字母、数字、空格、·、-和_'
  return ''
}
