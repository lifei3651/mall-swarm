import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const read = (file) => readFileSync(path.join(root, file), 'utf8')

describe('customer service ticket workbench', () => {
  it('is a dedicated order-service page for platform and scoped merchants', () => {
    const router = read('src/router/index.js')
    const layout = read('src/components/Layout.vue')
    const workspace = read('src/utils/adminWorkspace.js')
    expect(router).toMatch(/path: 'service-tickets'/)
    expect(router).toMatch(/permission: 'shop:order'/)
    expect(layout).toMatch(/客服工单[^\n]+\/shop\/service-tickets/)
    expect(layout).toContain('isMerchantWorkspacePath(item.path)')
    expect(workspace).toContain("'/shop/service-tickets'")
  })

  it('shows conversation, next responsibility and explicit reply state', () => {
    const source = read('src/views/shop/service-tickets.vue')
    const api = read('src/api/shop.js')
    expect(source).toMatch(/firstResponseOverdue/)
    expect(source).toMatch(/nextActionParty/)
    expect(source).toMatch(/replyAdminServiceTicket/)
    expect(source).toMatch(/不自动退款或关闭交易/)
    expect(api).toMatch(/X-Idempotency-Key/)
  })
})
