import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
const source=readFileSync(new URL('../src/views/ServiceTicketsView.vue',import.meta.url),'utf8')
test('工单选择保持原生键盘控件、箭头、禁用态及通栏排版',()=>{
  assert.equal((source.match(/<select /g)||[]).length,3)
  assert.equal((source.match(/<ChevronRight :size="16" aria-hidden="true"/g)||[]).length,3)
  assert.match(source,/v-if="orders.length && !contextLoading"/)
  assert.match(source,/暂无可关联订单/)
  assert.match(source,/\.support-page \.section-head[^}]*flex-direction:row/)
  assert.match(source,/\.support-page \.cancel-button[^}]*min-width:44px; min-height:44px/)
  assert.match(source,/:disabled="submitting" @click="creating=false"/)
})
test('关联订单失败明确显示错误，不伪装为无订单；重试期间避免重复加载',async()=>{
  const code=source.match(/const loadContext = async \(\) => \{[\s\S]*?\n\}/)[0]
  let reject, calls=0
  const state={contextLoading:{value:false},contextError:{value:''},legal:{value:{}},orders:{value:[]},getLegalConfig:async()=>({data:{}}),listMyOrders:()=>{calls++;return new Promise((_,r)=>{reject=r})}}
  const load=vm.runInNewContext(code+';loadContext',state)
  const pending=load();await load();assert.equal(calls,1);assert.equal(state.contextLoading.value,true)
  reject(Error('unavailable'));await pending
  assert.match(state.contextError.value,/加载失败/);assert.equal(state.contextLoading.value,false)
})
test('窄窗口预留纵向滚动条后不再被 body 最小宽度撑出横向滚动',()=>{
  const css=readFileSync(new URL('../src/assets/styles.css',import.meta.url),'utf8')
  assert.match(css,/body\s*\{[^}]*min-width:\s*0;/)
})
