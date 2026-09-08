# 133未完成项收尾：代码、原生界面及渠道分层验收

2026-09-08，当前Mac，分支 `codex/security-membership-payout`，起点 `bd33aea9`。产品范围仍为 [64项H5对齐表](../../MINI_H5_FULL_PARITY_133.md)，不另建一套规则。

## 结论与边界

- 补齐R06账号替代/注册/找回、R07更新管理、R09本人主播台；补订单折叠/多包裹、驳回/退货/换货信息和多页面异步隔离。H5已验证纯规则新增账号校验为第5个自动生成模块；既有头像、微信地址、邀请、本人会员和资金规则保留。
- 小程序396/396、H5104/104、后端19类111/111通过；官方32WXML/34WXSS、工程/同源检查及三种H5构建/边界检查通过。后端是本批相关类，不是重新执行整个后端套件。H5构建有既有大块体积提醒，不宣称零警告。
- 原生采用微信开发者工具Stable 2.02.2608060、基础库3.7.12与官方automator 0.12.1。不是把H5截图当小程序。320×568和390×844各覆盖27个注册页面，另外四首页/三导购及部分空/错误/售后变体。记录以各目录manifest为准。
- 所有展示账户、手机号、金额、订单与直播权限均为合成验收数据。无真实登录令牌；业务请求由本地样例响应，真实网络写入、授权、上传和支付均拦截。商品公开图片仍可下载，不能称“完全无网络”。没有发送短信、提交工单、保存地址、开播或付款。
- 本批未部署/上传/提审，正式上传目录仍为53f72269的133体验版。原生新账号端点须先部署后端。真实授权、短信、邀请码首注、头像/地址、实名、微信/余额支付、退款、提现到账、订阅送达、直播媒体及真实更新周期仍需渠道验收；不以这些截图替代。

## 临时连接与收尾

用户明确批准临时打开本机服务端口，用完关闭。隔离项目：`UI133-全页面验收-禁止上传`，临时AppID `testwx9ce37707eca89544`；未启用“获取工具登录票据”、自动化默认信任或多插件端口。

收尾检查状态：中途Mac再次锁屏，CUA明确返回locked；用户解锁后已重跑全部9个实点检查并通过。脚本额外修正导航完成等待和不受支持的bindtap属性选择器，不将测试脚本问题冒记为产品缺陷。最后只清理本次本地购物车/假会话，退出隔离窗口，恢复390/16字号。设置页已实际取消服务端口勾选，原生界面明确显示“工具的服务端口已关闭。命令行和 HTTP 都无法调用工具”，其他三个权限仍关闭；本机9420已无监听者。

原生实点9项：搜索“礼盒”带词进入分类并得到1件结果；价格升序/降序切换；限购被拒绝后本地购物车仍为1件并中央提示；订单信息展开；本地物流轨迹读取；手动粘贴姓名/手机号/省市区识别但不保存；游客原位登录弹窗协议未选；次级账号登录页协议未选；取消返回我的页且不残留登录层。详见 `interactions/results.json`。未点击真实授权、发送短信、保存地址、下单或支付。

## 发现、修复与复看

| 现场发现 | 修复 | 证据/限制 |
|---|---|---|
| 首页双列超长金额在数字中间折行 | 价格整体不折行；加购按钮作为整块换行 | 390-final/account-login.png实际是热重载误入首页，不能作登录证据；320首页及390-auth-final登录才是对应结果 |
| 公告筛选竖排、公告卡片变窄 | 覆盖原生默认button宽度；筛选按内容，卡片整行 | 390-fixes/notices.png已复看；与9月7日H5公告截图并列对照 |
| 消息头部小字对比度不足 | 头部浅色文字与操作区不压缩 | 390-fixes/messages.png已复看 |
| 账号主按钮只有半宽、次级动作错位 | 限定账号容器下按钮宽度，主按钮全宽/协议下置 | 390-auth-final/account-login-after-notice.png、sms-login.png及320/register-bottom.png |
| 快速验证码请求失败导致首次onLoad/onShow重复请求 | 初次仅onLoad请求，离页返回可重试 | 新增回归通过，390账号错误恢复截图；部分after-notice图片仍带系统模态框，不称已关闭 |
| 结算支付选项宽度被默认样式覆盖 | 增加限定选择器，恢复代码原定整行选项 | 修前320/checkout-bottom.png；修后320-final及390-payment-final/checkout-bottom.png均已复看 |
| 隐藏首页旧响应写全局主题；旧账号钱包/地址/售后/消息/提现数据晚到 | 版本/账号保护前置，旧草稿清理、旧成功失败不进入新会话 | 396自动回归包含相关条目；隐藏授权成功仅原账号返回后同步 |

H5视觉基准沿用9月7日保存的390截图（公开静态115）与当前H5源码；本轮并列复看分类和公告的层级、筛选、商品信息/加购和整行卡片。原生是合成数据，**不是同生产数据的像素级完全一致证明**。保留微信导航/授权、主动头像地址、本人等级与奖金，以及用户已确认的原位登录入口差异。

## 截图目录说明

| 目录 | 用途 |
|---|---|
| `390/` | 首轮46场景，发现问题的基线。账号页部分因登录状态/热重载错误跳转；结算/公告/直播部分样例随后修正，不能整组当最终通过 |
| `390-refined/` | 20个重点场景；三导购真实结构、正常结算、访客与表单、商品/订单/售后底部。此处order-return采用旧样例状态1，实际是退款完成，不是待退货 |
| `390-fixes/` | 5页修后：公告、消息、订阅、直播评论、登录密码页 |
| `390-final/` | 三导购底部、售后状态4“待寄回”修正。最初account-login/sms-login受文件热重载影响，登录最终改看下一组 |
| `390-auth-final/` | 4种账号方式：密码、短信、注册、找回；新增actualPath断言，确认不是跳错页面 |
| `320/` | 51场景，27页/四首页/三导购、小屏商品长金额、售罄/缺图、空/失败/权限及分层账号页面。每条含actualPath，官方SDK未收到JS异常事件；并不代表工具无兼容性警告 |
| `320-final/`、`390-payment-final/` | 末轮支付选项修后截图；账号错误状态及我的页补拍 |
| `320-font19/` | 5个重点场景将工具字体设置为19后复看；manifest字号确认19，但工具fontSizeScaleFactor仍0.73，不能当成全部真机系统大字体适配证明 |
| `interactions/` | 官方SDK原生点击9个检查点全通过并有截图，不是实际渠道交易 |

32个WXML包含页面/组件/模板；34个WXSS包含组件和公共样式，数量不等于注册页面数。截图分辨率受开发工具显示比例影响；逻辑窗口尺寸以manifest.systemInfo.windowWidth为准。

末轮源文件一致性：除隔离专用app启动防护、请求替身、项目/私人配置外，210个运行文件与本地源码逐字节一致，聚合SHA-256 `cd38f3e0406858aa1d90a4cf8b9a6bf3b505e7482c78956ab6db470d62e332d4`。固定正式上传工程236个清单文件则仍与53f72269一致，没有被QA覆盖。末次官方编译命令曾因多一个右括号失败，纠正后32/34全部编译通过；最终测试396项全通过。

## 页面覆盖索引（27个注册路径）

| 页面 | 320文件 | 390优先证据 |
|---|---|---|
| 首页 / 分类 / 商品 / 购物车 | home / category / product / cart | 390-refined或390同名；首页长价修后见390-final |
| 我的 / 账号资料 / 支付安全 | profile / account-security / account-settings | 390同名 |
| 微信登录 / 账号替代 | login / account-login、sms-login、register、reset | 390-refined/login；390-auth-final四模式 |
| 地址 / 确认订单 | address / checkout | 390-refined/checkout；390/address |
| 订单 / 详情 / 售后 | orders / order-detail / after-sale | 390-refined同名；390-final/order-return-bottom |
| 钱包 / 提现 / 收款确认 | wallet / withdraw / payout | 390同名 |
| 消息 / 消息详情 / 订阅设置 | messages / message-detail / subscriptions | 390-fixes/messages、subscriptions；390/message-detail |
| 工单列表 / 工单详情 | support / support-detail | 390同名 |
| 公告 / 活动 | notices / campaign | 390-fixes/notices；390/campaign |
| 内容共用页 / 法务 / 主播台 | live、live-square、new-arrivals、brand / legal / live-studio | 390-refined/live；390其余同名 |

## 自动测试的覆盖说明

原生本批由369增至396：账号/短信/注册/找回/邀请、同意/验证码缺失拦截、重复提交/换号/隐藏返回、更新安全窗口、主播资格撤销/归属、订单分包裹/驳回、消息/提现/订阅授权生命周期、钱包/地址/售后异步保护、首屏恢复等。原库存/跨SKU累计/历史限购、余额支付与订单幂等测试继续运行。

后端111项为以下19类：MiniProgramAccountAuthControllerTest、SecurityRateLimitFilterTest、ShopSessionCookieFilterTest、WeChatMiniProgramAuthServiceTest、ShopWechatRegistrationServiceTest、ShopRegistrationInviteCompatibilityTest、SmsVerificationAttemptLimitTest、LoginCaptchaServiceImplTest、SmsControllerTest、WeChatMiniProgramCapabilitiesControllerTest、WeChatSubscriptionServiceTest、WeChatMiniProgramBonusServiceTest、WeChatMiniProgramAccountServiceTest、WeChatMiniProgramMemberServiceTest、MiniAccountPayloadPipelineTest、PayloadEncryptionServiceImplTest、LiveRoomFoundationTest、EncryptedPayloadRequestBodyAdviceTest、EncryptedPayloadEnforcementAspectTest。

新原生账号接口复用ShopAuthService，不建立第二套会员。浏览器令牌继续HttpOnly；嵌套敏感字段不加密会被拒绝，验证码、用户当前隐私版本与no-store有回归。原H5公开注册也纳入现有共享shop-auth频率桶，属于防绕过的共同接口保护，不改账号/邀请/资金规则。
