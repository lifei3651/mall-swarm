import { beforeEach, describe, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => vi.fn())
vi.mock('@/utils/request', () => ({ default: request }))
import { getDirectReferralConfig, saveDirectReferralConfig } from '@/api/bonusConfig'

describe('直接推荐佣金前端接口合同', () => {
  beforeEach(() => request.mockReset())
  it('当前客户由服务端会话确定，GET/PUT保持专用合同与版本校验', async () => {
    const data = { enabled: true, commissionRate: 0.125, purchaseScope: 'ALL_ORDERS', settlementDelayDays: 7, expectedVersionId: 42, confirmPolicySwitch: false }
    await getDirectReferralConfig()
    await saveDirectReferralConfig(data)
    expect(request).toHaveBeenNthCalledWith(1, { url: '/distribution/bonus-config/direct-referral', method: 'get', silentError: true })
    expect(request).toHaveBeenNthCalledWith(2, { url: '/distribution/bonus-config/direct-referral', method: 'put', data, silentError: true })
    expect(request.mock.calls[1][0].data).not.toHaveProperty('tenantId')
  })
})
