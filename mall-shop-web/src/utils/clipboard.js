const fallbackCopyText = (text) => {
  if (typeof document === 'undefined' || !document.body) return false

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.setAttribute('aria-hidden', 'true')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '0'
  textarea.style.opacity = '0'
  textarea.style.pointerEvents = 'none'
  document.body.appendChild(textarea)

  try {
    textarea.focus()
    textarea.select()
    textarea.setSelectionRange(0, textarea.value.length)
    return document.execCommand('copy')
  } catch (_) {
    return false
  } finally {
    document.body.removeChild(textarea)
  }
}

/**
 * Copy text in both regular browsers and embedded APP WebViews.
 * Falls back to the legacy selection method when Clipboard API access is denied.
 */
export const copyText = async (value) => {
  const text = String(value ?? '')
  if (!text) return false

  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch (_) {
      // Desktop browsers can reject clipboard access because of permissions.
    }
  }

  return fallbackCopyText(text)
}
