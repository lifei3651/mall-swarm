// Official native SDK only; requires the isolated, network-blocked QA project.
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const require = createRequire(import.meta.url)
const sdk = require(process.env.MINI_AUTOMATOR_PATH || '/private/tmp/lingqi-native-qa-tools.d9E1K0/node_modules/miniprogram-automator')
const output = resolve(process.argv[2] || 'document/qa/2026-09-08-parity-completion/390')
await mkdir(output, { recursive: true })
const mini = await sdk.connect({ wsEndpoint: 'ws://127.0.0.1:9420' })
const info = await mini.systemInfo()
const local = await mini.evaluate(() => wx.getStorageSync('mall_mini_access_token'))
if (local !== 'LOCAL-UI-ONLY-NO-SERVER-AUTHORITY') { mini.disconnect(); throw Error('Not the isolated local QA account') }
const events = []
mini.on('exception', error => events.push(String(error)))
const cases = [
  ['home','home'], ['category','category'], ['product','product?id=27'], ['cart','cart'],
  ['profile','profile'], ['account-security','account-security'], ['account-settings','account-settings'],
  ['messages','messages'], ['message-detail','message-detail?id=81'], ['support','support'], ['support-detail','support-detail?id=91'],
  ['subscriptions','subscriptions'], ['payout','payout'], ['wallet','wallet'], ['withdraw','withdraw'],
  ['orders','orders'], ['order-detail','order-detail?id=13201'], ['after-sale','after-sale?orderId=13201'],
  ['checkout','checkout'], ['address','address'], ['notices','notices'], ['campaign','campaign'],
  ['live','store-content?type=live&id=71'], ['live-square','store-content?type=live'], ['new-arrivals','store-content?type=newArrivals'], ['brand','store-content?type=culture'],
  ['legal','legal?type=privacy'], ['live-studio','live-studio'],
  ['account-login','account-login?mode=password'], ['sms-login','account-login?mode=sms'], ['register','account-login?mode=register'], ['reset','account-login?mode=reset'], ['login','login'],
  ...['standard','product-focus','category-focus','campaign-feed'].map(layout=>['layout-'+layout,'home',{layout}]),
  ...['directory','showcase','scenario'].map(guide=>['guide-'+guide,'category',{layout:'category-focus',guide}]),
  ['category-empty','category',{empty:true}], ['orders-empty','orders',{empty:true}], ['home-error','home',{failure:'/shop/home'}],
  ['order-rejected','order-detail?id=13201',{saleStatus:2}], ['order-return','order-detail?id=13201',{saleStatus:4}],
  ['studio-no-role','live-studio',{noAnchor:true}], ['profile-guest','profile',{guest:true}],
  ['change-phone','account-settings?section=phone'], ['change-password','account-security?mode=password'],
  ['payment-password','account-settings?section=payment'], ['real-name','account-settings?section=identity']
]
const selected = process.env.QA_CASES ? cases.filter(item => process.env.QA_CASES.split(',').includes(item[0])) : cases
const records = []
try {
  // Only deterministic local cart state. No backend API is involved.
  await mini.evaluate(() => {
    wx.removeStorageSync('mall_mini_cart')
    wx.setStorageSync('mall_mini_cart_v2:member:132', [{ key:'1:11', productId:'1', skuId:'11', productName:'本地商品', salePrice:99, quantity:1, selected:true }])
  })
  for (const [name, route, settings={}] of selected) {
    await mini.evaluate(value => wx.setStorageSync('qa-settings', value), settings)
    const guest = settings.guest || ['account-login','sms-login','register','reset','login'].includes(name)
    await mini.evaluate(value => {
      if(value) {wx.removeStorageSync('mall_mini_access_token');wx.removeStorageSync('mall_mini_member')}
      else {wx.setStorageSync('mall_mini_access_token','LOCAL-UI-ONLY-NO-SERVER-AUTHORITY');wx.setStorageSync('mall_mini_member',{id:'132',nickname:'本地界面验收',username:'PreviewOnly',phone:'13800000000'})}
    },guest)
    if (name.startsWith('guide-')) { await mini.reLaunch('/pages/home/index'); await (await mini.currentPage()).waitFor(300) }
    const [pageName, query] = route.split('?')
    const page = await mini.reLaunch('/pages/'+pageName+'/index'+(query?'?'+query:''))
    await page.waitFor(500)
    const actual = (await mini.currentPage()).path.replace(/^\//,'')
    if (actual !== 'pages/'+pageName+'/index') throw Error('Native page changed during capture: '+name+' → '+actual)
    const data = await page.data()
    await mini.screenshot({ path:resolve(output,name+'.png') })
    records.push({name, route, actualPath:actual, settings, error:data.error||data.loadError||data.quoteError||'', title:data.pageTitle||'', loading:data.loading, screenshot:name+'.png'})
    console.log(name, records.at(-1).error)
    // Dismiss local feedback so the next page is not masked by an old dialog.
    if (records.at(-1).error) {
      await mini.native().confirmModal().catch(()=>{})
      await page.waitFor(300)
      await mini.screenshot({path:resolve(output,name+'-after-notice.png')})
    }
    if (['product','order-detail','order-rejected','order-return','after-sale','register','reset','checkout','home','guide-directory','guide-showcase','guide-scenario'].includes(name)) {
      if (name.startsWith('guide-')) await (await page.$('.category-products')).scrollTo(0,99999)
      else await mini.pageScrollTo(99999)
      await page.waitFor(200)
      await mini.screenshot({path:resolve(output,name+'-bottom.png')})
    }
  }
} finally {
  await writeFile(resolve(output,'manifest.json'), JSON.stringify({capturedAt:new Date().toISOString(),systemInfo:info,kind:'isolated native simulator; synthetic read-only fixtures; not real channels',records,events},null,2))
  mini.disconnect()
}
process.exit(0)
