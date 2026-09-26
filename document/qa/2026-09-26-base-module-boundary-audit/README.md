# 通用基座模块边界审查（2026-09-26，首轮）

## 范围与结论

用户确认当前站点只用于基座测试，目标是按客户选择派生独立项目。本轮仅做源码审查、两处狭窄关闭态修正和自动回归，**不形成新候选，不发布服务器或微信，不写正式库，不代替真机/第三方联调**。仓库根为 `mall-swarm-app-h5`，基线 `VERSION=1.0.167`；线上历史回执仍是 `1.0.167 / c40a0e8cea82b8c5081ba0438dc3ced0db0daf37 / 20260924-closure-1.0.167`。

## 已确认并处理的边界

1. `CustomerDeliveryReadinessService` 原先只接受支付宝配置；客户若只选微信支付，会被误判为支付未就绪。现在要求模拟支付关闭，且**至少一条**微信或支付宝通道具备必要配置与 HTTPS 回调；文案同时声明这不等于真实支付/退款联调通过。新增微信单通道、两者均无、退款回调非 HTTPS 三种反例。
2. `ErpIntegrationServiceImpl.executeTask` 原先对已排队任务不重查集成启停；停用 ERP 后定时或人工重试仍可能向第三方推单。现在执行前复查；停用任务保留原状态，不调用适配器，也不消耗重试次数。推单前再核对任务、配置与订单的租户一致性，异常任务进入受控失败，不外发。`DmsErpSyncTaskMapper.selectRetryable` 在 SQL 阶段排除明确停用的集成，避免旧任务占满每批队列；配置已丢失的任务仍可进入受控失败，不变成永远不处理的孤儿。监控积压口径也排除停用任务，历史终态失败仍保留。补人工重试、选出后再停用、跨租户、缺配置、队列限额和监控反例。具体厂商对接仍无真实回执。

## 仍未通过“可按需组装”的能力

| 边界 | 直接源码证据 | 需要的下一步 |
|---|---|---|
| 无团队 H5 的公开商城包 | `private-deploy/scripts/prepare-env.sh` 必填 `TEAM_DOMAIN`；`build-release.sh` 固定构建团队站；Nginx 模板与 `security-postflight.sh` 必验团队域名 | 设计 `public-only` 与 `public+team` 两种部署合同、域名与回归；未完成前不能对客户承诺可不部署团队站 |
| 优惠券独立关闭 | `mall-mini-program/pages/checkout/index.js` 普通结算默认 `couponEnabled: true`，目前仅秒杀路径关闭 | 增加租户级选择及服务端/小程序/H5 一致门禁，覆盖存量券与退款；当前不承诺“无券包” |
| 余额与多商户可拆 | 结算/资金、订单归属均使用这些模型；本轮未找到完整的“模块禁用”合同 | 不以隐藏页面当作模块关闭；做 API/任务/数据/支付退款交叉测试后再分级 |
| 秒杀/复购与奖金组合 | `ShopBusinessModeService.requireEnabled` 和 `ShopServiceImpl` 已有交易入口门禁 | 扩至页面、后台任务、历史订单和账务逆流程的开启/关闭矩阵 |

## 已执行验证与证据边界

- 客户派生/升级脚本测试、私有部署安全合同测试通过；证明现有固定项目可派生及升级预检，不证明任意模块组合可装配。
- 修复后 Java 定向 `CustomerDeliveryReadinessServiceTest,ErpRetryLimitTest,DynamicSqlWhitespaceRegressionTest,BusinessOperationsMonitoringMapperTest` 通过；最终 `./mvnw -q -pl mall-distribution -am test` 完整运行成功：`mall-common` 17 项、`mall-distribution` 828 项，失败/错误/跳过均 0。客户派生、升级与私有部署安全合同脚本均通过。小程序 529 项、H5 183 项、后台权限定向 5 项在源码审查阶段通过；UI 没有本轮改动。
- 未执行客户项目真实域名、客户商户号、实际物流/ERP、服务器发布、微信平台切换或真机交易。所有未验证能力维持总账原状态。

客户选择与逐模块放行方法见 `../../customer-project/MODULE_COMPOSITION_GATE.md`；全局 P0/P1 和最后统一候选顺序见 `../../MALL_CLOSURE_LEDGER.md`。
