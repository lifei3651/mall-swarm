import defaultBrowserLogo from '@/assets/lingqi-logo-mark.png'

const ensureIconLink = (rel) => {
  let link = document.head.querySelector(`link[rel="${rel}"]`)
  if (!link) {
    link = document.createElement('link')
    link.rel = rel
    document.head.appendChild(link)
  }
  return link
}

/** 让后台浏览器标签页图标与当前商城品牌保持一致。 */
export const updateAdminBrowserLogo = (logoUrl) => {
  const resolvedLogo = logoUrl || defaultBrowserLogo
  const favicon = ensureIconLink('icon')
  favicon.removeAttribute('type')
  favicon.href = resolvedLogo
  ensureIconLink('apple-touch-icon').href = resolvedLogo
}
