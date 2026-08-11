# 商城性能与接口版本配置

## 商品公共缓存

商城首页、商品分页列表、前台分类和商品详情使用 Redis 短时缓存。缓存只保存公共展示数据，
不保存会员余额、限购结果、支付状态或库存扣减结果；下单时始终以数据库实时库存和价格再次校验。

默认有效期：

| 内容 | 默认有效期 | 生产环境变量 |
| --- | ---: | --- |
| 首页 | 30 秒 | `SHOP_CATALOG_HOME_TTL_SECONDS` |
| 商品列表/详情 | 15 秒 | `SHOP_CATALOG_PRODUCT_TTL_SECONDS` |
| 分类 | 60 秒 | `SHOP_CATALOG_CATEGORY_TTL_SECONDS` |

后台修改商品、SKU、分类、轮播图、公告、商城资料或视觉配置后，会在数据库事务提交后立即切换缓存版本；
订单扣减或归还库存后也会立即失效。Redis 暂时不可用时自动查询数据库，不阻断客户浏览和下单。

## 数据库连接池

项目使用 Druid，生产默认初始连接 5、最小空闲 5、最大活动连接 20、最长等待 3 秒。
连接空闲期间每 60 秒用 `SELECT 1` 做存活检查，避免把失效连接交给业务请求。

可通过以下环境变量调整：

- `DB_POOL_INITIAL_SIZE`
- `DB_POOL_MIN_IDLE`
- `DB_POOL_MAX_ACTIVE`
- `DB_POOL_MAX_WAIT_MS`

调整最大连接数前必须同时核对 MySQL `max_connections`、应用实例数和服务器内存，不能只单方面调大。

## 响应压缩

生产 Nginx 已对 JSON、JavaScript、CSS、XML、文本和 SVG 开启 gzip。Java 服务同时开启压缩兜底，
用于本地直连、测试环境和未来容器不经过现有 Nginx 的场景。小于 1KB 的响应默认不压缩，避免得不偿失。

## API 版本与文档

- 新客户端入口：`/api/v1/...`
- 旧客户端兼容入口：`/api/...`
- Springdoc 在本地和测试环境随代码自动生成 `/v3/api-docs` 和 Swagger UI。
- 生产环境出于安全考虑关闭在线文档；交付文档使用 `./scripts/export-openapi.sh` 从测试服务导出。

Nginx 对 `/api/v1` 敏感接口应用与旧地址相同的限流规则，版本前缀不能用于绕过登录、短信和钱包限流。
