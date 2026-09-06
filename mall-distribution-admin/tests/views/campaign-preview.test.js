import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { campaignIndex, decorateCampaignProducts } from '../../src/utils/campaignDisplay.js'

const source = readFileSync(resolve(process.cwd(), 'src/views/tenant/list.vue'), 'utf8')
const api = readFileSync(resolve(process.cwd(), 'src/api/shop.js'), 'utf8')
const now = Date.parse('2026-09-06T10:00:00Z')
const product = { id: '1', status: 1, salePrice: 99 }
const active = { activity: { id: '10', productId: '1', status: 1, availableStock: 3, flashPrice: 19,
  startTime: '2026-09-06T09:00:00Z', endTime: '2026-09-06T11:00:00Z' }, product, activityState: 'ACTIVE' }

describe('活动信息流装修与公开商城同步', () => {
  it('公开商品和活动只读源匹配小程序，不再展示后台下架商品或示意活动', () => {
    expect(api).toMatch(/listStorefrontProducts[\s\S]*?url: '\/shop\/products'/)
    expect(api).toMatch(/listStorefrontFlashSales[\s\S]*?url: '\/shop\/flash-sales'/)
    expect(source).toContain('listStorefrontProducts({ status: 1, pageNum: 1, pageSize: 20 })')
    expect(source).toContain('product in campaignPreviewProducts')
    expect(source).toContain('v-if="product.campaign"')
    expect(source).not.toContain('活动好物 · 真实活动显示倒计时')
  })
  it('整体版型与首页模块均提供活动来源、管理入口和刷新，不新增互相冲突的开关', () => {
    expect(source).toContain("['layout', 'home'].includes(activeEditSection)")
    expect(source).toContain('限时活动 · 商品卡内容')
    expect(source).toContain('to="/tenant/flash-sales" target="_blank"')
    expect(source).toContain('@click="loadPreviewCampaigns"')
    expect(source).toContain('previewCampaignProductCount')
  })
  it('切换版型、关闭弹窗使旧请求和计时失效；读取失败显眼弹窗，不能冒充没有活动', () => {
    expect(source).toContain("visible && layout === 'campaign-feed'")
    expect(source).toContain('version !== previewCampaignVersion')
    expect(source).toContain('tenantId !== currentTenant.value?.id')
    expect(source).toContain('String(row.activity.tenantId) !== String(tenantId)')
    expect(source).toContain('ElMessageBox.alert(previewCampaignError.value')
    expect(source).toContain('onUnmounted(() => { previewCampaignVersion++; clearInterval(previewCampaignTimer) })')
  })
  it('仅匹配的公开活动商品显示真实价和倒计时，其他商品保持普通售价', () => {
    const cards = decorateCampaignProducts([product, { ...product, id: '2' }], [active], 'campaign-feed', now)
    expect(cards.map(p => p.priceText)).toEqual(['19.00', '99.00'])
    expect(cards[0].campaign.countdown).toBe('距结束 01:00:00')
    expect(cards[1].campaign).toBeNull()
    expect(decorateCampaignProducts([product], [active], 'standard', now)[0].priceText).toBe('99.00')
  })
  it('未开始/进行中/已结束自动衔接，无活动、售罄或关闭恢复普通商品', () => {
    const upcoming = { ...active, activityState: 'UPCOMING', activity: { ...active.activity, startTime: '2026-09-06T10:30:00Z' } }
    expect(campaignIndex([upcoming], now).get('1').actionLabel).toBe('去看看')
    expect(campaignIndex([upcoming], now + 1800000).get('1').actionLabel).toBe('去抢购')
    for (const rows of [[], [{ ...active, activityState: 'SOLD_OUT' }], [{ ...active, activity: { ...active.activity, status: 0 } }]]) {
      expect(decorateCampaignProducts([product], rows, 'campaign-feed', now)[0].priceText).toBe('99.00')
    }
    expect(campaignIndex([active], now + 3600000).size).toBe(0)
  })
})
