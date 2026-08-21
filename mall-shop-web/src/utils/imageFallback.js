const FALLBACK_IMAGE = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(`
  <svg xmlns="http://www.w3.org/2000/svg" width="640" height="480" viewBox="0 0 640 480">
    <rect width="640" height="480" fill="#f3f4f6"/>
    <path d="M235 305l62-72 45 50 35-39 70 83H207z" fill="#cbd5e1"/>
    <circle cx="263" cy="176" r="24" fill="#cbd5e1"/>
    <rect x="190" y="130" width="260" height="220" rx="18" fill="none" stroke="#94a3b8" stroke-width="12"/>
    <text x="320" y="402" text-anchor="middle" font-family="sans-serif" font-size="24" fill="#64748b">图片暂不可用</text>
  </svg>
`)}`

export const applyImageFallback = (event) => {
  const image = event?.currentTarget
  if (!image || image.dataset.fallbackApplied === 'true') return
  image.dataset.fallbackApplied = 'true'
  image.src = FALLBACK_IMAGE
}
