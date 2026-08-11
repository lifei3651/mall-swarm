const BUILD_ID = new URL(self.location.href).searchParams.get('build') || 'default'
const CACHE_NAME = `lingqi-shop-${BUILD_ID}`
const SHELL = ['/', '/manifest.webmanifest', '/pwa-icon-192.png', '/pwa-icon-512.png']

const isNeverCached = (url) => url.pathname.startsWith('/api/')
  || url.pathname.startsWith('/admin')
  || url.pathname.includes('/pay/')
  || url.pathname.endsWith('/version.json')

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then(async (cache) => {
    await cache.addAll(SHELL)
    const response = await fetch('/', { cache: 'no-store' })
    if (response.ok) {
      const html = await response.clone().text()
      const assets = [...html.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g)].map((match) => match[1])
      if (assets.length) await cache.addAll([...new Set(assets)])
    }
  }).then(() => self.skipWaiting()))
})

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((key) => key.startsWith('lingqi-shop-') && key !== CACHE_NAME).map((key) => caches.delete(key))))
    .then(() => self.clients.claim()))
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin || isNeverCached(url)) return

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).then((response) => {
      if (response.ok) caches.open(CACHE_NAME).then((cache) => cache.put('/', response.clone()))
      return response
    }).catch(() => caches.match('/')))
    return
  }

  if (url.pathname.startsWith('/assets/') || SHELL.includes(url.pathname)) {
    event.respondWith(caches.match(request).then((cached) => cached || fetch(request).then((response) => {
      if (response.ok) caches.open(CACHE_NAME).then((cache) => cache.put(request, response.clone()))
      return response
    })))
  }
})
