import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const read = (relative) => readFileSync(fileURLToPath(new URL(relative, import.meta.url)), 'utf8')

describe('私域直播运营链路', () => {
  it('提供独立菜单、路由和真实接口', () => {
    const layout = read('../../src/components/Layout.vue')
    const router = read('../../src/router/index.js')
    const api = read('../../src/api/shop.js')
    expect(layout).toContain("title: '直播运营中心'")
    expect(router).toContain("path: 'live-rooms'")
    expect(api).toContain("url: '/shop/admin/live-rooms'")
    expect(api).toContain("url: '/shop/admin/live-anchors'")
    expect(api).toContain('forceStopLiveRoom')
  })

  it('明确区分腾讯云短时推流和厂家固定观看源', () => {
    const source = read('../../src/views/shop/live-rooms.vue')
    expect(source).toContain('腾讯云直播')
    expect(source).toContain('厂家固定视频源')
    expect(source).toContain('不得填写内网地址或推流密钥')
  })

  it('发布前校验时间、授权主播和关联在售商品', () => {
    const source = read('../../src/views/shop/live-rooms.vue')
    expect(source).toContain('至少关联一个在售商品')
    expect(source).toContain('“直播中”只能由已授权主播开始直播后进入')
    expect(source).toContain('scheduledStartTime:timeRange.value[0]')
    expect(source).toContain('最多选择20个在售商品')
  })

  it('主播权限支持平台直接开通、暂停、恢复和收回', () => {
    const source = read('../../src/views/shop/live-rooms.vue')
    const api = read('../../src/api/shop.js')
    expect(source).toContain('平台添加后立即获得开播权限，不需要逐场审核')
    expect(source).toContain('恢复权限')
    expect(source).toContain('收回权限')
    expect(api).toContain('updateLiveAnchorStatus')
  })
})
