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
})
