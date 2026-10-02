# 灵启商城：两模式基础与部分退款本地交接

2026-10-02（UTC）。仓库 `mall-swarm-app-h5`；分支 `codex/security-membership-payout`；HEAD `472fcb877e2e2ba8f56e9ce3cb994db27e9243c4`；VERSION `1.0.177`。线上固定产品 `a4067dfc9deec999d2922bd792b72b3fbc6b5d29` 未改。

本轮仅有本地未提交代码、SQL文件和隔离验证。未 fetch/pull、提交/推送、封包、部署、微信上传，未执行生产配置、迁移、支付、退款、发货或数据库写入。

## 部分退款

A ¥89、B ¥0.01 同单支付，仅 A 退款完成时，后端继续保留待发货供 B 履约，这是正确业务状态。前端原来只在描述显示退款件数，顶部仍笼统待发货，B 无状态，取消按钮未明确剩余范围。

- 小程序与公开 H5 顶部显示“部分退款完成，剩余1件待发货”。
- A 保留已退款与原实付，B 显示待发货。同一行多件时显示剩余件数；普通未退款单不重复显示商品状态。
- 按钮显示“取消剩余商品并退款（¥0.01）”，仍进入现有申请确认流程；实际请求只发 B 的订单商品ID和数量1，不发送客户端金额。后端计算退款 ¥0.01，拒绝重复申请 A。
- 合并交易中多个子订单按商品数量汇总；子订单各自售后，未扩成按父交易全量退款。H5详情仍以服务端返回的当前订单为范围。
- 备注显式标为“商家处理说明”，只读取字符串 auditRemark。后台 textarea → ShopAfterSaleAuditDTO.auditRemark → setAuditRemark → audit_remark → 前端原文，映射一致。状态1单独显示退款完成。历史字符串“1”保留，未伪造解释或修改历史数据；其人工填写来源仍须该记录的只读证据。

主要源码：
- `mall-mini-program/pages/order-detail/index.js:195`、`:207`、`:259`、`:276`；`index.wxml`；`policy.js:123`。
- `mall-shop-web/src/views/OrderDetailView.vue:30`、`:71`、`:651`、`:802`、`:1292`；`src/utils/orderItemRefunds.js`。
- 请求保留 `mall-mini-program/pages/after-sale/index.js:149` 与 H5 的 `submitAfterSale`，无整单取消接口替代。
- 后端退款数量与金额 `ShopAfterSaleServiceImpl.java:209`、`:241`、`:947`、`:970`；备注 `:693`、`:723`，`DmsShopAfterSaleMapper.xml:30`、`:176`。

## 两模式基础

同一基座的普通商城 NORMAL 与直销／代理 AGENCY，NULL 仅代表兼容177存量，未自动转换老客户。后台设置中心 → 商城业务模块新增模式选择、配置状态及缺项；AGENCY只记录购买门槛、商品/金额、开通方式、目标等级、归属、退款规则、客户奖励制度草稿。无默认金额/商品/资格，填齐仍 PENDING_IMPLEMENTATION，不能启用。

服务端控制注册邀请、分享绑定、直接注册/升级/重启资格、支付后资格与新奖金、旧直接推荐奖金开关、团队/业绩接口。后台/H5菜单与直达路由、小程序能力同步受限；失败不放行团队入口，历史钱包/提现记录不经过团队门禁。普通购物和既有账务流程保留。实际支付测试覆盖显式 STANDARD 商品也不借用旧首单资格或奖金算法。

模式/草稿变更有 config:shop 权限、当前租户、行锁及版本校验；已有账号/代理/订单/奖金责任不能直接切换。账号/代理现有表缺租户归属，因此使用全库保守阻断，不能把跨客户账号假定归属。普通资料编辑、旧字段请求和配置版本恢复不会绕过模式边界。全新租户默认 NORMAL；空业务数据库可维护新模式。

两个可空字段的迁移文件 `document/db/migrations/V202610022300__customer_business_mode_drafts.sql` 仅保存，未执行，无数据 UPDATE/回填。全新私有建库基础表补齐列，初始化 NORMAL；未改原177固定发布清单，部署前仍需单独审核迁移与交付候选。

主要源码：`CustomerBusinessModePolicy.java`、`CustomerBusinessModeBoundaryConfig.java`、`TenantServiceImpl.java:206`、`:220`；后台 `CustomerModeSettings.vue` 与 `tenant/business-modes.vue`。保存回读不覆盖确认期间新编辑，七参数旧能力构造器保留兼容，客户大整数商品编号保留字符串。

## 验证和保留

- Java定向组合：96通过、0失败/错误；本地H2与Mockito，未连接生产。
- 后台：9文件67通过。
- 小程序：82通过；工程、JSON、JS、HTTPS与H5同源规则检查通过。
- H5：26通过；公开、团队、一体化构建与各边界检查通过。
- 后台生产模式本地构建通过；最后大整数编号修正后再次构建。
- `git diff --check` 通过。
- 退款隔离覆盖：A已退后B独立POST、后端金额和商品范围、A重复退款拒绝、部分退款仍待发货/全部退款才关闭、余额账务仅 ¥0.01 与B结算行、正常单和合并子单数量。
- 原始20个dirty文件中19个与起始快照逐字节相同；仅业务模式页面有叠加修改，保留原邀请说明。原财务、审核、主题及商品页修复均保留。
- 起始快照 `baseline-20261002-two-modes/`；Bug切换前快照 `checkpoint-two-modes-before-refund-bug/`。本轮新增内容仍未提交。
- 日志：`final-backend-validation.log`、`final-admin-validation.log`、`final-mini-validation.log`、`final-h5-validation.log`、`admin-build.log`、`final-h5-build.log`、`mini-check.log`（均在当前任务目录）。

未完成事项是客户实际门槛/资格/奖励制度实现（按用户要求暂缓）、历史备注“1”的来源确认、真实浏览器/真机逐页验收和后续发布。未宣称线上已修复或真实支付渠道已退款。


2026-10-02 发版新阶段：用户已批准合并发布，当前正在准备178。上文“仅本地/不提交”是上一阶段回执，不是永久限制；生产与微信完成前不宣称线上已修复。
