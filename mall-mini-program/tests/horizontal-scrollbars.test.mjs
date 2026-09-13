import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { commerceEnv } from './helpers/commerce-env.mjs'

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')

test('所有原生横滑容器隐藏滚动条，但不禁用横向滑动', () => {
  const pages = new URL('../pages/', import.meta.url)
  const horizontalPages = []
  for (const file of readdirSync(pages, { recursive: true }).filter(file => file.endsWith('.wxml'))) {
    const source = readFileSync(new URL(file, pages), 'utf8')
    for (const tag of source.match(/<scroll-view\b[^>]*>/g) || []) {
      if (!/\bscroll-x(?:\s|=|>)/.test(tag)) continue
      horizontalPages.push(file)
      assert.match(tag, /\bscroll-x(?:\s|>)/, `${file} preserves scrolling`)
      assert.match(tag, /\benhanced(?:\s|>)/, `${file} enables scrollbar control`)
      assert.match(tag, /show-scrollbar="\{\{false\}\}"/, `${file} hides the track`)
    }
  }
  for (const page of ['home/index.wxml', 'orders/index.wxml']) assert.ok(horizontalPages.includes(page))
})

test('新品仅对横滑容器提供滚动条样式回退，不全局裁剪页面', () => {
  for (const [path, selector] of [['../pages/home/index.wxss', 'arrival-scroll']]) {
    const css = read(path)
    assert.match(css, new RegExp(`\\.${selector}::-webkit-scrollbar\\s*\\{[^}]*display:\\s*none;[^}]*height:\\s*0;`))
    assert.doesNotMatch(css, /(?:page|scroll-view)\s*\{[^}]*overflow(?:-x)?:\s*hidden/)
  }
})

test('工单六种状态全部显示在三列网格，不再使用原生横滑容器', () => {
  const view = read('../pages/support/index.wxml'), css = read('../styles/support.wxss')
  assert.doesNotMatch(view, /<scroll-view/)
  assert.match(view, /<view class="support-tabs"/)
  assert.match(view, /aria-pressed="\{\{status === item.key\}\}"/)
  assert.match(css, /\.support-tabs\s*\{[^}]*display:grid;[^}]*grid-template-columns:repeat\(3,minmax\(0,1fr\)\)/)
  assert.doesNotMatch(css, /overflow(?:-x)?:\s*(auto|scroll)|white-space:nowrap/)
  const page = commerceEnv(() => ({})).page('support')
  assert.equal(page.data.filters.length, 6)
})

test('已关闭工单筛选仍可选择并请求原接口', async () => {
  const env = commerceEnv(() => ({ list: [], totalPage: 1 })), page = env.page('support')
  assert.equal(page.data.filters.at(-1).key, 'CLOSED')
  page.selectStatus({ currentTarget: { dataset: { key: 'CLOSED' } } })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(page.data.status, 'CLOSED')
  assert.equal(env.calls[0].url, '/shop/service-tickets')
  assert.equal(env.calls[0].params.status, 'CLOSED')
  assert.equal(page.data.loading, false)
})
