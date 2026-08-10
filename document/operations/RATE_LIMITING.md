# 接口限流配置与调整说明

## 1. 限流层级

系统采用两层限流：

1. Nginx 作为公网第一层，按客户端 IP 限制请求速率和连接数。
2. Java 应用作为第二层，按接口类型和客户端 IP 使用 Redis 固定时间窗口计数；Redis 暂时不可用时自动切换为单实例内存计数，限流不会完全失效。

## 2. Java 应用限流规则

以下数量均为“同一客户端 IP 在 60 秒内允许的最大请求数”。

| 规则名 | 请求范围 | 上限 |
| --- | --- | ---: |
| `admin-login` | `POST /distribution/admin-auth/login` | 10 |
| `shop-auth` | `POST /shop/auth/login`、`/shop/auth/register`、`/shop/auth/resetPassword` | 10 |
| `captcha` | `GET /captcha` | 30 |
| `sms-send` | `POST /sms/send...` | 5 |
| `wallet-write` | `/shop/wallet/**` 的非 GET 请求 | 30 |
| `business-write` | `/shop/**`、`/distribution/**` 的其他非 GET 请求 | 120 |
| `api-read` | `/shop/**`、`/distribution/**` 的 GET 请求 | 600 |

客户端地址只在请求来自本机反向代理时读取 `X-Real-IP` 或 `X-Forwarded-For`，外部请求不能直接伪造该值绕过限流。

## 3. Nginx 限流规则

| 区域 | 速率 | 突发请求 | 覆盖范围 |
| --- | ---: | ---: | --- |
| `lingqimall_sensitive` | 每 IP 3 次/秒 | 8 | 验证码、短信、商城登录注册、钱包相关接口 |
| `lingqimall_api` | 每 IP 20 次/秒 | 60 | 其他 `/api/distribution/**` 和 `/api/**` 接口 |

Nginx 超限和 Java 应用超限都返回 HTTP `429 Too Many Requests`。

## 4. 超限响应

Java 应用返回：

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 60
Content-Type: application/json;charset=UTF-8
```

```json
{
  "code": 429,
  "message": "操作过于频繁，请稍后再试",
  "data": null
}
```

前端收到 429 后应展示短时提示，并在 `Retry-After` 指定时间内避免自动重复提交。支付、转账、提现等写操作不得通过前端无限重试。

## 5. 配置位置

- Java 接口分类和阈值：`mall-distribution/src/main/java/com/macro/mall/distribution/security/SecurityRateLimitFilter.java`
- Redis 与本地兜底实现：`mall-distribution/src/main/java/com/macro/mall/distribution/security/SecurityRateLimitService.java`
- Nginx 限流区域：`scripts/nginx/lingqimall-security.conf`
- Nginx 路由应用规则：`scripts/nginx/lingqimall.conf`

当前 Java 阈值由代码统一管理，修改时必须同时更新本文件和对应测试；Nginx 阈值修改后必须先校验配置再平滑重载。

## 6. 调整原则

1. 登录、注册、找回密码、短信和钱包接口保持最严格限制。
2. 不因营销活动直接取消限流；需要扩容时先分析真实峰值、服务器容量和恶意流量比例。
3. 多实例部署必须保持 Redis 可用，否则各实例的内存兜底计数互不共享，实际总上限会随实例数增加。
4. 修改规则后至少验证：正常请求通过、超过阈值返回 429、窗口结束后恢复、Redis 故障时仍能限流。
5. 监控 429 比例；若大量正常用户被限制，应先定位请求重复、前端重试或代理 IP 识别问题，再决定是否调整阈值。
