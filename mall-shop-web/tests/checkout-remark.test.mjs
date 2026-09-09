import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
const source = readFileSync(new URL('../src/views/CheckoutView.vue', import.meta.url), 'utf8')
function harness() {
  let member = 'member'
  const form = { value: { remark: '' } }, submitting = { value: false }, pendingCheckoutId = { value: '' }
  const context = { form, submitting, pendingCheckoutId, ref: value => ({ value }), nextTick: callback => Promise.resolve(callback?.()), localStorage: { getItem: () => member } }
  const script = source.slice(source.indexOf('const remarkEditorVisible'), source.indexOf('const openAddressPage'))
  vm.runInNewContext(script + '\nthis.page = { openRemark, cancelRemark, saveRemark, remarkDraft, remarkEditorVisible }', context)
  return { ...context, member: value => { member = value } }
}
test('H5备注与小程序相同：草稿取消保留原值、确认保存、清空恢复默认', async () => {
  const h = harness(), p = h.page
  await p.openRemark(); p.remarkDraft.value = '草稿'; p.cancelRemark(); assert.equal(h.form.value.remark, '')
  await p.openRemark(); p.remarkDraft.value = '  请晚点送达  '; p.saveRemark(); assert.equal(h.form.value.remark, '请晚点送达')
  await p.openRemark(); assert.equal(p.remarkDraft.value, '请晚点送达'); p.remarkDraft.value = ''; p.saveRemark()
  assert.equal(h.form.value.remark, '')
})
test('H5备注字数、提交锁及换号保护，键盘退出和焦点约束保留', async () => {
  const h = harness(), p = h.page
  await p.openRemark(); p.remarkDraft.value = '字'.repeat(501); p.saveRemark(); assert.equal(h.form.value.remark.length, 500)
  await p.openRemark(); p.remarkDraft.value = '其他账号'; h.member('other'); p.saveRemark(); assert.equal(h.form.value.remark.length, 500)
  h.submitting.value = true; await p.openRemark(); assert.equal(p.remarkEditorVisible.value, false)
  assert.match(source, /form.remark \|\| '无备注'/)
  assert.match(source, /event.key === 'Escape'/); assert.match(source, /event.key !== 'Tab'/)
  assert.match(source, /remark: form.value.remark/)
})
