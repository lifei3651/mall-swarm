# 2026-10-02 后台定向补齐（未提交、未发布）

基线：活动工作区 `mall-swarm-app-h5`，分支 `codex/security-membership-payout`，HEAD `472fcb877e2e2ba8f56e9ce3cb994db27e9243c4`。用户“补齐”授权限于前轮确认的四项后台问题与受邀开通说明。VERSION 保持 1.0.177，既有固定产品 a4067dfc、发布包和微信工程保留。本记录为本地源码验证，不代替线上或手机验收。

| 原问题 | 补齐结果 | 证据 |
| --- | --- | --- |
| 财务日期快速切换时旧响应覆盖新数据，旧请求结束清除新查询加载态 | 仅最新请求更新汇总/明细/分账/预警/规则；加载态及异步图表受同一请求保护，未填自定义日期使旧请求失效，离页停止写入 | [finance.vue](../../../mall-distribution-admin/src/views/audit/finance.vue)、[组件回归](../../../mall-distribution-admin/tests/components/finance-request-race.test.js) |
| 售后审核确认与接口期间可重复点击 | 确认开始即锁定，固定订单和表单快照，禁用编辑/关闭/重复提交；取消或失败释放锁定，刷新完成后恢复 | [orders.vue](../../../mall-distribution-admin/src/views/shop/orders.vue)、[原函数定向回归](../../../mall-distribution-admin/tests/views/shop-orders-audit-submission.test.js) |
| 填充默认问答仍要求邀请码 | 默认模板说明微信首次授权注册及账号入口，邀请码选填，有效分享可首次绑定，既有关系不更换；没有覆盖任何已保存的商城问答 | [legal.vue](../../../mall-distribution-admin/src/views/tenant/legal.vue) |
| 无邀请人被标为创始会员 | 显示“无邀请人 / 尚未绑定”，不改变账号等级、资格或邀请关系 | [members.vue](../../../mall-distribution-admin/src/views/shop/members.vue) |
| AUTO_ON_INVITE 说明仅覆盖受邀注册 | 补注册后首次有效分享绑定及启用默认主账号的新注册首次绑定说明，已有后端逻辑不改 | [business-modes.vue](../../../mall-distribution-admin/src/views/tenant/business-modes.vue) |

验证：`npx vitest run tests/components/finance-request-race.test.js tests/views/shop-orders-audit-submission.test.js tests/views/shop-orders.test.js tests/views/tenant-legal.test.js tests/views/members.test.js tests/components/business-mode-settings.test.js --reporter=dot`，6文件/54项通过。财务为挂载原 Vue 组件的模拟接口测试，售后为执行原页面函数的模拟确认/接口测试；不是实际退款或真实浏览器行操作。原问题复现使用本地合成 Promise，没有真实 API 请求。

`npm run build -- --outDir /tmp/lingqi-admin-followup-build --emptyOutDir`：后台词汇检查、生产构建通过；仅临时产物，未覆盖固定177产物。未跑后端、跨端或完整交易回归，未修改权限、配置、资金规则或数据库迁移。

同步尝试 `git pull --ff-only` 被自动审批拒绝，理由为用户此前禁止pull/fetch且“补齐”未撤销限制；没有成功执行或重试同步。开始工作时本地干净，缓存上游0/0不代表实时远程。保留用户禁止提交推送的边界，修改以本地未提交差异交付，不发布、不封包、不上传微信。真实页面和手机仍待对应版本验收；历史真实支付退款成功证据保持原结论，不因本轮合成测试改写。


## 后续：前端按钮外框与主题/购买按钮颜色

用户要求核对前端UI按钮及主题与购买按钮颜色是否可以分开或统一。静态核对小程序/H5共用按钮层、商品/购物车/结算/订单和登录模板，以及后台装修保存/预览协议；执行现有共用规范扫描。按钮按用途统一：通栏购买/提交，紧凑订单操作，复制文字、图标、数量加减各用对应组件。没有声称所有点击控件形状相同，也没有把静态扫描当成实际逐页截图验收。

确认并修正两处：

- 小程序商品“加入购物车”有局部胶囊圆角，购买按钮被全局控制圆角覆盖，旁边两按钮形状不一致。加购接入 `ui-button`，移除页面购买栏局部圆角；H5同一购买栏两按钮接入 `btn`/`btn primary`，外框读取各自端的共用规则。加购原辅助色保留；购买、提交和确认仍读独立主按钮色。缺货禁用和互斥防连点行为不改，原行为测试选择器更新为允许额外共用样式类。
- 后台已经支持 `buttonBg`/`priceColor` 各自的跟随主题与单独设置，但切换预设无条件覆盖原指定颜色。现在仅保留明确为 `custom` 且已有合法非空值的按钮/价格颜色，跟随模式仍使用新主题；缺配置的老表单继续按原预设初始化，明确“恢复默认颜色”仍可重置。补充跨主题、序列化保存重载及改回跟随模式的回归。

入口：商城视觉与页面 → 品牌视觉 → 按钮与价格 → 主按钮颜色。跟随主题可与主题同色；单独设置可保持例如“淡绿主题＋红色购买键”。价格强调色也可独立。源码未发布，不代替已保存的装修配置，未自动操作保存发布。

验证：

- 后台 `tests/views/tenant-display.test.js` 20项通过（含新增跨主题保存重载）。
- 小程序 `ui-consistency`、`button-affordance`、`decoration`、`quick-cart-stability`、`h5-purchase-alignment`、`product-services-ui` 六文件63项通过。
- H5 `ui-consistency`、`button-affordance`、`quick-cart-stability`、`product-services-ui` 四文件17项通过。
- 同一份 `themeColor=#b8dcc5`、指定 `buttonBg=#e7193f` 的序列化配置：custom 时H5/小程序购买色均为红色，theme 时均为淡绿色。
- 词汇/共用UI规范、小程序同源规则与工程检查通过；后台及公开/团队/一体化H5分别构建到临时目录成功。未覆盖固定177工程或运行候选封包。

未补范围：真实浏览器320/390px逐页渲染、手机和实际体验版验收。现有静态合同只验证已定义的共用规则，不能证明所有页面各状态的像素表现。没有新增/扩大交易回归，没有真实支付退款、业务模式改动、Git同步或提交推送，VERSION及服务器/微信保持原177证据状态。
