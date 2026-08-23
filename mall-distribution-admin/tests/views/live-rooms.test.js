import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const read = (relative) => readFileSync(fileURLToPath(new URL(relative, import.meta.url)), 'utf8')

describe('直播广场后台运营链路', () => {
  it('提供独立菜单、路由和真实接口', () => {
    const layout = read('../../src/components/Layout.vue')
    const router = read('../../src/router/index.js')
    const api = read('../../src/api/shop.js')
    expect(layout).toContain("title: '直播间管理'")
    expect(router).toContain("path: 'live-rooms'")
    expect(api).toContain("url: '/shop/admin/live-rooms'")
    expect(api).toContain('updateLiveRoomStatus')
  })

  it('明确区分公开观看地址与服务商推流密钥', () => {
    const source = read('../../src/views/shop/live-rooms.vue')
    expect(source).toContain('推流密钥只保存在直播服务商，不录入商城')
    expect(source).toContain('不要填写推流地址、Secret 或鉴权密钥')
    expect(source).toContain('HTTPS 观看地址')
  })

  it('发布前校验时间、状态与关联在售商品', () => {
    const source = read('../../src/views/shop/live-rooms.vue')
    expect(source).toContain('预告或直播中至少关联一个在售商品')
    expect(source).toContain('直播中状态必须填写 HTTPS 观看地址')
    expect(source).toContain('scheduledStartTime: timeRange.value[0]')
    expect(source).toContain('最多选择20个在售商品')
  })
})
