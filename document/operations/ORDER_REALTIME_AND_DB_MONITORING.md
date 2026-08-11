# 订单实时通知与数据库监控

## 1. 订单与售后实时通知

商城前台和管理后台使用服务端事件流接收“订单数据已变化”通知：

- 商城会员：`GET /api/shop/events/orders`
- 管理后台：`GET /api/shop/admin/events/orders`
- 通知范围：支付、取消、超时关闭、发货、确认收货、售后申请、撤回、审核、退货寄回和退款完成。
- 通知只携带订单 ID 和变化类型，不携带收货地址、手机号、支付信息等敏感业务数据。
- 页面收到通知后重新调用原查询接口读取最终状态，避免把通知内容当成权威数据。
- 数据库事务提交成功后才发送通知；事务回滚不会向页面发送错误状态。

浏览器断线后从 1 秒开始自动重连，最长等待 30 秒。实时连接不可用时，页面自动切换为每 30 秒查询一次；连接恢复后停止轮询，避免重复请求。Nginx 必须为上述两个地址使用独立代理规则，关闭请求/响应缓冲、缓存和压缩，保留授权头，并将读写超时延长到 35 分钟。仓库内两套正式配置已同步处理：

- 当前服务器部署模板：`scripts/nginx/lingqimall.conf`
- 客户独立部署模板：`document/private-deploy/nginx/conf.d/mall.conf`

修改或发布 Nginx 配置前先验证仓库模板：

```bash
python3 scripts/nginx/verify-sse-config.py \
  scripts/nginx/lingqimall.conf \
  document/private-deploy/nginx/conf.d/mall.conf
```

服务器加载配置后，还需要检查实际生效配置，避免只修改模板但未加载：

```bash
sudo nginx -T 2>/dev/null | python3 scripts/nginx/verify-sse-config.py -
sudo nginx -t
```

两项均通过后才允许重新加载 Nginx。该检查只读取配置，不连接数据库，也不修改订单、售后或会员数据。

当前正式部署为单个分销服务实例，事件连接保存在当前进程内。如果以后扩展为多个分销服务实例，应先用 Redis Stream 或消息队列增加跨实例事件广播，再启用多实例流量分配。

心跳间隔可通过环境变量调整：

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `SHOP_REALTIME_HEARTBEAT_MS` | `25000` | 实时连接心跳间隔（毫秒） |

## 2. 订单与售后组合索引

增量迁移：`document/sql/20260811_add_order_realtime_query_indexes.sql`

迁移只增加以下索引，不删除、不修改业务数据，并且可以重复执行：

- 会员订单状态分页：`dms_shop_order(user_id, status, create_time, id)`
- 后台租户订单状态分页：`dms_shop_order(tenant_id, status, create_time, id)`
- 订单售后状态查询：`dms_shop_after_sale(order_id, status, id)`
- 会员售后状态分页：`dms_shop_after_sale(member_id, status, create_time, id)`

正式发布时应先备份数据库，再通过现有数据库迁移工具执行该 SQL，最后确认四个索引均存在。本轮代码提交不会自动连接生产或执行迁移。

## 3. 慢查询监控

MyBatis 查询或写入超过阈值时记录 `DB_SLOW_QUERY` 告警，内容仅包含 Mapper 方法标识、耗时和阈值，不记录 SQL 参数，避免手机号、地址、订单信息等敏感内容进入日志。

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `DB_SLOW_QUERY_MS` | `1000` | 慢查询告警阈值（毫秒） |

慢查询告警用于定位问题，不会中断订单、支付或售后交易。排查时优先确认执行计划是否使用本次新增组合索引，再判断是否需要调整查询语句。

SQL 耗时分布、连接池和缓存的可采集指标及告警规则见 `document/operations/METRICS_MONITORING.md`。

## 4. 数据量与连接池预警

系统每天低峰期只读检查订单、订单商品、售后、会员资金流水和后台操作日志五张核心表，同时检查 Druid 数据库连接池使用率：

- `DB_CAPACITY_WARNING`：核心表预计行数或数据与索引总容量超过阈值。
- `DB_POOL_CAPACITY_WARNING`：连接池活跃连接达到最大连接数的预警比例。
- `DB_CAPACITY_CHECK_FAILED` / `DB_POOL_CAPACITY_CHECK_FAILED`：本次检查失败，不影响业务交易，下次计划任务会继续检查。

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `DB_ROW_WARNING_THRESHOLD` | `1000000` | 单张核心表预计行数预警值 |
| `DB_SIZE_WARNING_MB` | `2048` | 单张核心表容量预警值（MB） |
| `DB_POOL_WARNING_PERCENT` | `80` | 连接池使用率预警百分比 |
| `DB_CAPACITY_MONITOR_CRON` | `0 15 3 * * ?` | 每日容量检查时间，默认 03:15 |

监控只告警，不会自动删除、归档、扩容或改变业务数据。连续出现容量告警时，依次评估数据归档、读写查询优化和只读副本；现阶段不实施分库分表。
