import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => vi.fn())
vi.mock('@/utils/request', () => ({ default: request }))

import { getTenantBusinessModes, saveTenantBusinessModes } from '@/api/tenant'

describe('业务模式前端接口合同', () => {
  beforeEach(() => request.mockReset())

  it('使用独立GET/PUT，不复用整租户列表与保存接口', async () => {
    request.mockResolvedValue({ data: {} })
    const data = {
      id: 1,
      promotionJoinMode: 'DISABLED',
      flashSaleEnabled: 0,
      flashSaleBonusMode: 'NONE',
      repurchaseMallEnabled: 0,
      repurchaseEligibilityMode: 'PAID_MEMBER',
      repurchaseBonusMode: 'NONE',
    }

    await getTenantBusinessModes(1)
    await saveTenantBusinessModes(1, data)

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/distribution/tenant/1/business-modes',
      method: 'get',
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/distribution/tenant/1/business-modes',
      method: 'put',
      data,
    })
    expect(request.mock.calls.flatMap(([config]) => [config.url])).not.toContain('/distribution/tenant/list')
    expect(request.mock.calls.flatMap(([config]) => [config.url])).not.toContain('/distribution/tenant')
  })
})
