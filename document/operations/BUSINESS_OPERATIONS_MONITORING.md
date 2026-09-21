# 商城业务运行监控与值守手册

## 目标与边界

本监控覆盖微信发货同步、微信物流关注、奖金计算、ERP 同步、外部退款、提现打款和商户账本一致性。它只执行聚合只读查询并输出固定标签，不把订单号、支付单号、运单号、会员编号、手机号、地址或异常消息写入指标与监控日志。

指标异常只表示需要核对，不能替代支付渠道、退款渠道、微信交易组件或银行/支付机构的真实结果。尤其是退款和打款超时后，禁止未经渠道查询直接再次退款或打款。

## 指标口径

| 指标 | 固定标签 | 口径 |
| --- | --- | --- |
| `mall_business_task_backlog` | `task` | 待执行、可重试或正在执行的任务数 |
| `mall_business_task_oldest_age_seconds` | `task` | 最老积压任务从创建至当前的秒数；无积压为 `0` |
| `mall_business_task_failures` | `task` | 已进入终态且需要人工处理的失败数 |
| `mall_business_operation_timed_out` | `operation` | 超过配置阈值仍处于处理中的操作数 |
| `mall_business_operation_oldest_age_seconds` | `operation` | 最老超时操作从最后更新时间至当前的秒数 |
| `mall_business_ledger_mismatches` | `ledger` | 账户六项余额与最新账本结余不同，或没有初始化账本的账户数 |
| `mall_business_monitor_available` | `type`,`source` | `1` 采样成功、`0` 采样失败、`-1` 尚未采样 |

任务标签只有 `wechat_shipping_sync`、`wechat_logistics_follow`、`bonus_calculation` 和 `erp_sync`；操作标签只有 `refund`、`withdrawal_payout`；账本标签只有 `merchant_balance`。代码拒绝动态标签，防止业务标识导致高基数和隐私泄露。

状态口径：

- 微信发货：`PENDING/RETRYABLE/SENDING` 为积压，`PERMANENT` 为失败；本地撤销运单产生的 `LOCAL_WAYBILL_CANCELLED` 不算失败。
- 物流关注：`PENDING/RETRYABLE/SENDING` 为积压，`PERMANENT` 为失败；已跳过和撤销中的任务不算积压。
- 奖金：待处理、处理中和仍有重试次数的失败任务为积压；重试耗尽为终态失败。
- ERP：待处理和可重试为积压，终止重试为失败。
- 退款：微信/支付宝售后处于“退款处理中”且最后更新时间超过默认 30 分钟。
- 提现打款：提现仍为打款中，渠道状态为 `PROCESSING/WAIT_USER_CONFIRM/UNKNOWN`，且最后更新时间超过默认 24 小时。
- 商户账本：待结算、可提现、普通冻结、保证金冻结、欠款、累计已打款六个余额逐项比较最新账本结余。

## 采集与阈值

应用默认每 60 秒采集，启动 30 秒后首次执行。可配置：

| 环境变量 | 默认值 | 含义 |
| --- | ---: | --- |
| `BUSINESS_MONITOR_SAMPLE_MS` | `60000` | 聚合采集间隔（毫秒） |
| `BUSINESS_MONITOR_INITIAL_DELAY_MS` | `30000` | 启动后首次采集延迟（毫秒） |
| `BUSINESS_MONITOR_REFUND_TIMEOUT_SECONDS` | `1800` | 外部退款处理中超时阈值 |
| `BUSINESS_MONITOR_WITHDRAWAL_PAYOUT_TIMEOUT_SECONDS` | `86400` | 提现打款处理中超时阈值 |

Prometheus 告警规则在 `document/operations/prometheus/mall-distribution-rules.yml`：微信任务 15 分钟、奖金 10 分钟、ERP 30 分钟；终态失败、退款/打款超时、账本差异和采样失败均会告警。

## 只读值守检查

生产配置安全默认只暴露 `health`。部署内网 Prometheus 或执行本脚本前，需要在服务器显式设置
`MANAGEMENT_ENDPOINTS=health,metrics,prometheus`；应用仍只监听本机/内网，Nginx 不得把 Actuator 转发到公网。

在应用服务器执行：

```bash
scripts/production-business-watch.sh
```

脚本仅请求 `127.0.0.1:8086/actuator/prometheus` 并解析上述聚合指标。输出只有固定类型、数量和年龄，不输出任何业务明细。返回 `0` 表示聚合值正常，返回 `1` 表示指标端点不可用、指标缺失或至少一项达到告警条件。

## 告警处理顺序

1. 先确认 `mall_business_monitor_available`。为 `0/-1` 时先排查采集 SQL、数据库和应用日志；指标值 `-1` 不能当成零积压。
2. 任务积压时确认相应调度是否运行、外部能力是否开通、失败终态是否增加。恢复应使用后台已有重试/恢复入口，不直接改表。
3. 退款超时时先通过原支付渠道查询退款单真实状态，再走现有幂等核对流程；禁止创建新退款号碰运气。
4. 打款超时时先查询原渠道请求号真实状态。渠道未知或待用户确认时禁止再次打款。
5. 账本差异时暂停相关账户的人工调账与打款，使用后台对账页面和账本流水只读定位；修复前保留审计证据，禁止直接覆盖余额。
6. 处理后观察至少两个采集周期，确认数量下降且没有新增终态失败，再关闭告警。

## 验收记录

代码测试只能证明指标口径和故障隔离；正式值守验收还需单独记录：Prometheus 已加载规则、Alertmanager 路由可达、服务器脚本执行结果、一次测试告警的触发与恢复时间。没有这四项真实环境证据时，只能记录为“代码/测试完成”，不能写成“正式监控已完成”。
