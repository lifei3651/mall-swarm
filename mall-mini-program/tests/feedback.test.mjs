import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync, readdirSync } from 'node:fs'
function helper(name, wx = {}) { const module = { exports: {} }; vm.runInNewContext(readFileSync(new URL(`../utils/${name}.js`, import.meta.url),'utf8'), { module, wx }); return module.exports }
test('错误提示为显眼的确认弹窗，去重并串行呈现，不自动消失', async () => {
  const dialogs = [], feedback = helper('feedback', { showModal: value => dialogs.push(value) })
  const first = feedback.notice('保存失败','请留意'), duplicate = feedback.notice('保存失败','请留意')
  const next = feedback.notice('头像上传失败','请留意')
  assert.equal(first,duplicate); assert.equal(dialogs.length,1); assert.equal(dialogs[0].showCancel,false)
  assert.equal(dialogs[0].content,'保存失败'); assert.equal(dialogs[0].confirmText,'知道了')
  dialogs[0].success({confirm:true}); await first; assert.equal(dialogs.length,2)
  dialogs[1].success({confirm:true}); await next
})
test('普通成功只做轻提示，不要求确认、不阻塞后续操作', async () => {
  const dialogs = [], tips = [], callbacks = []
  const feedback = helper('feedback', { showModal: value => { dialogs.push(value); value.success({ confirm: true }) }, showToast: value => tips.push(value) })
  const result = await feedback.toast({ title: '设置已保存', icon: 'success', success: () => callbacks.push('success'), complete: () => callbacks.push('complete') })
  assert.equal(dialogs.length, 0); assert.equal(tips.length, 1)
  assert.equal(tips[0].title, '设置已保存'); assert.equal(tips[0].mask, false)
  assert.ok(tips[0].duration <= 1800); assert.notEqual(result.confirm, true)
  assert.deepEqual(callbacks, ['success', 'complete'])
})
test('页面成功文案及登录引导只更新页面，错误仍弹窗且保留回调', () => {
  const dialogs = [], feedback = helper('feedback', { showModal: value => dialogs.push(value) })
  let called = false
  const page = { data: {}, setData(patch, callback) { Object.assign(this.data, patch); callback?.() } }
  feedback.update(page, { message: '昵称已保存', loginNotice: '请通过手机号继续登录' }, () => { called = true })
  assert.equal(called, true); assert.equal(dialogs.length, 0)
  assert.equal(page.data.message, '昵称已保存'); assert.equal(page.data.loginNotice, '请通过手机号继续登录')
  feedback.update(page, { error: '保存失败', message: '旧成功文案' })
  assert.equal(dialogs.length, 1); assert.equal(dialogs[0].content, '保存失败')
})
test('轻提示不能覆盖错误弹窗，也不排队到错误关闭后再提示成功', async () => {
  const dialogs = [], tips = []; let hiddenTips = 0
  const feedback = helper('feedback', { showModal: value => dialogs.push(value), showToast: value => tips.push(value), hideToast: () => hiddenTips++ })
  await feedback.success('已保存')
  const failure = feedback.notice('网络异常', '请留意')
  await feedback.success('已更新')
  assert.equal(hiddenTips, 1); assert.equal(tips.length, 1)
  dialogs[0].success({ confirm: true }); await failure
  assert.equal(tips.length, 1); assert.equal(dialogs.length, 1)
})
test('空成功提示及提示组件不可用不能让已经成功的业务变成失败', async () => {
  const feedback = helper('feedback', { showToast() { throw new Error('提示组件不可用') } })
  assert.equal((await feedback.success('')).shown, false)
  assert.equal((await feedback.success('已保存')).shown, false)
  assert.equal((await helper('feedback').success('已保存')).shown, false)
})
test('错误兼容入口仍等待用户确认，不降级为自动消失的轻提示', async () => {
  const dialogs=[],tips=[];let completed=false
  const feedback=helper('feedback',{showModal:value=>dialogs.push(value),showToast:value=>tips.push(value)})
  const pending=feedback.toast({title:'保存失败，请重试',icon:'none',complete:()=>{completed=true}})
  await Promise.resolve()
  assert.equal(dialogs.length,1);assert.equal(tips.length,0);assert.equal(completed,false)
  dialogs[0].success({confirm:true});await pending;assert.equal(completed,true)
})
test('页面普通成功不得重新接入确认弹窗，地址导入和保存不发成功提示', () => {
  for (const dir of ['pages','utils']) {
    const root=new URL(`../${dir}/`,import.meta.url)
    for (const entry of readdirSync(root,{recursive:true}).filter(name=>name.endsWith('.js'))) {
      const source=readFileSync(new URL(entry,root),'utf8')
      assert.doesNotMatch(source,/feedback\.notice\([^\n]*,\s*['"](?:操作完成|操作结果|设置完成|预约结果|验证码已发送|支付成功)['"]/u,entry)
    }
  }
  const address=readFileSync(new URL('../pages/address/index.js',import.meta.url),'utf8')
  assert.doesNotMatch(address,/feedback\.success|icon:\s*'success'|微信地址已导入/)
})
test('清空提示不弹窗，后台或离开页面不弹迟到信息，保留就地重试状态', () => {
  const dialogs=[], feedback=helper('feedback',{showModal:x=>dialogs.push(x)})
  const page={ data:{},setData(p){Object.assign(this.data,p)} }
  feedback.update(page,{error:'',message:''}); assert.equal(dialogs.length,0)
  page.hidden=true; feedback.update(page,{error:'晚到错误'}); assert.equal(dialogs.length,0)
  page.hidden=false; feedback.update(page,{error:'上传失败'}); assert.equal(dialogs.length,1); assert.equal(page.data.error,'上传失败')
})
test('头像错误区分合法域名、超时、TLS与失效文件，不输出原始载荷', () => {
  const {transportError,statusError}=helper('transport-error')
  assert.match(transportError({errMsg:'uploadFile:fail url not in domain list'},'头像上传','uploadFile'), /uploadFile 合法域名/)
  assert.match(transportError({errMsg:'downloadFile:fail timeout'},'头像预览','downloadFile'), /超时/)
  assert.match(transportError({errMsg:'SSL certificate invalid'}), /证书/)
  assert.match(transportError({errMsg:'ENOENT'}), /重新选择/)
  assert.match(statusError(413), /2MB/); assert.match(statusError(503), /服务暂不可用/)
  assert.doesNotMatch(transportError({errMsg:'secret=do-not-show'}), /secret/)
})
test('售后、物流和奖金等专用错误也不能只出现在页面底部', () => {
  for (const key of ['submitError','shipmentError','loadError','bonusError']) {
    const dialogs=[],feedback=helper('feedback',{showModal:x=>dialogs.push(x)})
    feedback.update({setData(){}},{[key]:'操作未完成，请重试'})
    assert.equal(dialogs.length,1);assert.equal(dialogs[0].content,'操作未完成，请重试')
  }
})
