const ROOT_LOCK_CLASS = 'shop-mobile-viewport-locked'

/**
 * 锁定移动端应用的唯一可视区域。
 *
 * iPhone 微信内置浏览器的地址栏收起/展开时，100vh/100dvh 在部分版本中
 * 会短暂保留旧值。这里直接读取 visualViewport 的当前高度，只覆盖绝对高度，
 * 不做累计位移；页面本身不滚动，仅由应用中间内容区滚动。
 */
export const installMobileViewport = (maxWidth = 920) => {
  if (typeof window === 'undefined' || typeof document === 'undefined') return () => {}

  const root = document.documentElement
  const media = window.matchMedia(`(max-width: ${maxWidth}px)`)
  let frame = 0

  const update = () => {
    frame = 0
    const locked = media.matches
    root.classList.toggle(ROOT_LOCK_CLASS, locked)
    if (!locked) {
      root.style.removeProperty('--shop-visual-viewport-height')
      root.style.removeProperty('--shop-visual-viewport-width')
      return
    }
    const viewport = window.visualViewport
    const height = Math.max(1, Math.round(viewport?.height || window.innerHeight))
    const width = Math.max(1, Math.round(viewport?.width || window.innerWidth))
    root.style.setProperty('--shop-visual-viewport-height', `${height}px`)
    root.style.setProperty('--shop-visual-viewport-width', `${width}px`)
  }

  const schedule = () => {
    if (frame) window.cancelAnimationFrame(frame)
    frame = window.requestAnimationFrame(update)
  }

  update()
  window.addEventListener('resize', schedule, { passive: true })
  window.addEventListener('orientationchange', schedule, { passive: true })
  window.visualViewport?.addEventListener('resize', schedule, { passive: true })
  window.visualViewport?.addEventListener('scroll', schedule, { passive: true })
  media.addEventListener?.('change', schedule)

  return () => {
    if (frame) window.cancelAnimationFrame(frame)
    window.removeEventListener('resize', schedule)
    window.removeEventListener('orientationchange', schedule)
    window.visualViewport?.removeEventListener('resize', schedule)
    window.visualViewport?.removeEventListener('scroll', schedule)
    media.removeEventListener?.('change', schedule)
    root.classList.remove(ROOT_LOCK_CLASS)
    root.style.removeProperty('--shop-visual-viewport-height')
    root.style.removeProperty('--shop-visual-viewport-width')
  }
}
