// 只索引已存在的配置入口；权限仍由原页面和服务端执行。
export const SETTINGS_GROUPS = [
  { key: 'base', title: '商城基础', description: '经营主体、资质与公开协议。' },
  { key: 'appearance', title: '品牌与页面', description: '统一商城品牌、主题与页面展示。' },
  { key: 'finance', title: '余额与提现', description: '集中查看资金相关入口与待接入能力，不改变现行资金规则。' },
  { key: 'marketing', title: '优惠与营销', description: '优惠券、秒杀与消息运营；发行和活动管理在原业务页面完成。' },
  { key: 'team', title: '会员与奖金', description: '推广资格、业务模式、奖金接入与会员端查看权限。' },
  { key: 'service', title: '客服与售后', description: '客服渠道、售后地址与问题处理。' },
  { key: 'integration', title: '服务与对接', description: 'ERP与物流配置；支付、短信、实名通道需配套部署。' },
  { key: 'security', title: '账号与安全', description: '后台账号权限与操作追溯。' },
]

export const SETTINGS_ENTRIES = [
  { group: 'base', title: '经营主体与商城资料', description: '名称、经营地址、营业执照与备案资料', path: '/tenant/profile', permission: 'config:shop', editor: true },
  { group: 'base', title: '协议与规则', description: '用户协议、隐私政策及商城规则', path: '/tenant/legal', permission: 'config:shop', editor: true },
  { group: 'appearance', title: '商城视觉与页面', description: '品牌标识、主题颜色、首页、分类及商品详情版型', path: '/tenant/list', permission: 'config:shop', editor: true },
  { group: 'finance', title: '余额与提现规则', description: '集中设置余额持有人资格、人工余额、银行卡和线下打款', path: '/withdraw/settings', permission: 'finance:manage', editor: true },
  { group: 'finance', title: '会员余额账户', description: '查看账户与余额来源', path: '/account/list', permission: 'distribution:manage' },
  { group: 'finance', title: '余额流水', description: '查看余额变化与资金来源', path: '/account/flows', permission: 'finance:read' },
  { group: 'finance', title: '提现审核', description: '处理现有提现申请，不等同于线下转账后标记已打款', path: '/withdraw/audit', permission: 'finance:manage' },
  { group: 'finance', title: '提现记录', description: '查询审核状态及现有打款处理结果', path: '/withdraw/list', permission: 'finance:read' },
  { group: 'finance', title: '商户货款与总账', description: '商家结算、货款、发票与财务处理', path: '/audit/merchant-finance', permission: 'finance:read' },
  { group: 'marketing', title: '优惠券规则与发行', description: '平台或指定商家、全部或指定商品；设置成本承担、奖金与退券规则', path: '/shop/coupons', permission: 'config:shop' },
  { group: 'marketing', title: '秒杀活动', description: '活动商品、价格、库存、时间与限购', path: '/tenant/flash-sales', permission: 'shop:product' },
  { group: 'marketing', title: '消息运营', description: '通知、公告与消息发送配置', path: '/tenant/message-operations', permission: 'config:shop' },
  { group: 'team', title: '团队、秒杀与复购模式', description: '推广资格开通方式、秒杀与复购入口及奖金处理', path: '/tenant/business-modes', permission: 'config:bonus', editor: true },
  { group: 'team', title: '客户奖金接入', description: '客户奖金程序及制度接入配置', path: '/tenant/bonus-config', permission: 'config:bonus', editor: true },
  { group: 'team', title: '会员端业绩查看权限', description: '控制会员端业绩数据的可见范围', path: '/audit/settings', permission: 'config:bonus', editor: true },
  { group: 'service', title: '商城客服渠道', description: '联系电话、邮箱与工作时间；微信客服需另在微信后台配置', path: '/tenant/profile', hash: '#customer-service', permission: 'config:shop' },
  { group: 'service', title: '发货与退货地址', description: '维护发货及售后退货地址', path: '/shop/service-addresses', permission: 'shop:product' },
  { group: 'service', title: '客服工单', description: '回复咨询、投诉、售后争议与账号问题', path: '/shop/service-tickets', permission: 'shop:order' },
  { group: 'integration', title: 'ERP与物流对接', description: '订单同步及物流服务设置', path: '/tenant/erp', permission: 'config:integration', editor: true },
  { group: 'security', title: '后台账号与权限', description: '管理操作人员与授权范围', path: '/system/admin-users', permission: 'system:manage' },
  { group: 'security', title: '后台操作日志', description: '查询配置及业务操作记录', path: '/audit/operation-logs', permission: 'system:manage' },
]

export function settingsEntriesFor(store, query = '') {
  if (store.userInfo?.merchantId) return []
  const terms = String(query).trim().toLowerCase().split(/\s+/).filter(Boolean)
  return SETTINGS_ENTRIES.filter((entry) => store.hasPermission(entry.permission)
    && terms.every((term) => `${entry.title} ${entry.description} ${SETTINGS_GROUPS.find((group) => group.key === entry.group)?.title}`.toLowerCase().includes(term)))
}
export const canAccessSettings = (store) => settingsEntriesFor(store).length > 0
export function isSettingsEditor(path) {
  return SETTINGS_ENTRIES.some((entry) => entry.editor && entry.path === path)
}
// 配置编辑页只有“设置中心”一个导航归属，禁止再挂到订单、财务等业务菜单下。
export const withoutSettingsEditors = (items = []) => items.filter((item) => !isSettingsEditor(item.path))
export const isSettingsContext = (path, merchantId) => !merchantId && (path === '/settings' || isSettingsEditor(path))
export const settingsAwareMenuPath = (path, merchantId) => isSettingsContext(path, merchantId) ? '/settings' : path
export function settingsGroupFor(route, entries) {
  const allowed = new Set(entries.map((entry) => entry.group))
  if (route.path === '/settings') return allowed.has(route.query.group) ? route.query.group : entries[0]?.group
  return entries.find((entry) => entry.path === route.path && entry.hash === route.hash)?.group
    || entries.find((entry) => entry.path === route.path)?.group
}
