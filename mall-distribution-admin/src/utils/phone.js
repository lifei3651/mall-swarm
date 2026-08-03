export const isValidMainlandPhone = (value) => /^1[3-9]\d{9}$/.test(String(value ?? '').trim())

export const normalizeMainlandPhone = (value) => String(value ?? '')
  .replace(/\D/g, '')
  .slice(0, 11)
