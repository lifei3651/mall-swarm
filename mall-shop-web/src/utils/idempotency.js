export const createIdempotencyKey = (scope = 'request') => {
  const random = globalThis.crypto?.randomUUID?.()
    || `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${scope}-${random}`.replace(/[^A-Za-z0-9._:-]/g, '-').slice(0, 128)
}
