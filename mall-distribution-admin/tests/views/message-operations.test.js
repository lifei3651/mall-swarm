import { describe,expect,it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
const source=readFileSync(resolve(process.cwd(),'src/views/tenant/message-operations.vue'),'utf8')
const api=readFileSync(resolve(process.cwd(),'src/api/messageOperation.js'),'utf8')
describe('message operations safety boundary',()=>{
  it('shows templates channels deliveries and keeps all external channels closed',()=>{
    expect(source).toContain('消息模板');expect(source).toContain('发送记录');expect(source).toContain('外部通道生产默认关闭')
    expect(source).toContain('短信');expect(source).toContain('App 推送');expect(source).toContain('小程序订阅')
  })
  it('shows retry history without exposing a resend action',()=>{
    expect(source).toContain('retryCount')
    expect(api).not.toMatch(/export function (retry|resend)/i)
    expect(source).not.toMatch(/@(click|confirm)=["'][^"']*(retry|resend|重发)/i)
  })
  it('shows one read-only service SMS readiness panel instead of a dangerous all-on action',()=>{
    expect(source).toContain('通知短信开通进度')
    expect(source).toContain('合规、服务商、模板、事件、费用和运行门禁')
    expect(source).toContain('会员已授权')
    expect(source).toContain('serviceSmsReadiness')
    expect(source).not.toMatch(/@(click|confirm)=["'][^"']*(全开|开启短信|enableSms)/i)
  })
})
