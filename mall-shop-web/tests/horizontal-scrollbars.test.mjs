import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const read = file => readFileSync(new URL(`../src/views/${file}`, import.meta.url), 'utf8')

test('H5工单及分类标签保留横滑，标准与WebKit滚动条均隐藏', () => {
  for (const [file, selector] of [['ServiceTicketsView.vue', 'status-tabs'], ['CategoryView.vue', 'guide-shelf-tabs']]) {
    const source = read(file)
    const rules = [...source.matchAll(new RegExp(`\\.${selector}\\s*\\{([^}]+)\\}`, 'g'))].map(match => match[1]).join(';')
    assert.match(rules, /overflow(?:-x)?:\s*auto/)
    assert.match(rules, /scrollbar-width:\s*none/)
    assert.doesNotMatch(rules, /overflow(?:-x)?:\s*(?:hidden|clip)/)
    assert.match(source, new RegExp(`\\.${selector}::-webkit-scrollbar\\s*\\{[^}]*display:\\s*none;[^}]*height:\\s*0;`))
    assert.match(source, new RegExp(`\\.${selector} button\\s*\\{[^}]*flex:\\s*0 0 auto`))
  }
})

test('工单末项筛选绑定不变，选择已关闭仍查询已关闭工单', async () => {
  const source = read('ServiceTicketsView.vue'), calls = []
  assert.match(source, /@click="changeStatus\(item.key\)"/)
  assert.match(source, /key: 'CLOSED', label: '已关闭'/)
  const state = { status: { value: '' }, loading: { value: false }, error: { value: '' }, pageNum: { value: 0 }, totalPage: { value: 1 }, tickets: { value: [] }, listServiceTickets: async params => { calls.push(params); return { data: { list: [], pageNum: 1, totalPage: 1 } } } }
  vm.runInNewContext(source.slice(source.indexOf('const load ='), source.indexOf('const loadContext =')) + '\nthis.change = changeStatus', state)
  state.change('CLOSED')
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(calls[0].status, 'CLOSED')
  assert.equal(state.status.value, 'CLOSED')
  assert.equal(state.loading.value, false)
})
