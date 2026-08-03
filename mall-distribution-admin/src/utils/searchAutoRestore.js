import { onBeforeUnmount, watch } from 'vue'

const normalize = (value) => String(value ?? '').trim()

/**
 * 已执行关键词查询后，如果运营人员通过清除按钮或退格键把关键词删空，
 * 自动恢复页面的默认列表（或由页面恢复初始空态）。有内容时不自动请求，
 * 避免输入过程中频繁查询。
 */
export function useSearchAutoRestore(source, restore, options = {}) {
  const delay = Number(options.delay ?? 120)
  let appliedKeyword = ''
  let timer = null

  const clearTimer = () => {
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
  }

  const markSearchApplied = (value = source()) => {
    appliedKeyword = normalize(value)
    clearTimer()
  }

  const stop = watch(
    () => normalize(source()),
    (keyword) => {
      if (keyword || !appliedKeyword) return
      clearTimer()
      timer = window.setTimeout(() => {
        timer = null
        if (normalize(source()) || !appliedKeyword) return
        appliedKeyword = ''
        restore()
      }, Math.max(0, delay))
    },
  )

  onBeforeUnmount(() => {
    clearTimer()
    stop()
  })

  return { markSearchApplied }
}
