# SQL、数据库连接池与缓存监控

## 访问方式

正式环境启用 Actuator 的 `health`、`metrics` 和 `prometheus` 三个端点。应用默认只监听服务器本机，指标不经过商城 Nginx 对公网开放：

```bash
curl --fail --silent http://127.0.0.1:8086/actuator/prometheus
```

Prometheus 采集示例：

```yaml
scrape_configs:
  - job_name: mall-distribution
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['127.0.0.1:8086']
```

标准告警规则位于 `document/operations/prometheus/mall-distribution-rules.yml`。如果监控程序运行在另一台服务器，应通过内网或 SSH 隧道采集，不要把 Actuator 直接暴露到公网。

## SQL 性能指标

| 指标 | 含义 |
| --- | --- |
| `mall_database_query_duration_seconds` | MyBatis Mapper 执行耗时直方图，可计算 P50/P95/P99 |
| `mall_database_query_slow_total` | 超过 `DB_SLOW_QUERY_MS` 的累计次数 |

指标仅使用 Mapper 方法标识和查询/写入类型作为标签，不记录 SQL 参数、手机号、地址、订单内容或支付信息。日志仍保留 `DB_SLOW_QUERY`，便于没有 Prometheus 时定位具体 Mapper。

## 数据库连接池指标

| 指标 | 含义 |
| --- | --- |
| `mall_database_pool_active` | 当前活跃连接数 |
| `mall_database_pool_idle` | 当前空闲连接数 |
| `mall_database_pool_max` | 最大连接数 |
| `mall_database_pool_waiting` | 正在等待连接的线程数 |

默认每 15 秒采集一次。活跃连接达到最大连接数 80% 时继续写入 `DB_POOL_CAPACITY_WARNING` 日志；出现等待线程时应优先检查慢查询和长事务，不应直接盲目调大连接池。

## 缓存指标

| 指标 | 标签/含义 |
| --- | --- |
| `mall_cache_requests_total` | `result=hit/miss/error/bypass_*`，商城商品公共缓存请求结果 |
| `mall_cache_operations_total` | 缓存写入、失效、异常缓存删除的成功/失败次数 |
| `mall_cache_redis_available` | `1` 可用、`0` 不可用、`-1` 尚未检查 |
| `mall_cache_redis_ping` | Redis PING 延迟（毫秒），不可用时为 `-1` |

Redis 默认每 15 秒检查一次，只执行 PING，不读取、修改或扫描业务缓存。Redis 不可用时商品浏览会按原有设计回源数据库，支付、库存和下单仍以数据库实时校验为准。

缓存命中率计算口径：

```promql
sum(rate(mall_cache_requests_total{cache="shop_catalog",result="hit"}[10m]))
/
sum(rate(mall_cache_requests_total{cache="shop_catalog",result=~"hit|miss"}[10m]))
```

## 可调参数

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `DB_SLOW_QUERY_MS` | `1000` | 慢查询阈值（毫秒） |
| `DB_POOL_WARNING_PERCENT` | `80` | 连接池日志预警比例 |
| `DB_POOL_SAMPLE_MS` | `15000` | 连接池指标采集间隔 |
| `CACHE_MONITOR_SAMPLE_MS` | `15000` | Redis 指标采集间隔 |

调整阈值后必须结合一段时间的真实流量观察，不以单次峰值直接扩容或修改数据库参数。
