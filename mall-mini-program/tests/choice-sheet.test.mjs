import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runMiniScript } from './helpers/run-mini-script.mjs'

const source = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')

function sheet(properties = {}) {
  let definition
  runMiniScript(source('components/choice-sheet/index.js'), { Component(value) { definition = value } })
  const events = []
  const defaults = Object.fromEntries(Object.entries(definition.properties).map(([name, config]) => [name, config.value]))
  const instance = {
    data: { ...defaults, ...definition.data, ...properties },
    setData(patch) { Object.assign(this.data, patch) },
    triggerEvent(name, detail) { events.push({ name, detail }) },
    ...definition.methods
  }
  return { instance, events, definition }
}

test('共用选择面板从底部展开，取消和遮罩关闭均保留原值', () => {
  const { instance, events } = sheet({ options: ['不想要了', '与商品描述不符'], selectedIndex: 1 })
  instance.open()
  assert.equal(instance.data.visible, true)
  assert.equal(instance.data.listHeight, 192)
  instance.close()
  assert.equal(instance.data.visible, false)
  assert.equal(instance.data.selectedIndex, 1)
  assert.deepEqual(events, [])
})

test('长选项表保持滚动高度，选中仅回传合法索引，禁用时不能改选', () => {
  const options = Array.from({ length: 50 }, (_, index) => ({ label: `订单 ${index}` }))
  const { instance, events } = sheet({ options })
  instance.open()
  assert.equal(instance.data.listHeight, 640)
  instance.choose({ currentTarget: { dataset: { index: 49 } } })
  assert.equal(events.length, 1)
  assert.equal(events[0].name, 'change')
  assert.equal(events[0].detail.value, '49')
  assert.equal(instance.data.visible, false)
  instance.open()
  instance.choose({ currentTarget: { dataset: { index: 50 } } })
  assert.equal(instance.data.visible, true)
  instance.data.disabled = true
  instance.choose({ currentTarget: { dataset: { index: 0 } } })
  assert.equal(events.length, 1)
  instance.close()
  instance.open()
  assert.equal(instance.data.visible, false)
})

test('售后、退货快递和客服选择统一使用底部面板，不再落入窄原生弹层', () => {
  const view = source('components/choice-sheet/index.wxml')
  const style = source('components/choice-sheet/index.wxss')
  assert.match(view, /class="choice-backdrop" bindtap="close"/)
  assert.match(view, /scroll-view class="choice-options" scroll-y/)
  assert.match(style, /\.choice-panel \{[^}]*right:0; bottom:0; left:0;/)
  for (const page of ['after-sale', 'order-detail', 'support', 'campaign']) {
    const form = source(`pages/${page}/index.wxml`)
    const config = JSON.parse(source(`pages/${page}/index.json`))
    assert.doesNotMatch(form, /<picker\b/)
    assert.match(form, /<choice-sheet\b/)
    assert.equal(config.usingComponents['choice-sheet'], '/components/choice-sheet/index')
  }
})
