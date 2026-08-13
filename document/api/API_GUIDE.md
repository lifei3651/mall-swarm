# 灵启商城 API 接入与维护说明

## 1. 文档来源

`mall-distribution` 使用 Springdoc OpenAPI 自动生成接口文档。控制器路由、请求对象、响应对象和 Swagger 注解是接口规范的唯一代码来源，避免手工维护一份容易过期的接口清单。

本地或测试环境启动后访问：

```text
Swagger UI: http://127.0.0.1:8086/swagger-ui/index.html
OpenAPI JSON: http://127.0.0.1:8086/v3/api-docs
```

生产环境强制关闭 Swagger UI 和 `/v3/api-docs`，Nginx 同时返回 404。不得为了方便调试将生产接口文档公开到互联网。

## 2. 请求地址

新接入的浏览器端统一使用 `/api/v1` 前缀，Nginx 转发给后端时移除版本前缀。
现有 `/api` 地址作为 v1 的兼容入口继续保留，当前前台和后台不需要同时强制迁移。例如：

| 场景 | 浏览器请求 | 后端控制器路由 |
| --- | --- | --- |
| 商城首页 | `GET /api/v1/shop/home` | `GET /shop/home` |
| 商城登录 | `POST /api/v1/shop/auth/login` | `POST /shop/auth/login` |
| 后台登录 | `POST /api/v1/distribution/admin-auth/login` | `POST /distribution/admin-auth/login` |
| 支付宝异步通知 | `POST /api/v1/pay/alipay/notify` | `POST /pay/alipay/notify` |

前端开发时不要在业务代码中写死生产域名，应沿用项目已有的统一请求客户端。

## 3. 通用响应格式

除文件下载、第三方回调等特殊接口外，业务接口统一使用：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

`data` 的具体结构由 OpenAPI 中对应接口的响应模型决定。

### 通用业务码

| code | 含义 | 前端处理建议 |
| ---: | --- | --- |
| 200 | 操作成功 | 使用 `data` 更新页面 |
| 400 | 参数检验失败 | 在对应字段附近显示准确提示 |
| 401 | 未登录或会话过期 | 清理当前会话并跳转登录页 |
| 403 | 没有权限或请求来源校验失败 | 提示无权限，禁止自动重试 |
| 429 | 请求过于频繁 | 根据 `Retry-After` 等待后再试 |
| 500 | 业务处理失败 | 展示服务端 `message`；不得向客户展示堆栈或数据库信息 |

注意：部分业务校验失败仍可能使用 HTTP 200 返回，并在响应体 `code` 中表达失败；认证、权限、限流等网关或过滤器错误会直接使用对应 HTTP 状态。对接方必须同时检查 HTTP 状态和响应体 `code`，不能只判断其中一个。

完整错误处理、常见业务提示和安全输出规则见 [ERROR_CODES.md](./ERROR_CODES.md)。

## 4. 身份认证

### 商城客户

- 浏览器登录成功后由服务端写入名为 `shop_session` 的 Secure、HttpOnly Cookie，前端 JavaScript 不读取 Token。
- Cookie 路径为 `/api`。
- 使用 Cookie 发起 POST、PUT、PATCH、DELETE 等写操作时，请求必须带 `X-Shop-Client: storefront`。
- 受信任的非浏览器客户端可按既有兼容方式使用 `Authorization: Bearer <token>`，但不得把 Token 写入日志、截图或公开文档。

### 管理后台

- 浏览器登录成功后由服务端写入名为 `admin_session` 的 Secure、HttpOnly Cookie，默认绝对有效期为 12 小时，前端 JavaScript 不读取 Token。
- Cookie 路径为 `/api`；浏览器请求使用 `withCredentials: true`。
- 使用 Cookie 发起 POST、PUT、PATCH、DELETE 等写操作时，请求必须带 `X-Admin-Client: admin-web`。
- 历史 `Authorization: Bearer <token>` 只保留一次性迁移及受信任非浏览器客户端兼容；浏览器调用 `/distribution/admin-auth/me` 校验成功后迁移到 Cookie，并清除本地旧 Token。
- 不同后台接口还会检查管理员权限；登录成功不等于拥有全部业务权限。

## 5. 请求参数与响应模型怎么看

在 Swagger UI 中展开具体接口，可直接查看：

- HTTP 方法和完整路由；
- 路径参数、查询参数、请求头和请求体；
- 必填字段、字段类型与校验约束；
- 响应格式和模型字段；
- 控制器通过 `@Operation`、`@Tag` 提供的业务说明。

前端联调前应先在本地或测试环境查看 OpenAPI，不以聊天记录、截图或旧发布报告作为接口规范。

涉及下单、余额支付、转账、提现、支付宝回调、退款和发货时，还必须阅读 [IDEMPOTENCY.md](./IDEMPOTENCY.md)，按接口矩阵生成并复用请求唯一编号。

## 6. 导出与归档

本地服务启动后可用仓库脚本导出当前版本的机器可读文档：

```bash
./scripts/export-openapi.sh
```

导出文件只用于指定版本的联调、审阅或交付。接口代码继续是唯一来源；后续接口变更后必须重新生成，不允许手工修改导出的 JSON。

## 7. 接口变更维护规则

新增或修改接口时必须同步完成：

1. 使用明确的 HTTP 方法和稳定路由。
2. 请求 DTO 添加必要的字段校验和 `@Schema` 说明。
3. 控制器补充或更新 `@Operation`、`@Tag`。
4. 新错误场景返回准确、可面向客户的 `message`，不得返回异常堆栈、SQL、密钥或内部路径。
5. 增加成功、参数错误、未认证、无权限及关键业务冲突测试。
6. 本地查看 Swagger UI，确认请求参数、响应模型和说明正确。
7. 发布或交付前重新导出 OpenAPI，并在发版报告中记录接口兼容性变化。

### 版本兼容规则

- 当前稳定接口版本为 `v1`。
- 新客户端使用 `/api/v1/...`；旧客户端可继续使用 `/api/...`。
- v1 内只允许向后兼容地新增可选字段，不删除字段、不改变已有字段含义。
- 确需不兼容升级时新增 `/api/v2/...`，并给旧版本留出迁移期，禁止直接覆盖 v1。
- 支付宝、短信等第三方回调地址在迁移期内必须同时验证，不能因版本升级漏接通知。

## 8. 代码与配置依据

- OpenAPI 配置：`mall-distribution/src/main/java/com/macro/mall/distribution/config/SpringDocConfig.java`
- 控制器：`mall-distribution/src/main/java/com/macro/mall/distribution/controller/`
- 通用响应：`mall-common/src/main/java/com/macro/mall/common/api/CommonResult.java`
- 通用错误码：`mall-common/src/main/java/com/macro/mall/common/api/ResultCode.java`
- 商城 Cookie 会话：`mall-distribution/src/main/java/com/macro/mall/distribution/security/ShopSessionCookieService.java`
- 后台 Cookie 会话：`mall-distribution/src/main/java/com/macro/mall/distribution/security/AdminSessionCookieService.java`
- 生产禁用规则：`mall-distribution/src/main/resources/application-prod.yml`、`scripts/nginx/lingqimall.conf`
