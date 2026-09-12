import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
const source = readFileSync(new URL('../src/views/ProductDetailView.vue', import.meta.url), 'utf8')
function harness(items = [{ title: '七天无理由', description: '原服务条件' }]) {
  const body = { style: { overflow: 'auto' } }, loading = { value: false }
  let focused = 0, shows = 0
  const dialog = { open: false, showModal() { this.open = true; shows++ }, close() { this.open = false } }
  const context = { loading, serviceGuarantees: { value: items }, document: { body }, ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }) }
  const script = source.slice(source.indexOf('const guaranteeSummary'), source.indexOf('const selectedSkuAttributeEntries'))
  vm.runInNewContext(script + '\nthis.ui = { guaranteeSummary, guaranteesOpen, guaranteesDialog, guaranteesTrigger, openGuarantees, closeGuarantees, finishGuarantees, dismissGuaranteesBackdrop }', context)
  context.ui.guaranteesDialog.value = dialog
  context.ui.guaranteesTrigger.value = { isConnected: true, focus() { focused++ } }
  return { ...context, dialog, body, get focused() { return focused }, get shows() { return shows } }
}
test('H5保障单行入口，长文只在对话框内完整展示，不改原保障解析', () => {
  const row = source.slice(source.indexOf('<section v-if="serviceGuarantees.length"'), source.indexOf('<dialog ref="guaranteesDialog"'))
  assert.match(row, /guarantee-summary-row/); assert.match(row, /aria-haspopup="dialog"/)
  assert.doesNotMatch(row, /description|guarantee-caption|guarantee-icon/)
  assert.match(source, /min-height:52px/)
  assert.match(source, /text-overflow:ellipsis; white-space:nowrap/)
  assert.match(source, /@cancel.prevent="closeGuarantees"/)
  assert.match(source, /max-height:56vh; overflow-y:auto/)
  assert.match(source, /white-space:pre-wrap; overflow-wrap:anywhere/)
  assert.match(source, /filter\(\(item\) => item.enabled && item.title\)/)
  assert.match(source, /const fetchProduct = async \(\) => \{\s+closeGuarantees\(\)/)
  assert.match(source, /onBeforeUnmount\(\(\) => \{ closeGuarantees\(\)/)
})
test('只有主动点击打开，重复点击不重复打开；关闭恢复页面滚动及入口焦点', () => {
  const h = harness(), p = h.ui
  assert.equal(p.guaranteesOpen.value, false)
  assert.equal(p.guaranteeSummary.value, '七天无理由')
  p.openGuarantees(); p.openGuarantees()
  assert.equal(h.shows, 1); assert.equal(h.body.style.overflow, 'hidden')
  p.dismissGuaranteesBackdrop({ target: {} }); assert.equal(p.guaranteesOpen.value, true)
  p.dismissGuaranteesBackdrop({ target: h.dialog })
  assert.equal(p.guaranteesOpen.value, false); assert.equal(h.body.style.overflow, 'auto'); assert.equal(h.focused, 1)
  p.finishGuarantees(); assert.equal(h.focused, 1)
  p.openGuarantees(); p.finishGuarantees() // delayed close from the previous opening
  assert.equal(p.guaranteesOpen.value, true); assert.equal(h.body.style.overflow, 'hidden')
  p.closeGuarantees(); assert.equal(h.body.style.overflow, 'auto')
})
test('空配置/加载中不打开；所有服务标题按原顺序，不删完整说明', () => {
  const h = harness([])
  h.ui.openGuarantees(); assert.equal(h.shows, 0)
  const items = Array.from({ length: 8 }, (_, i) => ({ title: `保障${i}`, description: '长条件\n'.repeat(60) }))
  h.serviceGuarantees.value = items; h.loading.value = true
  h.ui.openGuarantees(); assert.equal(h.shows, 0)
  h.loading.value = false; h.ui.openGuarantees()
  assert.equal(h.ui.guaranteeSummary.value, items.map(item => item.title).join(' · '))
  assert.deepEqual(h.serviceGuarantees.value, items)
})
