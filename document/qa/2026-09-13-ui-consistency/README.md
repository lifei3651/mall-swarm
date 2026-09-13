# 全页面 UI 检查与修复记录

日期：2026-09-13。基线：181ef3c3，修改前 pull 成功。版本仍 1.0.139，线上只读为 e6a11c3d。本轮只修源码，不部署、不上传、不提审，不更新固定正式工程。

## 结论与验收边界

已经逐页采集全部 29 个注册小程序页面及 11 个追加表单/弹层状态，并依据截图修复选择行、按钮宽度和窄屏滚动问题。**这不是“全部页面所有状态已通过”的结论**：原生 390 基线采集完成；320 补测期间模拟器响应超时，随后检测到 Mac 锁屏，部分最终样式需要解锁后复拍。H5 实际工单组件已完成 390/320 截图与交互检查。后台桌面页面、微信真机、真实验证码、客服、实名、提现通道未在本轮做完整视觉/业务验收。

使用官方开发者工具和官方 miniprogram-automator，只有 touristappid 隔离假数据工程；测试鉴权值不能访问服务器。禁止真实支付、短信、地址授权、头像上传和业务写入。商品图片只用既有静态样例；未上传截图里的私人账号/地址数据。本报告截图均为合成业务数据。

当前未收尾项：临时服务端口关闭、模拟器恢复原机型及后述页面的最终复拍，需 Mac 解锁后完成。不要把端口当成已经关闭。

## 用户路径与发现

| 步骤 | 原问题 / 风险 | 本轮处理 | 当前证据 |
| --- | --- | --- | --- |
| 1. 联系客服 → 提交问题 | 咨询像普通输入框，取消与标题不对齐，重复说明占空间 | 通栏选择行，标签/当前值/箭头；取消与标题同行；收起重复帮助卡 | [原生修后](after/support-create.png)、[H5修前](before/h5-support.png)、[H5修后390](after/h5-support-390.png) |
| 2. 关联订单 | 无订单还弹唯一“不关联订单”；失败与为空混淆 | 无可选订单时不提供选择器；单独显示加载/不可用/空状态，失败有重试 | [空态](after/support-empty.png)、[失败](after/support-error.png)、[H5窄屏空态](after/h5-empty-320.png) |
| 3. 售后、活动与地址选择 | 选择控件缺乏明显入口 | 售后原因、快递、活动数量共用选择箭头；地址取消按钮明确尺寸 | [售后原因](after/after-sale.png)、[活动](after/campaign.png)、[地址](after/address-edit.png) |
| 4. 商品分享与通栏按钮 | 微信实际把分享按钮拉成184×45；部分主操作居中且只有半宽 | 分享限制最大宽度，实际45×45；消息详情/提醒按钮通栏；补评价、物流、地址识别、直播刷新等一致性 | [分享修前](before/product.png)、[修后](after/product.png)、[320分享37×37](narrow/product.png)、[消息详情](after/message-detail.png) |
| 5. H5小屏 | body 最小320宽与纵向滚动条叠加，出现横向滚动 | 去掉 body 固定最小宽度，不靠裁剪隐藏内容 | [320修后](after/h5-support-320.png)；DOM clientWidth=305、scrollWidth=305，viewport=320 |

### 已完成的交互检查

- H5 工单实际原生 select 从咨询切换账号问题，关联订单行随之隐藏；取消再打开保留本地标题草稿和已选类型。
- H5 无订单时只有问题类型一个下拉控件，关联订单为不可点击的空态文字；没有空菜单。
- 原生逻辑测试覆盖异步加载、失败、取消保留草稿、提交中不能取消/改选，以及扫描全部已注册页面的 picker 箭头与可理解标签。
- 没有提交真实工单、评价、改密、提现或订单操作；加载失败和验证码失败均由隔离夹具明确制造。未把服务端资金/权限规则改成前端判断。

## 29 个注册页面覆盖清单

下列路径都是当前轮次采集。首屏截图不代表长页面底部也全部验收；有追加底图的见 after/manifest.json。所有页面做源码同类控件检查，视觉复核以对应截图为界。

| 页 | 证据 | 结果 / 限制 |
| --- | --- | --- |
| 首页 | [home](after/home.png) | 长金额/标题样例，保留布局 |
| 内容页 | [culture](narrow/store-content.png) | 初次遗漏type参数产生“页面不存在”；补culture正常。该错误不是生产缺陷 |
| 联系/法律页 | [legal](after/legal.png) | 联系入口布局；未联系真人 |
| 公告 | [notices](after/notices.png) | 首屏检查 |
| 活动 | [campaign](after/campaign.png) | 购买数量选择箭头已补 |
| 分类 | [category](after/category.png) | 商品信息约束宽度，未改加购逻辑 |
| 商品 | [product](after/product.png) | 分享实测45×45，320实测37×37 |
| 购物车 | [cart](after/cart.png) | 沿用已解决的加减/管理逻辑，不引入整页刷新 |
| 优惠券 | [coupons](after/coupons.png) | 首轮夹具少title导致空标题区域，已修夹具；非生产数据问题，最终复拍待完成 |
| 手机号登录 | [login](after/login.png) | 只检验表单，不取真实手机号 |
| 账号登录 | [account-login](after/account-login-notice-dismissed.png) | 隔离验证码错误提示与表单布局；不获取真实验证码 |
| 主播工作台 | [live-studio](after/live-studio.png) | 发现刷新按钮过宽，已修，最终截图待补 |
| 我的 | [profile](after/profile.png) | 图标/入口/底部状态检查 |
| 个人资料与登录密码 | [account-security](after/account-security.png) / [password](after/change-password.png) | 表单排版，不提交改密 |
| 账号安全设置 | [account-settings](after/account-settings.png) | 支付密码/实名/手机号另有截图 |
| 消息 | [messages](after/messages.png) | 分类和内容布局 |
| 消息详情 | [message-detail](after/message-detail.png) | 主按钮通栏 |
| 工单列表 | [support](after/support.png) | 简化头部，六状态全部可见 |
| 工单详情 | [support-detail](after/support-detail.png) | 标题权重修复；关联订单按钮最终复拍待补 |
| 消息订阅 | [subscriptions](after/subscriptions.png) | 授权主按钮通栏；未真实授权 |
| 微信收款 | [payout](after/payout.png) | 保留必要资金提示，不做转账 |
| 钱包 | [wallet](after/wallet.png) | 金额与入口对齐 |
| 提现 | [withdraw](after/withdraw.png) | 保留权限/实名认证/支付验证；本轮不改银行和人工打款规则 |
| 订单列表 | [orders](after/orders.png) | 操作布局检查，未操作订单 |
| 订单详情 | [order-detail](after/order-detail.png) | 物流按钮/信息行修复；最后一批通栏补丁仍需复拍 |
| 订单评价 | [order-review](after/order-review.png) | 发现提交和返回按钮半宽，已修源码；最终截图待补 |
| 申请售后 | [after-sale](after/after-sale.png) | 原因选择箭头，售后类型/金额校验保持 |
| 确认订单 | [checkout](after/checkout.png) / [remark](after/checkout-remark.png) | 备注/支付块排版检查，未付款 |
| 地址 | [address](after/address.png) / [edit](after/address-edit.png) | 取消按钮已复核，识别按钮通栏最后补丁待复拍；不授权真实微信地址 |

其余内容页分支（直播、直播广场、新品、品牌）及登录注册/重置模式在同轮前置截图检查过，完整临时证据位于 /private/tmp/lingqi-ui-audit-before。它们不是额外注册页面，也不代表真实直播播放/注册成功已验证。

## 验证与发布状态

- 原生测试470/470，H5测试158/158。
- 小程序同源规则、工程结构/JSON/JS/HTTPS检查通过。
- 微信官方 WXML 34份、WXSS 36份编译通过。
- 公开、团队、一体化 H5 构建和边界检查通过。
- 规范固化于 [UI_ACCEPTANCE_STANDARD.md](../../UI_ACCEPTANCE_STANDARD.md)，后续新增选择器自动检查箭头/标签；修复不能仅靠颜色或CSS文字判断。
- 本轮未发布；手机仍不会自动取得未上传的小程序源码改动。
