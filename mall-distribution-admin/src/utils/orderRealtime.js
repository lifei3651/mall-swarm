const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

export const connectAdminOrderRealtime = ({ onEvent, onStatus } = {}) => {
  let stopped = false
  let controller = null
  const run = async () => {
    let retry = 1000
    while (!stopped) {
      controller = new AbortController()
      try {
        const legacyToken = localStorage.getItem('token')
        const hasSession = Boolean(legacyToken || localStorage.getItem('admin_session_present') === '1')
        if (!hasSession) throw new Error('NO_SESSION')
        const headers = { Accept: 'text/event-stream', 'X-Admin-Client': 'admin-web' }
        if (legacyToken) headers.Authorization = `Bearer ${legacyToken}`
        const response = await fetch('/api/shop/admin/events/orders', {
          signal: controller.signal,
          credentials: 'same-origin',
          headers,
        })
        if (!response.ok || !response.body) throw new Error(`SSE ${response.status}`)
        onStatus?.(true)
        retry = 1000
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        while (!stopped) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
          let boundary
          while ((boundary = buffer.indexOf('\n\n')) >= 0) {
            const block = buffer.slice(0, boundary)
            buffer = buffer.slice(boundary + 2)
            const eventLine = block.split('\n').find((line) => line.startsWith('event:'))
            if (eventLine?.slice(6).trim() === 'order.changed') onEvent?.()
          }
        }
      } catch (error) {
        if (stopped || error?.name === 'AbortError') break
      } finally {
        onStatus?.(false)
      }
      await wait(retry)
      retry = Math.min(retry * 2, 30000)
    }
  }
  run()
  return () => { stopped = true; controller?.abort(); onStatus?.(false) }
}
