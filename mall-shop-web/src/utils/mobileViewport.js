const ROOT_LOCK_CLASS = 'shop-mobile-viewport-locked'
const KEYBOARD_OPEN_CLASS = 'shop-mobile-keyboard-open'
const NON_TEXT_INPUT_TYPES = new Set([
  'button', 'checkbox', 'color', 'file', 'hidden', 'image', 'radio', 'range', 'reset', 'submit',
])

export const isTextEntryTarget = (element) => {
  if (!element) return false
  if (element.isContentEditable) return true
  const tagName = String(element.tagName || '').toUpperCase()
  if (tagName === 'TEXTAREA' || tagName === 'SELECT') return true
  if (tagName !== 'INPUT') return false
  return !NON_TEXT_INPUT_TYPES.has(String(element.type || 'text').toLowerCase())
}

/**
 * 只有“正在编辑输入框”且可视高度发生明显收缩时才认定键盘已打开。
 * 这样浏览器地址栏的普通展开/收起不会被误判为键盘。
 */
export const isMobileKeyboardOpen = ({ activeElement, layoutHeight, viewportHeight }) => {
  const stableHeight = Math.max(0, Number(layoutHeight) || 0)
  const visibleHeight = Math.max(0, Number(viewportHeight) || 0)
  if (!isTextEntryTarget(activeElement) || !stableHeight || !visibleHeight) return false
  const keyboardThreshold = Math.max(120, Math.round(stableHeight * 0.18))
  return stableHeight - visibleHeight >= keyboardThreshold
}

/**
 * 锁定移动端应用的唯一滚动区域，并单独处理 iOS 软键盘。
 *
 * 普通地址栏变化继续使用 visualViewport 实时高度。键盘打开时则保留打开前
 * 的稳定布局高度、隐藏底部功能导航，并把键盘占用高度作为内容滚动留白，
 * 避免整个应用随键盘一起被压缩后出现导航上移或大块空白。
 */
export const installMobileViewport = (maxWidth = 920) => {
  if (typeof window === 'undefined' || typeof document === 'undefined') return () => {}

  const root = document.documentElement
  const media = window.matchMedia(`(max-width: ${maxWidth}px)`)
  let frame = 0
  let settleTimer = 0
  let focusTimer = 0
  let stableLayoutHeight = 0

  const removeViewportState = () => {
    root.classList.remove(KEYBOARD_OPEN_CLASS)
    root.style.removeProperty('--shop-visual-viewport-height')
    root.style.removeProperty('--shop-visual-viewport-width')
    root.style.removeProperty('--shop-keyboard-inset')
  }

  const keepFocusedFieldVisible = () => {
    window.clearTimeout(focusTimer)
    if (!root.classList.contains(KEYBOARD_OPEN_CLASS)) return
    const activeElement = document.activeElement
    if (!isTextEntryTarget(activeElement) || typeof activeElement.scrollIntoView !== 'function') return
    focusTimer = window.setTimeout(() => {
      if (document.activeElement === activeElement) {
        activeElement.scrollIntoView({ block: 'center', inline: 'nearest' })
      }
    }, 80)
  }

  const update = () => {
    frame = 0
    const locked = media.matches
    root.classList.toggle(ROOT_LOCK_CLASS, locked)
    if (!locked) {
      stableLayoutHeight = 0
      removeViewportState()
      return
    }

    const viewport = window.visualViewport
    const viewportHeight = Math.max(1, Math.round(viewport?.height || window.innerHeight))
    const viewportWidth = Math.max(1, Math.round(viewport?.width || window.innerWidth))
    if (!stableLayoutHeight) {
      stableLayoutHeight = Math.max(viewportHeight, Math.round(window.innerHeight || 0))
    }

    const layoutHeight = Math.max(stableLayoutHeight, Math.round(window.innerHeight || 0))
    const keyboardOpen = isMobileKeyboardOpen({
      activeElement: document.activeElement,
      layoutHeight,
      viewportHeight,
    })

    if (!keyboardOpen) {
      stableLayoutHeight = Math.max(viewportHeight, Math.round(window.innerHeight || 0))
    }

    const keyboardInset = keyboardOpen ? Math.max(0, stableLayoutHeight - viewportHeight) : 0
    const shellHeight = keyboardOpen ? stableLayoutHeight : viewportHeight
    root.classList.toggle(KEYBOARD_OPEN_CLASS, keyboardOpen)
    root.style.setProperty('--shop-visual-viewport-height', `${shellHeight}px`)
    root.style.setProperty('--shop-visual-viewport-width', `${viewportWidth}px`)
    root.style.setProperty('--shop-keyboard-inset', `${keyboardInset}px`)
    keepFocusedFieldVisible()
  }

  const schedule = () => {
    if (frame) window.cancelAnimationFrame(frame)
    frame = window.requestAnimationFrame(update)
  }

  const settle = () => {
    schedule()
    window.clearTimeout(settleTimer)
    settleTimer = window.setTimeout(schedule, 120)
  }

  const resetForOrientation = () => {
    stableLayoutHeight = 0
    settle()
  }

  update()
  window.addEventListener('resize', settle, { passive: true })
  window.addEventListener('orientationchange', resetForOrientation, { passive: true })
  document.addEventListener('focusin', settle, true)
  document.addEventListener('focusout', settle, true)
  window.visualViewport?.addEventListener('resize', settle, { passive: true })
  window.visualViewport?.addEventListener('scroll', schedule, { passive: true })
  media.addEventListener?.('change', settle)

  return () => {
    if (frame) window.cancelAnimationFrame(frame)
    window.clearTimeout(settleTimer)
    window.clearTimeout(focusTimer)
    window.removeEventListener('resize', settle)
    window.removeEventListener('orientationchange', resetForOrientation)
    document.removeEventListener('focusin', settle, true)
    document.removeEventListener('focusout', settle, true)
    window.visualViewport?.removeEventListener('resize', settle)
    window.visualViewport?.removeEventListener('scroll', schedule)
    media.removeEventListener?.('change', settle)
    root.classList.remove(ROOT_LOCK_CLASS)
    removeViewportState()
  }
}
