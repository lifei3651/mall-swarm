// 只索引已存在的配置入口；权限仍由原页面和服务端执行。
export const SETTINGS_GROUPS = [
  { key: 'base', title: '商城基础', description: '经营主体、资质与公开协议。' },
  { key: 'appearance', title: '品牌与页面', description: '统一商城品牌、主题与页面展示。' },
  { key: 'finance', title: '余额与提现规则', description: '设置余额资格、提现渠道和打款方式；财务查询与审核留在奖金与财务。' },
  { key: 'team', title: '业务模块与奖金', description: '业务开关、推广资格、奖金接入与会员端查看权限。' },
  { key: 'service', title: '客服与售后配置', description: '设置客服渠道、发货地址与退货地址；工单留在订单与售后。' },
  { key: 'integration', title: '服务与对接', description: 'ERP与物流对接配置；支付、短信、实名通道需配套部署。' },
  { key: 'security', title: '账号与权限', description: '配置后台账号与岗位权限；操作日志留在风控与审计。' },
]

export const SETTINGS_ENTRIES = [
  { group: 'base', title: '经营主体与商城资料', description: '名称、经营地址、营业执照与备案资料', path: '/tenant/profile', permission: 'config:shop', editor: true },
  { group: 'base', title: '协议与规则', description: '用户协议、隐私政策及商城规则', path: '/tenant/legal', permission: 'config:shop', editor: true },
  { group: 'appearance', title: '商城视觉与页面', description: '品牌标识、主题颜色、首页、分类及商品详情版型', path: '/tenant/list', permission: 'config:shop', editor: true },
  { group: 'finance', title: '余额与提现规则', description: '设置余额持有人资格、人工余额、银行卡和线下打款规则', path: '/withdraw/settings', permission: 'finance:manage', editor: true },
  { group: 'team', title: '商城业务模块', description: '邀请开关与普通商城模式、余额、多商户、推广资格、秒杀、复购及优惠券', path: '/tenant/business-modes', permission: 'config:bonus', editor: true },
  { group: 'team', title: '客户奖金接入', description: '客户奖金程序及制度接入配置', path: '/tenant/bonus-config', permission: 'config:bonus', editor: true },
  { group: 'team', title: '会员端业绩查看权限', description: '控制会员端业绩数据的可见范围', path: '/audit/settings', permission: 'config:bonus', editor: true },
  { group: 'service', title: '商城客服渠道', description: '联系电话、邮箱与工作时间；微信客服需另在微信后台配置', path: '/tenant/profile', hash: '#customer-service', permission: 'config:shop' },
  { group: 'service', title: '发货与退货地址', description: '维护发货及售后退货地址', path: '/shop/service-addresses', permission: 'shop:product' },
  { group: 'integration', title: 'ERP与物流对接', description: '订单同步及物流服务设置', path: '/tenant/erp', permission: 'config:integration', editor: true },
  { group: 'security', title: '后台账号与权限', description: '管理操作人员与授权范围', path: '/system/admin-users', permission: 'system:manage' },
]

export function settingsEntriesFor(store, query = '') {
  if (store.userInfo?.merchantId) return []
  const terms = String(query).trim().toLowerCase().split(/\s+/).filter(Boolean)
  return SETTINGS_ENTRIES.filter((entry) => store.hasPermission(entry.permission)
    && terms.every((term) => `${entry.title} ${entry.description} ${SETTINGS_GROUPS.find((group) => group.key === entry.group)?.title}`.toLowerCase().includes(term)))
}
export const canAccessSettings = (store) => settingsEntriesFor(store).length > 0
export function normalizeSettingsPath(path) {
  const value = String(path || '')
  if (!value || value === '/') return value
  return value.replace(/\/+$/, '') || '/'
}
export function isSettingsEditor(path) {
  const normalizedPath = normalizeSettingsPath(path)
  return SETTINGS_ENTRIES.some((entry) => entry.editor && normalizeSettingsPath(entry.path) === normalizedPath)
}
// 配置编辑页只有“设置中心”一个导航归属，禁止再挂到订单、财务等业务菜单下。
export const withoutSettingsEditors = (items = []) => items.filter((item) => !isSettingsEditor(item.path))
export const isSettingsContext = (path, merchantId) => !merchantId && (normalizeSettingsPath(path) === '/settings' || isSettingsEditor(path))
export const settingsAwareMenuPath = (path, merchantId) => isSettingsContext(path, merchantId) ? '/settings' : path
export function settingsGroupFor(route, entries) {
  const allowed = new Set(entries.map((entry) => entry.group))
  const normalizedPath = normalizeSettingsPath(route.path)
  if (normalizedPath === '/settings') return allowed.has(route.query.group) ? route.query.group : entries[0]?.group
  return entries.find((entry) => normalizeSettingsPath(entry.path) === normalizedPath && entry.hash === route.hash)?.group
    || entries.find((entry) => normalizeSettingsPath(entry.path) === normalizedPath)?.group
}
