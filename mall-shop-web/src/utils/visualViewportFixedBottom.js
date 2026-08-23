import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue'

const MAX_VIEWPORT_SHIFT = 320
const POSITION_TOLERANCE = 0.75

export const resolveFixedBottomShift = (currentShift, currentBottom, viewportBottom) => {
  const shift = Number(currentShift)
  const bottom = Number(currentBottom)
  const target = Number(viewportBottom)
  if (![shift, bottom, target].every(Number.isFinite)) return 0

  const delta = target - bottom
  if (Math.abs(delta) <= POSITION_TOLERANCE) return shift
  return Math.max(-MAX_VIEWPORT_SHIFT, Math.min(MAX_VIEWPORT_SHIFT, shift + delta))
}

/**
 * iOS/微信在收起或恢复浏览器工具栏时，position:fixed 偶尔仍停留在旧的
 * layout viewport 底部。这里以 visualViewport 为准校正真实可视位置。
 */
export const useVisualViewportFixedBottom = (elementRef) => {
  let animationFrame = 0
  let currentShift = 0
  let mounted = false

  const clearShift = (element = elementRef.value) => {
    currentShift = 0
    element?.style.removeProperty('--bottom-nav-viewport-shift')
  }

  const alignToViewport = () => {
    animationFrame = 0
    const element = elementRef.value
    const viewport = window.visualViewport
    if (!element || !viewport) return

    const rect = element.getBoundingClientRect()
    if (rect.height <= 0 || window.getComputedStyle(element).position !== 'fixed') {
      clearShift(element)
      return
    }

    const viewportBottom = viewport.offsetTop + viewport.height
    const nextShift = resolveFixedBottomShift(currentShift, rect.bottom, viewportBottom)
    if (Math.abs(nextShift - currentShift) <= POSITION_TOLERANCE) return

    currentShift = nextShift
    element.style.setProperty('--bottom-nav-viewport-shift', `${currentShift.toFixed(2)}px`)
  }

  const scheduleAlignment = () => {
    if (!mounted) return
    window.cancelAnimationFrame(animationFrame)
    animationFrame = window.requestAnimationFrame(alignToViewport)
  }

  const resetAndAlign = () => {
    clearShift()
    scheduleAlignment()
  }

  const handleVisibilityChange = () => {
    if (document.visibilityState === 'visible') resetAndAlign()
  }

  watch(elementRef, (element, previousElement) => {
    clearShift(previousElement)
    if (!element) return
    nextTick(resetAndAlign)
  })

  onMounted(() => {
    mounted = true
    const viewport = window.visualViewport
    viewport?.addEventListener('resize', scheduleAlignment, { passive: true })
    viewport?.addEventListener('scroll', scheduleAlignment, { passive: true })
    window.addEventListener('resize', resetAndAlign, { passive: true })
    window.addEventListener('scroll', scheduleAlignment, { passive: true })
    window.addEventListener('orientationchange', resetAndAlign, { passive: true })
    window.addEventListener('pageshow', resetAndAlign, { passive: true })
    document.addEventListener('visibilitychange', handleVisibilityChange)
    nextTick(resetAndAlign)
  })

  onBeforeUnmount(() => {
    mounted = false
    const viewport = window.visualViewport
    viewport?.removeEventListener('resize', scheduleAlignment)
    viewport?.removeEventListener('scroll', scheduleAlignment)
    window.removeEventListener('resize', resetAndAlign)
    window.removeEventListener('scroll', scheduleAlignment)
    window.removeEventListener('orientationchange', resetAndAlign)
    window.removeEventListener('pageshow', resetAndAlign)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    window.cancelAnimationFrame(animationFrame)
    clearShift()
  })
}
