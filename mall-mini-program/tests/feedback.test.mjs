import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
function helper(name, wx = {}) { const module = { exports: {} }; vm.runInNewContext(readFileSync(new URL(`../utils/${name}.js`, import.meta.url),'utf8'), { module, wx }); return module.exports }
test('操作提示为显眼的确认弹窗，去重并串行呈现，不自动消失', async () => {
  const dialogs = [], feedback = helper('feedback', { showModal: value => dialogs.push(value) })
  const first = feedback.notice('保存失败','请留意'), duplicate = feedback.notice('保存失败','请留意')
  const next = feedback.notice('昵称已保存','操作结果')
  assert.equal(first,duplicate); assert.equal(dialogs.length,1); assert.equal(dialogs[0].showCancel,false)
  assert.equal(dialogs[0].content,'保存失败'); assert.equal(dialogs[0].confirmText,'知道了')
  dialogs[0].success({confirm:true}); await first; assert.equal(dialogs.length,2)
  dialogs[1].success({confirm:true}); await next
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
