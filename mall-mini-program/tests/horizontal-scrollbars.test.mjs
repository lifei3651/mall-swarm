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
  for (const page of ['support/index.wxml', 'home/index.wxml', 'orders/index.wxml']) assert.ok(horizontalPages.includes(page))
})

test('工单和新品仅对横滑容器提供滚动条样式回退，不全局裁剪页面', () => {
  for (const [path, selector] of [['../styles/support.wxss', 'support-tabs'], ['../pages/home/index.wxss', 'arrival-scroll']]) {
    const css = read(path)
    assert.match(css, new RegExp(`\\.${selector}::-webkit-scrollbar\\s*\\{[^}]*display:\\s*none;[^}]*height:\\s*0;`))
    assert.doesNotMatch(css, /(?:page|scroll-view)\s*\{[^}]*overflow(?:-x)?:\s*hidden/)
  }
})

test('横滑最末端已关闭工单筛选仍可选择并请求原接口', async () => {
  const env = commerceEnv(() => ({ list: [], totalPage: 1 })), page = env.page('support')
  assert.equal(page.data.filters.at(-1).key, 'CLOSED')
  page.selectStatus({ currentTarget: { dataset: { key: 'CLOSED' } } })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(page.data.status, 'CLOSED')
  assert.equal(env.calls[0].url, '/shop/service-tickets')
  assert.equal(env.calls[0].params.status, 'CLOSED')
  assert.equal(page.data.loading, false)
})
