const normalizeFullWidthDigits = (value) => String(value ?? '').replace(/[０-９]/g, (digit) => (
  String(digit.charCodeAt(0) - '０'.charCodeAt(0))
))

/**
 * 购买数量只接受从开头连续输入的数字。
 * 小数、负号、字母或特殊字符不会被拼接成另一个更大的数量。
 */
export const sanitizePositiveIntegerInput = (value, maxLength = 10) => {
  const normalized = normalizeFullWidthDigits(value).trimStart()
  const leadingDigits = normalized.match(/^\d+/)?.[0] || ''
  return leadingDigits.replace(/^0+(?=\d)/, '').slice(0, Math.max(1, Number(maxLength) || 1))
}

export const resolvePositiveIntegerQuantity = (value, maximum) => {
  const digits = sanitizePositiveIntegerInput(value)
  const parsed = Number(digits)
  const safeMaximum = Math.max(1, Math.floor(Number(maximum) || 1))
  if (!Number.isSafeInteger(parsed) || parsed <= 0) return 1
  return Math.min(parsed, safeMaximum)
}
