import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'
test('备注默认空，编辑草稿取消不保存，确认才提交，清空恢复无备注', async () => {
  const env = commerceEnv(), page = env.page('checkout')
  assert.equal(page.data.remark, '')
  page.openRemark(); page.remarkInput({ detail: { value: '草稿' } })
  assert.equal(page.data.remark, '')
  await page.submit(); assert.equal(env.calls.length, 0, '编辑备注期间不提交订单')
  page.cancelRemark(); assert.equal(page.data.remark, '')
  page.openRemark(); page.remarkInput({ detail: { value: ' 请晚点送达 ' } }); page.saveRemark()
  assert.equal(page.data.remark, '请晚点送达')
  assert.equal(page.orderPayload({ id: '9' }).remark, '请晚点送达')
  assert.equal(page.orderPayload({ id: '9' }, false).remark, undefined, '不改变报价请求')
  page.openRemark(); assert.equal(page.data.remarkDraft, '请晚点送达')
  page.remarkInput({ detail: { value: '  ' } }); page.saveRemark()
  assert.equal(page.data.remark, ''); assert.equal(page.orderPayload({ id: '9' }).remark, undefined)
})
test('备注限制500字，提交中不可编辑，换号和离页草稿不写回', () => {
  const env = commerceEnv(), page = env.page('checkout')
  page.openRemark(); page.remarkInput({ detail: { value: '字'.repeat(501) } })
  assert.equal(page.data.remarkDraft.length, 500)
  env.token('other'); page.saveRemark(); assert.equal(page.data.remark, '')
  page.openRemark(); page.remarkInput({ detail: { value: '离页草稿' } }); page.onHide(); page.saveRemark()
  assert.equal(page.data.remark, ''); assert.equal(page.data.remarkEditorVisible, false)
  page.inactive = false; page.setData({ submitting: true }); page.openRemark(); assert.equal(page.data.remarkEditorVisible, false)
})
test('单行入口右侧箭头和无备注占位，只有弹框内有多行编辑器', () => {
  const view = readFileSync(new URL('../pages/checkout/index.wxml', import.meta.url), 'utf8')
  assert.match(view, /class="remark-value">\{\{remark \|\| '无备注'\}\}/)
  assert.match(view, /class="remark-arrow"/)
  assert.doesNotMatch(view.split('class="remark-overlay"')[0], /<textarea/)
  assert.match(view, /bindtap="cancelRemark"/); assert.match(view, /bindtap="saveRemark"/)
  assert.match(view, /<view class="remark-row"[^>]*aria-role="button"/)
  assert.doesNotMatch(view, /<button class="remark-row"/, '普通信息行不受微信原生按钮默认宽度影响')
  const css = readFileSync(new URL('../pages/checkout/index.wxss', import.meta.url), 'utf8')
  assert.match(css, /\.remark-row\s*\{[^}]*width: 100%;[^}]*margin: 20rpx 0 0;/)
})
