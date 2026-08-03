const STALE_CHUNK_RELOAD_KEY = 'shop_stale_chunk_reload_target'

const STALE_CHUNK_MESSAGES = [
  'Failed to fetch dynamically imported module',
  'Importing a module script failed',
  'Unable to preload CSS',
  'ChunkLoadError',
  'Loading chunk',
]

export const isStaleChunkError = (error) => {
  const message = String(error?.message || error || '')
  return STALE_CHUNK_MESSAGES.some((text) => message.includes(text))
}

export const recoverFromStaleChunk = (error, targetHref) => {
  if (!isStaleChunkError(error)) return false

  const target = targetHref || `${window.location.pathname}${window.location.search}${window.location.hash}`
  let previousTarget = ''
  try {
    previousTarget = window.sessionStorage.getItem(STALE_CHUNK_RELOAD_KEY) || ''
  } catch {
    // 部分浏览器禁用会话存储时仍允许执行一次页面级恢复。
  }
  if (previousTarget === target) return false

  try {
    window.sessionStorage.setItem(STALE_CHUNK_RELOAD_KEY, target)
  } catch {
    // 会话存储不可用不应阻止重新加载最新页面资源。
  }
  window.location.replace(target)
  return true
}

export const clearStaleChunkRecovery = () => {
  try {
    window.sessionStorage.removeItem(STALE_CHUNK_RELOAD_KEY)
  } catch {
    // 忽略会话存储异常。
  }
}
