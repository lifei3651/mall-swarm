const titles = { agreement: '用户服务协议', privacy: '隐私政策', 'after-sale': '交易与售后规则', faq: '常见问题', license: '经营资质', contact: '联系客服' }
const miniPrivacy = '微信资料便捷填写补充说明\n\n1. 头像、昵称均为可选资料。仅在你主动选择后读取；选择头像即提交更新，昵称须点击保存才提交。头像保存在账号专属的非公开目录，仅本人登录后可读取。昵称用于账号识别；发布商品评价时按商城规则脱敏展示。\n\n2. 微信收货地址仅在点击导入并由你选择后读取收货人姓名、电话及省市区、街道门牌信息。先回填表单，经你核对并点击保存才提交商城，不自动覆盖已有或默认地址。为配送订单，必要收货信息会提供给履约商家和实际承运服务商，具体接收方及处理规则以商城完整隐私政策及订单为准。\n\n3. 微信手机号仅在你主动授权快捷注册或绑定时读取。拒绝头像、昵称或地址便捷授权不影响浏览；昵称及地址可手动填写。不自动获取精确位置，不持续定位，也不批量读取微信通讯录、相册或地址簿。\n\n4. 售后图片由你主动选择后，在提交申请时上传，用于核实订单售后问题。仅本人和获授权处理该订单的后台人员可访问，请勿上传无关人员、证件或其他非必要敏感信息。\n\n5. 图片和资料通过HTTPS传输，密码与验证码另经商城统一敏感字段加密处理。头像使用固定文件名覆盖，每个账号至多保留JPG、PNG各一份；客户端临时预览在离开资料页后尝试清理。账号及交易资料按下方政策和法定必要期限保存。需要查阅、更正、删除或撤回授权，可在微信小程序设置中管理权限，并联系商城客服处理已保存资料。撤回微信授权不等于自动删除商城中已保存的订单或地址。'
const miniClientNotice = '\n\n6. 本小程序使用小程序本地缓存保存登录凭据、必要的购物车和页面设置。退出登录会清理当前会话；清理小程序缓存可能需要重新登录，未提交内容可能丢失。下方商城通用政策中的浏览器、Cookie及网页设置描述适用于网页端，不表示小程序会读取浏览器数据。\n\n7. 小程序可在“我的”账号资料入口修改头像、昵称，在“收货地址”管理配送信息。换绑手机号、注销账号及其他尚无自助入口的请求需联系客服，经必要身份核验后处理。通用政策列举的其他终端功能不代表本小程序已提供对应入口。微信登录、手机号快捷注册及订阅提醒由微信（腾讯）提供相关能力，只有在相应功能就绪且你主动操作时使用；微信平台隐私指引应与实际启用的能力一致。'
function contactValue(value) { return typeof value === 'string' ? value.trim() : '' }
function resolveText(value, config = {}) {
  const replacements = {
    companyName: config.companyName || '本商城经营主体', brandName: config.brandName || '本商城',
    unifiedSocialCreditCode: config.unifiedSocialCreditCode || '以营业执照公示信息为准',
    companyAddress: config.companyAddress || '以经营资质公示信息为准', servicePhone: contactValue(config.servicePhone) || '暂未配置',
    serviceEmail: contactValue(config.serviceEmail) || '暂未配置', serviceHours: config.serviceHours || '以客服实际在线时间为准',
    thirdPartyServices: config.thirdPartyServices || '支付、短信、云服务及订单实际承运的物流服务商'
  }
  return String(value || '').replace(/\{\{\s*(\w+)\s*\}\}|\{(company|phone|email)\}/g, (match, key, legacy) => replacements[key || ({ company: 'companyName', phone: 'servicePhone', email: 'serviceEmail' })[legacy]] || match)
}
function content(type, config = {}) {
  return resolveText(config[({ agreement: 'userAgreement', privacy: 'privacyPolicy', 'after-sale': 'afterSalePolicy' })[type]], config)
}
function faqs(config = {}) {
  try {
    const items = typeof config.faqs === 'string' ? JSON.parse(config.faqs) : config.faqs
    return (Array.isArray(items) ? items : []).filter((item) => item && String(item.question || '').trim() && String(item.answer || '').trim())
      .map((item) => ({ question: String(item.question), answer: resolveText(item.answer, config) }))
  } catch (_) { return [] }
}
const miniInviteNotice = '\n\n8. 邀请功能为可选。小程序会暂存你从分享、扫码或手动填写获得的邀请码及来源，用于首次注册时核对邀请人；有效缓存最长24小时，登录成功、退出登录或主动选择不使用邀请时清理。收到不同邀请会提示选择，不会自动替换；过期后需重新核对或明确选择不使用邀请。首次注册成立的邀请关系由商城服务端保存，缓存清理不删除已成立的关系，已有账号不会因收到分享重新绑定。具备邀请资格的账号主动分享商城或商品时，会在分享路径中携带本人的公开邀请码，接收人可核对邀请人的公开昵称。分享不包含手机号、登录凭据或团队明细。'
const accountNotice = '\n\n9. 账号安全自助功能：更换绑定手机号时，需分别验证新旧手机号；验证码仅用于本次身份校验，不用于营销。支付密码为独立的6位密码，设置或修改时需再次验证登录密码或原支付密码及绑定手机号验证码。\n\n10. 实名认证仅在你主动填写姓名、身份证号并单独勾选实名认证授权后提交，核验通道未启用时不会提交。上述敏感信息用于核验本人身份和资金操作所需的成年条件，不用于商品推荐；通过HTTPS及商城敏感字段加密传输，服务端保存和核验服务接收方以商城完整隐私政策及实际启用的核验服务为准。客户端不将姓名、身份证号、密码、验证码写入本地持久缓存，离开表单后清空；成功后仅展示脱敏状态。拒绝实名授权不影响浏览及不要求实名的功能，但无法完成需要实名认证的资金操作。'
const liveNotice = '\n\n11. 直播互动：你主动预约或取消预约时，商城保存或更新本账号的直播预约记录；你主动发表评论后，评论内容和商城提供的显示名称会在直播间展示给其他观看者，请勿发布手机号、证件或其他无关个人信息。进入直播间时，小程序生成并在本地保存随机访客标识，随进入、停留、离开、分享和商品点击事件发送直播间编号、停留时长及相关商品编号，用于直播观看与互动统计，不读取微信通讯录或精确位置。登录状态下，商城服务端可能同时识别当前账号，该标识不代表不可关联个人的匿名数据。你可清理小程序缓存重置本地标识；服务端记录的保存和删除按商城完整隐私政策办理。'
const nativeAccountNotice = '\n\n12. 其他方式登录、注册及找回密码：仅在你主动提交时使用填写的账号、手机号、密码、图形验证码及短信验证码完成身份核验，复用商城同一会员及邀请码规则；不会自动读取微信手机号或更换已成立的邀请关系。密码及验证码不写入本地持久缓存，切换到后台或离开表单时清空。账号登录不会静默绑定或覆盖微信身份。\n\n13. 主播工作台仅供商城已授权的本人账号管理直播间，不自动开启摄像头或麦克风。短时推流信息只在当前页面临时保留，切后台或离页清空；仅在你确认复制时写入系统剪贴板，用于粘贴到你信任的外部直播工具，请勿转发。直播服务接收方以商城已配置的服务和完整隐私政策为准。'
module.exports = { titles, resolveText, content, faqs, miniPrivacy: miniPrivacy + miniClientNotice.replace('换绑手机号、注销账号及其他尚无自助入口的请求需联系客服', '换绑手机号可在账号资料内自助办理；旧手机号已停用、注销账号及其他尚无自助入口的请求需联系客服') + miniInviteNotice + accountNotice + liveNotice + nativeAccountNotice, contactValue }
