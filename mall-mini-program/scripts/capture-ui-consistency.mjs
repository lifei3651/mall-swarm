// Official native automation against build-ui-qa's isolated fixtures only.
import { createRequire } from 'node:module'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const require = createRequire(import.meta.url)
const sdk = require(process.env.MINI_AUTOMATOR_PATH || 'miniprogram-automator')
const output = resolve(process.argv[2])
await mkdir(output, { recursive: true })
const app = JSON.parse(await readFile(new URL('../app.json', import.meta.url)))
const queries = {product:'id=27', 'store-content':'type=culture', 'message-detail':'id=81', 'support-detail':'id=91', 'order-detail':'id=13201', 'order-review':'id=1&orderItemId=13202', 'after-sale':'orderId=13201',legal:'type=contact'}
const cases = app.pages.map(path=>{const name=path.split('/')[1];return [name,path+(queries[name]?'?'+queries[name]:'')]})
cases.push(['support-create','pages/support/index?create=1'],['support-empty','pages/support/index?create=1',{empty:true}],['support-error','pages/support/index?create=1',{failure:'/shop/orders'}],['address-edit','pages/address/index',{},'startAdd'],['payment-password','pages/account-settings/index?section=payment'],['real-name','pages/account-settings/index?section=identity'],['change-password','pages/account-security/index?mode=password'],['change-phone','pages/account-settings/index?section=phone'],['checkout-remark','pages/checkout/index',{},'openRemark'],['order-return','pages/order-detail/index?id=13201',{saleStatus:4}],['product-services','pages/product/index?id=1',{},'openServices'])
const selected=process.env.QA_CASES?cases.filter(c=>process.env.QA_CASES.split(',').includes(c[0])):cases
const mini=await sdk.connect({wsEndpoint:'ws://127.0.0.1:9420'})
const records=[]
try {
  if(await mini.evaluate(()=>wx.getStorageSync('mall_mini_access_token'))!=='LOCAL-UI-ONLY-NO-SERVER-AUTHORITY')throw Error('Not isolated QA')
  for(const [name,path,settings={},method] of selected){
    await mini.evaluate(v=>{wx.setStorageSync('qa-settings',v);wx.setStorageSync('mall_mini_access_token','LOCAL-UI-ONLY-NO-SERVER-AUTHORITY');wx.setStorageSync('mall_mini_member',{id:'132',nickname:'本地界面验收',username:'PreviewOnly',phone:'13800000000'})},settings)
    const page=await mini.reLaunch('/'+path)
    await page.waitFor(600)
    if(method){await page.callMethod(method);await page.waitFor(150)}
    const data=await page.data()
    const record={name,path,actual:(await mini.currentPage()).path,error:data.error||data.loadError||data.contextError||'',loading:data.loading||false,images:[]}
    await mini.screenshot({path:resolve(output,name+'.png')});record.images.push(name+'.png')
    if(data.error){await mini.native().confirmModal().catch(()=>{});await mini.screenshot({path:resolve(output,name+'-notice-dismissed.png')});record.images.push(name+'-notice-dismissed.png')}
    if(['support-create','support-empty','support-error','address-edit','after-sale','order-detail','order-return','checkout','profile','order-review','account-login'].includes(name)){
      await mini.pageScrollTo(99999);await page.waitFor(150);await mini.screenshot({path:resolve(output,name+'-bottom.png')});record.images.push(name+'-bottom.png')
    }
    if(name==='product'){const b=await page.$('.product-share-button');record.shareSize=await b.size()}
    records.push(record);console.log(JSON.stringify(record))
  }
}finally{await writeFile(resolve(output,'manifest.json'),JSON.stringify({kind:'isolated native simulator, synthetic fixtures, not real account/channel acceptance',records},null,2));mini.disconnect()}
