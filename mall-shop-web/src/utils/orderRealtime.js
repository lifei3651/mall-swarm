import { apiBaseUrl } from '@/utils/appEnvironment'
import { getLegacyShopToken } from '@/utils/shopSession'

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

export const connectOrderRealtime = ({ onEvent, onStatus } = {}) => {
  let stopped = false
  let controller = null

  const run = async () => {
    let retry = 1000
    while (!stopped) {
      controller = new AbortController()
      try {
        const token = getLegacyShopToken()
        const response = await fetch(`${apiBaseUrl}/shop/events/orders`, {
          credentials: 'include',
          signal: controller.signal,
          headers: {
            Accept: 'text/event-stream',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
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
            let eventName = 'message'
            const data = []
            block.split('\n').forEach((line) => {
              if (line.startsWith('event:')) eventName = line.slice(6).trim()
              if (line.startsWith('data:')) data.push(line.slice(5).trim())
            })
            if (eventName === 'order.changed' && data.length) {
              try { onEvent?.(JSON.parse(data.join('\n'))) } catch { onEvent?.({}) }
            }
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
  return () => {
    stopped = true
    controller?.abort()
    onStatus?.(false)
  }
}
