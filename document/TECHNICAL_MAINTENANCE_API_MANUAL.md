# 灵启商城《技术维护/接口说明手册》

适用版本：`1.0.50`（当前待发布版本）

适用对象：后端、前端、测试、运维及后续客户定制开发人员

配套文档：

- [API 接入说明](./api/API_GUIDE.md)
- [错误码说明](./api/ERROR_CODES.md)
- [幂等性说明](./api/IDEMPOTENCY.md)
- [秒杀与复购设计](./FLASH_SALE_AND_REPURCHASE_DESIGN.md)
- [数据库迁移说明](./operations/DATABASE_MIGRATIONS.md)
- [健康检查说明](./operations/HEALTH_CHECK.md)
- [客户交付检查表](./private-deploy/CUSTOMER_DELIVERY_CHECKLIST.md)

## 一、系统范围与代码结构

### 1. 当前交付形态

| 组成 | 目录 | 主要技术 | 用途 |
| --- | --- | --- | --- |
| 商城会员端 | `mall-shop-web/` | Vue 3、Vite、Axios、Vue Router | H5、电脑网页、Capacitor App 共用商城业务 |
| 商城管理后台 | `mall-distribution-admin/` | Vue 3、Vite、Element Plus、Axios | 商品、订单、会员、资金、配置与审计运营 |
| 商城业务后端 | `mall-distribution/` | Java 17、Spring Boot 3.5、MyBatis、MySQL、Redis | 商城、分销、钱包、售后、秒杀、复购和 ERP 接口 |
| 公共模块 | `mall-common/` | Spring 公共组件 | 通用响应、异常、幂等、Redis 锁等 |
| 数据模型代码 | `mall-mbg/` | MyBatis Generator | 基础实体和 Mapper |
| 数据库与运维文档 | `document/`、`scripts/` | SQL、Shell、Nginx、systemd | 迁移、备份、发布、回滚和验收 |

仓库根目录的 `VERSION` 是发布版本标记。每次发布必须同时记录 Git 提交、构建编号、前后台 `version.json` 和服务器实际版本。

### 2. 生产请求链路

```text
浏览器或 App
  -> HTTPS / Nginx
  -> /api 或 /api/v1
  -> mall-distribution:8086
  -> MySQL / Redis / 支付、短信、物流、ERP 等外部服务
```

生产后端默认只监听 `127.0.0.1:8086`。容器网络确需对容器网段监听时，通过 `SERVER_ADDRESS=0.0.0.0` 显式覆盖，并由防火墙和容器网络限制访问。

### 3. 前端路由入口

商城公开路由：`/`、`/category`、`/product/:id`、`/notices`、`/notices/:id`、`/login`、`/register`、`/forgot-password`、`/app-download`、`/legal/:type`。

商城登录后路由：`/cart`、`/checkout`、`/flash-sale`、`/repurchase`、`/invite`、`/profile`、`/profile/settings`、`/profile/wallet`、`/profile/team`、`/profile/security`、`/profile/addresses`、`/orders`、`/orders/:id`。

后台入口为 `/admin/`，登录页为 `/admin/login`。其余后台路由均要求管理会话，并按 `shop:product`、`shop:order`、`shop:member`、`distribution:manage`、`commission:manage`、`finance:read`、`finance:manage`、`config:manage`、`import:manage`、`system:manage` 等权限过滤。

## 二、接口通用约定

### 4. 地址版本

- 当前前后台实际客户端使用 `/api` 兼容入口。
- 新外部客户端推荐使用 `/api/v1`。
- Nginx 将 `/api/v1` 转换到同一组 v1 控制器路由。
- 下表统一写推荐浏览器地址 `/api/v1/...`；把 `/api/v1` 改为 `/api` 即为当前兼容地址。
- 业务代码禁止写死正式域名。

本地开发接口文档：

```text
Swagger UI: http://127.0.0.1:8086/swagger-ui/index.html
OpenAPI JSON: http://127.0.0.1:8086/v3/api-docs
```

生产环境必须关闭 Swagger UI 和 `/v3/api-docs`。

### 5. 通用响应

普通 JSON 接口：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | integer | 业务结果；200 成功，400 参数问题，401 未登录，403 无权限，429 过频，500 业务或系统失败 |
| `message` | string | 可直接面向用户的结果说明，不包含堆栈、SQL、密钥和服务器路径 |
| `data` | object/array/scalar/null | 具体业务结果 |

分页接口通常返回 `CommonPage<T>`：`pageNum`、`pageSize`、`totalPage`、`total`、`list`。文件下载、SSE 和第三方回调不使用上述 JSON 结构。

调用方必须同时检查 HTTP 状态和响应体 `code`。

### 6. 身份认证与会话

#### 商城会员端

- 登录或注册成功后，服务端写入 Secure、HttpOnly 的 `shop_session` Cookie。
- 前端请求使用 `withCredentials: true`，写请求自动携带 `X-Shop-Client: storefront`。
- 旧版本本地 Bearer Token 只用于一次性迁移；新代码不得把新 Token 写入本地存储。
- 受保护页面的前端守卫仅用于体验，最终身份和数据权限必须由服务端判断。

#### 管理后台

- 登录成功后，服务端写入 Secure、HttpOnly 的管理会话 Cookie；默认会话为 12 小时。
- 后台写请求携带 `X-Admin-Client: admin-web`。
- 旧 Bearer Token 仅用于一次性会话迁移，完成 `/distribution/admin-auth/me` 校验后清除。
- 每个接口继续执行权限检查；登录成功不代表拥有全部权限。

#### 会话安全

- 商城会员和后台账号均执行单账号单会话。
- 新设备登录后，旧会话的下一次请求返回 401。
- 密码、支付密码、验证码、私钥和完整 Token 禁止写入日志。

### 7. 幂等、加密和重试

- 下单、余额支付、余额转账、提现等写操作使用 `X-Idempotency-Key`。
- 键长度 8～128，只允许字母、数字、点、下划线、冒号和短横线。
- 同一业务请求超时重试必须复用原键；用户重新发起的新业务使用新键。
- 敏感请求由前端请求层按服务端公钥加密，密钥入口为 `GET /api/v1/security/payload-encryption/key`。
- 商城端仅对 GET、HEAD、OPTIONS 的瞬时网络错误自动重试一次；写请求禁止盲目重试。
- 秒杀、资金、退款和发货必须同时依靠服务端状态机、数据库锁或唯一约束，不得只靠前端禁用按钮。

## 三、商城会员端接口

### 8. 登录、账号、短信与邀请

| 方法与地址 | 主要入参 | `data` 出参 | 核心逻辑 |
| --- | --- | --- | --- |
| `GET /api/v1/captcha?scene=shop` | `scene=shop` | `LoginCaptchaVO`：验证码编号、图片 | 创建商城登录图形验证码 |
| `POST /api/v1/shop/auth/register` | `phone`、`username`、`password`、`smsCode`、可选 `nickname`、`inviteCode` | 登录/会员会话结果 | 校验短信、账号唯一性和邀请关系，创建会员并写入会话 Cookie |
| `POST /api/v1/shop/auth/login` | `account`、`loginType=password|sms`；密码方式传 `password`、`captchaId`、`captchaCode`，短信方式传 `smsCode` | 登录/会员会话结果 | 校验账号状态和锁定次数，成功后使旧会话失效 |
| `GET /api/v1/shop/auth/me` | 会话 Cookie | 当前会员资料和权限 | 校验会话，可完成旧 Token 向 Cookie 的一次性迁移 |
| `POST /api/v1/shop/auth/logout` | 会话 Cookie | `boolean` | 注销当前商城会话 |
| `POST /api/v1/shop/auth/resetPassword` | `phone`、`smsCode`、`newPassword` | `boolean` | 验证短信后重置登录密码并失效旧会话 |
| `PUT /api/v1/shop/auth/account` | `username`、`password` | 会员资料 | 为仅手机号会员补充登录账号和密码 |
| `PUT /api/v1/shop/auth/password` | `currentPassword`、`newPassword`、可选 `smsCode` | `boolean` | 校验原密码后修改登录密码 |
| `PUT /api/v1/shop/auth/nickname` | `nickname` | 会员资料 | 更新当前会员昵称 |
| `PUT /api/v1/shop/auth/phone` | `currentPhoneSmsCode`、`newPhone`、`newPhoneSmsCode` | 会员资料 | 双手机号验证后换绑手机号 |
| `POST /api/v1/sms/send` | `phone`、`bizType` | 脱敏发送结果 | 按注册、登录、重置、转账等业务类型发送验证码并限流 |
| `POST /api/v1/sms/send/payment-password` | 当前会员会话 | 脱敏发送结果 | 固定向绑定手机号发送支付密码验证码 |
| `POST /api/v1/sms/verify` | `phone`、`bizType`、`code` | `boolean` | 验证对应业务验证码 |
| `GET /api/v1/shop/invite/my` | 当前会员会话 | 邀请码、链接、二维码和会员信息 | 返回当前会员邀请信息 |
| `GET /api/v1/shop/invite/{inviteCode}` | 路径邀请码 | 邀请人预览 | 注册前确认邀请人，不返回敏感资料 |

账号约束：手机号为中国大陆 11 位号码；登录账号为 4～20 位，以英文字母开头，仅支持字母、数字和下划线；登录密码为 6～32 位；短信验证码为 6 位数字。

### 9. 首页、商品、分类、公告与评价

| 方法与地址 | 主要入参 | `data` 出参 | 核心逻辑 |
| --- | --- | --- | --- |
| `GET /api/v1/shop/home` | 可选首页查询参数 | 商城资料、视觉配置、轮播、分类、商品、公告 | 只返回已启用且当前租户可见内容，商品目录使用短时缓存 |
| `GET /api/v1/shop/legal-config` | 无 | 协议、规则和经营资料配置 | 替换客户经营主体和联系方式占位符 |
| `GET /api/v1/shop/business-config` | 当前会话可选 | 秒杀、复购入口及资格配置 | 只公开允许前端展示的功能开关和资格结果 |
| `GET /api/v1/shop/categories` | 可选状态、首页展示条件 | 分类列表 | 仅返回可用分类，按排序展示 |
| `GET /api/v1/shop/products` | `keyword`、`categoryId`、`pageNum`、`pageSize` 等 | 商品分页 | 按租户、上架状态、库存和渠道过滤 |
| `GET /api/v1/shop/products/{id}` | 商品 ID | `ShopProductVO` | 返回商品、SKU、库存、价格、限购和服务信息 |
| `GET /api/v1/shop/products/{id}/skus` | 商品 ID | SKU 列表 | 返回可用规格组合、价格和库存 |
| `POST /api/v1/shop/products/{id}/purchase-limit/check` | 查询参数 `quantity` | 限购校验结果 | 合并历史有效订单占用和本次数量检查；提交订单时仍会再次校验 |
| `GET /api/v1/shop/products/{id}/reviews` | `pageNum`、`pageSize` | `ProductReviewPageVO` | 返回公开评价、星级分布及当前会员评价资格 |
| `POST /api/v1/shop/products/{id}/reviews` | `rating` 1～5、`content` 最多 1000 字 | 商品评价记录 | 仅确认收货且符合资格的买家可评价 |
| `GET /api/v1/shop/notices` | 分页和类型 | 公告分页 | 仅返回当前租户已启用公告 |
| `GET /api/v1/shop/notices/{id}` | 公告 ID | 公告详情 | 校验公告所属租户和展示状态 |

价格、库存、运费、限购、PV/业绩值必须以提交订单时服务端重新计算结果为准，不接受客户端自报金额。

### 10. 地址、运费与普通订单

| 方法与地址 | 主要入参 | `data` 出参 | 核心逻辑 |
| --- | --- | --- | --- |
| `GET /api/v1/shop/addresses` | 当前会员会话 | 地址列表 | 只返回当前会员地址 |
| `POST /api/v1/shop/addresses` | `id` 可选、`receiverName`、`receiverPhone`、`province`、`city`、`district`、`detailAddress`、`isDefault` | 地址记录 | `id` 为空新增，有值时更新；默认地址互斥 |
| `DELETE /api/v1/shop/addresses/{id}` | 地址 ID | `boolean` | 校验地址属于当前会员后删除 |
| `POST /api/v1/shop/orders/freight-quote` | 收货地区、`items[]` | 运费试算结果 | 按商品和运费模板试算，最终金额在下单时重算 |
| `POST /api/v1/shop/orders` | `addressId` 或完整收货信息、`payType`、`remark`、`items[]`、`businessType=NORMAL`；请求键 | `ShopOrderVO` | 服务端读取商品/SKU、校验库存和限购、计算价格/PV/运费、扣减库存并创建待支付订单 |
| `GET /api/v1/shop/orders` | 状态、分页等 | 当前会员订单分页 | 仅查询本人订单；售后历史不覆盖订单实际履约状态 |
| `GET /api/v1/shop/orders/{id}` | 订单 ID | `ShopOrderDetailVO` | 返回订单、商品、付款、地址、物流、可售后状态及售后记录 |
| `PUT /api/v1/shop/orders/{id}/cancel` | 订单 ID | 订单结果 | 仅允许本人取消待付款订单；释放普通、复购或秒杀对应库存 |
| `PUT /api/v1/shop/orders/{id}/receive` | 订单 ID | 订单结果 | 写入确认收货时间；触发后续评价、售后与奖金等待期逻辑 |
| `POST /api/v1/shop/orders/{id}/pay?payType=...` | 订单 ID、支付方式 | 订单结果 | 兼容支付入口；生产禁止模拟支付 |
| `GET /api/v1/shop/events/orders` | 会话 Cookie，SSE | 订单事件流 | 推送订单状态变化；断线后页面仍需通过订单查询确认最终状态 |

`ShopOrderSubmitDTO.items[]` 每项只接受 `productId`、可选 `skuId` 和正整数 `quantity`。`userId`、`agentId`、价格、退款额和奖金归属均不能信任客户端值。

### 11. 支付宝、余额、转账与提现

| 方法与地址 | 主要入参 | `data` 出参 | 核心逻辑 |
| --- | --- | --- | --- |
| `GET /api/v1/shop/pay/config` | 当前租户 | 可用支付方式 | 只返回已配置并启用的支付渠道 |
| `POST /api/v1/shop/pay/alipay/create?orderId={id}` | 订单 ID | 支付宝官方表单参数 | 校验订单归属、状态、金额和支付宝配置后创建支付 |
| `GET /api/v1/shop/pay/alipay/query?orderId={id}` | 订单 ID | 支付状态 | 主动查询并同步支付宝交易状态 |
| `POST /api/v1/pay/alipay/notify` | 支付宝回调表单 | 文本 `success` 或 `failure` | 先验签，再核对应用、商户、订单和金额，仅成功交易入账；重复通知幂等 |
| `GET /api/v1/payment/checkVerify` | `amount`、`tenantId` | 是否需要短信验证 | 根据租户的大额支付阈值决定是否验证 |
| `GET /api/v1/shop/wallet/summary` | 当前会员 | `ShopWalletSummaryVO` | 返回余额、支付密码设置/锁定状态等 |
| `POST /api/v1/shop/wallet/recipient` | `phone` | `BalanceRecipientVO` | 仅返回转账确认所需的脱敏收款人信息 |
| `PUT /api/v1/shop/wallet/payment-password` | `oldPassword` 可选、`newPassword`、`loginPassword` 可选、`smsCode` | `boolean` | 首次设置与修改使用不同校验；支付密码加密保存 |
| `POST /api/v1/shop/wallet/transfers` | `recipientPhone`、整数 `amount`、`paymentPassword`、可选 `remark`；请求键 | `boolean` | 校验双方账户、密码、余额；账户行锁内完成扣加款和双向流水 |
| `POST /api/v1/shop/wallet/orders/{id}/pay` | `paymentPassword`；请求键 | `ShopOrderVO` | 锁定订单和账户，余额足够且订单待付款时扣款、入账并更新订单 |
| `POST /api/v1/shop/wallet/withdrawals` | `withdrawAmount`、`withdrawType` 1～3、`bankName`、`bankAccount`、`accountName`、`paymentPassword`、`smsCode`；请求键 | `WithdrawRecordVO` | 校验提现规则和余额，创建待审核记录及冻结/扣款流水 |
| `GET /api/v1/shop/wallet/withdrawals` | 当前会员 | 提现记录列表 | 只返回本人提现记录 |
| `GET /api/v1/shop/wallet/flows` | 当前会员 | 余额流水列表 | 只返回本人流水，不把加载失败当作空数据 |

支付同步跳转页面不能作为入账依据。订单支付结果必须以后端订单状态、支付平台查询或验签通过的异步通知为准。

### 12. 售后与退货

| 方法与地址 | 主要入参 | `data` 出参 | 核心逻辑 |
| --- | --- | --- | --- |
| `POST /api/v1/shop/after-sales` | `orderId`、`applyType` 1 仅退款/2 退货退款、`items[{orderItemId,quantity}]`、`reason`、可选 `proofImages` | 售后单 | 校验订单归属、状态、服务端售后截止时间、累计可退数量和金额；退款金额由服务端计算 |
| `PUT /api/v1/shop/after-sales/{id}/cancel` | 售后 ID | 售后结果 | 仅本人可取消仍在等待处理的申请 |
| `PUT /api/v1/shop/after-sales/{id}/return-shipment` | `deliveryCompany`、`deliveryNo` | 售后结果 | 仅审核同意且等待买家寄回时允许填写 |

售后期限由租户设置：`RECEIVE_TIME` 表示从订单确认收货时间起算，`ORDER_TIME` 表示从下单时间起算；有效天数为 0～365。0 天关闭会员自助入口，但不阻止后台人工售后。前端不得自行固定“下单后 7 天”或“签收后 7 天”。

### 12.1 个人中心聚合接口

| 方法与地址 | 主要入参 | `data` 出参 | 核心逻辑 |
| --- | --- | --- | --- |
| `GET /api/v1/shop/profile` | 可选页面所需范围参数 | 当前会员基础资料、钱包和业务入口摘要 | 基础资料优先返回，其他区域加载失败不能使整个个人中心白屏 |
| `GET /api/v1/shop/profile/order-summary` | 当前会员 | 各订单状态数量 | 待发货与在途售后互斥统计，避免同一订单重复显示待办 |
| `GET /api/v1/shop/profile/performance` | 当前会员 | 个人/团队业绩摘要和可见权限 | 仅在租户配置、会员等级和授权均允许时返回相应范围 |

### 13. 秒杀与复购

| 方法与地址 | 主要入参 | `data` 出参 | 核心逻辑 |
| --- | --- | --- | --- |
| `GET /api/v1/shop/flash-sales` | 当前会员 | 可参加的秒杀活动列表 | 过滤未开始、已结束、停用活动，并返回剩余库存和限购 |
| `POST /api/v1/shop/flash-sales/{activityId}/orders` | 地址、商品数量等下单信息；请求键 | `ShopOrderVO` | 校验活动、会员、SKU、限购和库存后创建 `FLASH_SALE` 订单 |
| `GET /api/v1/shop/repurchase/products` | 关键词、分类、分页 | 复购商品分页 | 先校验复购开关和会员准入条件，再返回复购渠道商品 |
| `GET /api/v1/shop/repurchase/products/{id}` | 商品 ID | 复购商品详情 | 返回复购价、复购 PV、复购限购及 SKU 覆盖值 |
| `POST /api/v1/shop/orders` | 与普通订单相同，但 `businessType=REPURCHASE` | `ShopOrderVO` | 复购商品只能独立直接结算，禁止与普通购物车混单 |

秒杀并发依次使用应用限流、Redis Lua 原子防重复与预扣、数据库活动库存原子扣减、商品/SKU 实物库存最终扣减和唯一约束。Redis 不可用时降级为本机限流加数据库原子校验；数据库始终是最终库存事实。

普通、秒杀、复购订单均记录业务类型。秒杀和复购奖金模式可选 `NONE`、`NORMAL`、`CUSTOM`；`CUSTOM` 规则未开发并验收时必须拒绝下单。

## 四、管理后台接口

### 14. 后台登录、工作台与账号权限

| 方法与地址 | 主要入参 | `data` 出参 |
| --- | --- | --- |
| `GET /api/v1/captcha?scene=admin` | `scene=admin` | 后台图形验证码 |
| `POST /api/v1/distribution/admin-auth/login` | 后台账号、密码、验证码编号和验证码 | 管理员资料、权限和会话结果 |
| `GET /api/v1/distribution/admin-auth/me` | 管理会话 | 当前管理员与权限列表 |
| `POST /api/v1/distribution/admin-auth/logout` | 管理会话 | `boolean` |
| `GET /api/v1/distribution/dashboard` | 无 | 工作台统计 `AdminDashboardVO` |
| `GET /api/v1/distribution/dashboard/export` | 无 | 工作台导出文件 |
| `GET /api/v1/distribution/admin-users` | 关键词、状态、分页 | 后台账号分页 |
| `POST /api/v1/distribution/admin-users` | 账号、姓名、密码、状态、权限 | 新账号 |
| `PUT /api/v1/distribution/admin-users/{id}` | 姓名、状态、权限 | 更新结果 |
| `PUT /api/v1/distribution/admin-users/{id}/password` | 新密码及必要验证 | `boolean` |
| `PUT /api/v1/distribution/admin-users/{id}/status` | `status` | `boolean` |
| `PUT /api/v1/distribution/admin-users/{id}/unlock` | 管理员 ID | `boolean` |
| `GET /api/v1/distribution/admin-users/permission-options` | 无 | 可分配权限列表 |

创建和修改后台账号必须执行最小权限原则。资金、退款、导入、配置和系统管理权限不能默认全选。

### 15. 后台商品、分类、规格、运费与内容

| 方法与地址 | 主要入参 | `data` 出参 |
| --- | --- | --- |
| `GET /api/v1/shop/admin/products` | 关键词、分类、状态、渠道、分页 | 商品分页 |
| `POST /api/v1/shop/admin/products` | 商品基础字段 | 商品记录 |
| `PUT /api/v1/shop/admin/products/{id}` | 商品基础字段 | 商品记录 |
| `POST /api/v1/shop/admin/products/publish` | `ProductPublishDTO` | 新建并发布结果 |
| `PUT /api/v1/shop/admin/products/{id}/publish` | `ProductPublishDTO` | 更新并发布结果 |
| `PUT /api/v1/shop/admin/products/{id}/status` | 查询参数 `status` | `boolean` |
| `GET /api/v1/shop/products/{id}/skus` | 商品 ID | SKU 列表 |
| `POST /api/v1/shop/admin/skus` | 商品 ID、规格、价格、库存、状态 | SKU |
| `PUT /api/v1/shop/admin/skus/{id}` | SKU 字段 | SKU |
| `PUT /api/v1/shop/admin/skus/{id}/status` | `status` | `boolean` |
| `GET /api/v1/shop/admin/categories` | 状态等 | 分类列表 |
| `POST /api/v1/shop/admin/categories` | 名称、父级、图标、排序、状态 | 分类 |
| `PUT /api/v1/shop/admin/categories/{id}` | 分类字段 | 分类 |
| `DELETE /api/v1/shop/admin/categories/{id}` | 分类 ID | `boolean` |
| `PUT /api/v1/shop/admin/categories/{id}/status` | `status` | `boolean` |
| `PUT /api/v1/shop/admin/categories/{id}/show-on-home` | `showOnHome` | `boolean` |
| `GET /api/v1/shop/admin/freight-templates` | 租户、状态等筛选 | 运费模板列表 |
| `POST /api/v1/shop/admin/freight-templates` | 模板名称、计费方式、默认运费、地区规则 | 新运费模板 |
| `PUT /api/v1/shop/admin/freight-templates/{id}` | 模板名称、计费方式、默认运费、地区规则 | 更新后的运费模板 |
| `POST /api/v1/shop/admin/media/images` | `multipart/form-data` 的 `file` | 公开媒体相对地址 |
| `GET /api/v1/shop/admin/product-settings` | 无 | 商品全局设置 |
| `PUT /api/v1/shop/admin/product-settings/pv` | `enabled` | `boolean` |
| `GET/POST/PUT /api/v1/shop/admin/banners[/{id}]` | 标题、图片、链接、排序、状态 | Banner 或列表 |
| `PUT /api/v1/shop/admin/banners/{id}/status` | `status` | `boolean` |
| `GET/POST/PUT /api/v1/shop/admin/notices[/{id}]` | 标题、类型、正文、排序、状态 | 公告或列表 |
| `PUT /api/v1/shop/admin/notices/{id}/status` | `status` | `boolean` |
| `DELETE /api/v1/shop/admin/notices/{id}` | 公告 ID | `boolean` |
| `GET /api/v1/shop/admin/reviews` | 关键词、商品、星级、状态、分页 | 评价分页 |
| `PUT /api/v1/shop/admin/reviews/{id}/status` | 评价显示状态 | `boolean` |

商品发布必须由服务端校验 SKU、价格、库存、PV、渠道和限购。图片上传返回相对路径；前端和 App 通过统一媒体地址解析，不把服务器文件路径暴露给客户。

### 16. 后台订单、发货、售后与客服备注

| 方法与地址 | 主要入参 | `data` 出参 |
| --- | --- | --- |
| `GET /api/v1/shop/admin/orders` | 订单状态、售后状态、关键词、备注、时间、分页 | 订单分页 |
| `GET /api/v1/shop/admin/orders/work-summary` | 无 | 待发货、待售后等工作量 |
| `GET /api/v1/shop/admin/orders/export` | 当前筛选条件 | Excel 文件 |
| `GET /api/v1/shop/admin/orders/shipment-template` | 订单筛选条件 | 待发货数据模板 |
| `GET /api/v1/shop/admin/orders/shipments/import-template` | 无 | 空导入模板 |
| `POST /api/v1/shop/admin/orders/shipments/import` | Excel 文件 | 逐行成功/失败结果 |
| `PUT /api/v1/shop/admin/orders/{id}/ship` | `deliveryCompany`、`deliveryNo`、`shipmentQuantity` | 订单结果 |
| `PUT /api/v1/shop/admin/orders/{id}/service-remark` | `serviceRemark` 最多 500 字 | 订单结果 |
| `PUT /api/v1/shop/admin/orders/{id}/cancel` | 订单 ID | 订单结果 |
| `POST /api/v1/shop/admin/orders/{id}/refund` | `refundMode=QUANTITY|AMOUNT`、商品项、金额、原因、售后类型 | 售后/退款结果 |
| `GET /api/v1/shop/admin/after-sales` | 状态、订单、会员、时间、分页 | 售后分页 |
| `PUT /api/v1/shop/admin/after-sales/{id}/audit` | `status`、`auditRemark` | 售后结果 |
| `PUT /api/v1/shop/admin/after-sales/{id}/return-received` | 收货及退款确认数据 | 售后结果 |
| `GET/POST /api/v1/shop/admin/service-addresses` | 地址类型、联系人、省市区、详细地址、默认状态 | 地址或列表 |
| `PUT /api/v1/shop/admin/service-addresses/{id}/status` | `status`、`tenantId` | `boolean` |

发货前必须锁定订单并再次检查订单履约状态、在途售后和剩余可发数量。客服备注不返回会员端；内容变更写入后台操作日志。退款同时锁定订单和售后单，累计退款数量和金额不能超过实际可退值。

### 17. 后台会员、团队、账户与导入

| 方法与地址 | 主要入参 | `data` 出参 |
| --- | --- | --- |
| `GET /api/v1/shop/admin/members` | 关键词、等级、状态、分页 | 会员分页 |
| `POST /api/v1/shop/admin/members` | 账号、手机号、密码、昵称、邀请关系等 | 新会员 |
| `GET /api/v1/shop/admin/members/{id}/profile` | 会员 ID | 会员全景资料 |
| `PUT /api/v1/shop/admin/members/{id}/status` | `status` | `boolean` |
| `PUT /api/v1/shop/admin/members/{id}/unlock` | 会员 ID | `boolean` |
| `PUT /api/v1/shop/admin/members/{id}/payment-password/unlock` | 会员 ID | `boolean` |
| `PUT /api/v1/shop/admin/members/{id}/phone` | 新手机号和验证资料 | 会员资料 |
| `PUT /api/v1/shop/admin/members/{id}/login-password` | 新密码和管理员验证 | `boolean` |
| `PUT /api/v1/shop/admin/members/{id}/level` | 新等级、生效原因 | 会员资料 |
| `GET /api/v1/distribution/agent/list` | 关键词、等级、状态、分页 | 代理/会员关系分页 |
| `GET /api/v1/distribution/agent/roots` | 无 | 根节点列表 |
| `GET /api/v1/distribution/agent/export` | 当前会员筛选 | Excel 文件 |
| `GET /api/v1/distribution/agent/resolve/{memberKey}` | 账号、手机号或代理编号 | 唯一匹配的代理/会员摘要 |
| `GET /api/v1/distribution/agent/{id}` | 代理 ID | 代理详情 |
| `GET /api/v1/distribution/agent/user/{userId}` | 会员用户 ID | 代理详情 |
| `GET /api/v1/distribution/agent/code/{agentCode}` | 代理编号 | 代理详情 |
| `GET /api/v1/distribution/agent/children/{parentId}` | 上级 ID | 直属下级 |
| `GET /api/v1/distribution/agent/descendants/{agentId}` | 代理 ID | 全部下级 |
| `POST /api/v1/distribution/agent/register` | `AgentRegisterDTO` | 代理记录 |
| `PUT /api/v1/distribution/agent/{id}/status` | `status` | `boolean` |
| `PUT /api/v1/distribution/agent/{id}/level` | `AgentLevelAdjustDTO` | 代理记录 |
| `POST /api/v1/distribution/agent/switch-line` | 会员、目标上级、原因、生效方式 | 移线申请/结果 |
| `GET /api/v1/distribution/agent/line-change-applications` | 状态、关键词、分页 | 移线申请分页 |
| `POST /api/v1/distribution/agent/line-change-applications/{id}/audit` | 审核结果、说明 | 申请结果 |
| `GET /api/v1/distribution/agent/qrcode/{agentId}` | 代理 ID | 邀请二维码数据 |
| `GET /api/v1/distribution/agent/team-count/{agentId}` | 代理 ID | 团队人数 |
| `GET /api/v1/distribution/agent/level-counts/{agentId}` | 代理 ID | 各等级人数 |
| `GET /api/v1/distribution/account/agent/{agentId}` | 代理 ID | 账户记录 |
| `GET /api/v1/distribution/account/user/{userId}` | 会员用户 ID | 账户记录 |
| `GET /api/v1/distribution/assets/accounts` | 会员、状态、分页 | 余额账户分页 |
| `GET /api/v1/distribution/assets/flows` | 账户、类型、时间等筛选 | 基础资产流水列表 |
| `GET /api/v1/distribution/assets/flow-records` | 关键词、关联号、方向、来源、时间、分页 | 余额流水分页 |
| `GET /api/v1/distribution/assets/flow-records/summary` | 同流水筛选 | 流水汇总 |
| `POST /api/v1/distribution/assets/issue` | 会员、金额、原因、管理员密码、请求编号 | 新增余额流水 |
| `POST /api/v1/distribution/assets/deduct` | 会员、金额、原因、管理员密码、请求编号 | 扣减余额流水 |
| `POST /api/v1/distribution/import/external-team/file` | 文件、锚点会员 | 导入批次结果 |
| `POST /api/v1/distribution/import/agents/file` | 文件、操作人、批次号 | 导入结果 |
| `POST /api/v1/distribution/import/agents/list` | `agentList`、操作人 | 导入结果 |
| `POST /api/v1/distribution/import/orders/file` | 文件、操作人、批次号 | 导入结果 |
| `POST /api/v1/distribution/import/orders/list` | `orderList`、操作人 | 导入结果 |
| `GET /api/v1/distribution/import/result/{batchNo}` | 批次号 | 成功、失败和错误明细 |

移线必须阻止循环关系并保留历史。人工调账必须验证管理员密码，使用唯一业务编号并记录前后余额。导入必须按租户隔离，逐行校验，失败行不能导致已成功行被重复导入。

### 18. 奖金、业绩、财务、提现和审计

| 接口组 | 主要地址 | 入参和出参摘要 |
| --- | --- | --- |
| 奖金记录 | `GET /api/v1/distribution/commission/records` | `CommissionQueryDTO`；返回 `CommonPage<CommissionRecordVO>` |
| 奖金金额汇总 | `GET /api/v1/distribution/commission/unsettled/{agentId}`、`GET /api/v1/distribution/commission/settled/{agentId}` | 代理 ID；分别返回待结算和已结算金额 |
| 订单触发兼容接口 | `POST /api/v1/distribution/commission/calculate`、`POST /api/v1/distribution/commission/calculate-async` | 订单、会员、金额等；当前均明确拒绝外部直接调用，奖金任务只能由支付确认或订单导入流程创建 |
| 奖金计算任务 | `GET /api/v1/distribution/commission/calculation-tasks`、`POST /api/v1/distribution/commission/calculation-tasks/process`、`POST /api/v1/distribution/commission/calculation-tasks/{id}/process` | 状态、订单、分页或任务 ID；返回任务分页/处理数量/结果 |
| 禁止提前结算兼容接口 | `POST /api/v1/distribution/commission/settle/{recordId}`、`POST /api/v1/distribution/commission/settle-batch` | 记录 ID 或 ID 列表；当前均明确返回失败 |
| 月度结算批次 | `POST/GET /api/v1/distribution/commission/settlement-batches`、`GET /api/v1/distribution/commission/settlement-batches/{id}/items`、`POST /api/v1/distribution/commission/settlement-batches/{id}/execute` | 结算周期和批次；返回批次与明细 |
| 奖金取消 | `POST /api/v1/distribution/commission/cancel/{recordId}` | 取消原因；返回 `boolean` |
| 业绩概览 | `GET /api/v1/distribution/performance/overview/{agentId}` | 开始/结束日期；返回会员业绩概览 |
| 业绩贡献 | `GET /api/v1/distribution/performance/contributions/{agentId}` | 日期范围；返回下级贡献 |
| 下级订单明细 | `GET /api/v1/distribution/performance/contributions/{agentId}/details/{subordinateAgentId}` | 日期范围；返回该下级贡献订单 |
| 业绩来源 | `GET /api/v1/distribution/performance/sources/{agentId}` | 日期范围；返回订单来源明细 |
| 业绩排名 | `GET /api/v1/distribution/performance/ranking` | 周期、范围、分页；返回排名 |
| 汇总刷新 | `POST /api/v1/distribution/performance/refresh/daily`、`POST /api/v1/distribution/performance/refresh/monthly` | `statDate`；返回处理结果 |
| 订单财务追溯 | `GET/PUT /api/v1/distribution/audit/orders/{orderId}/finance` | 订单 ID 或 `OrderFinanceDTO`；返回财务快照 |
| 订单奖金来源 | `GET /api/v1/distribution/audit/bonus-sources` | 订单、会员、奖金类型等筛选；返回来源明细 |
| 公司分配明细 | `PUT /api/v1/distribution/audit/orders/{orderId}/company-shares` | `OrderCompanyShareDTO`；返回更新结果 |
| 会员资金全景 | `GET /api/v1/distribution/audit/person-profile` | 会员标识和日期；返回订单、奖金、余额、提现汇总 |
| 审计订单列表 | `GET /api/v1/distribution/audit/orders` | 订单、会员、时间、分页；返回审计订单分页 |
| 财务汇总 | `GET /api/v1/distribution/audit/finance/summary`、`GET /api/v1/distribution/audit/finance/daily` | 日期和租户；返回汇总/每日明细 |
| 财务导出 | `GET /api/v1/distribution/audit/finance/export` | 当前筛选；返回文件 |
| 财务退款登记 | `POST /api/v1/distribution/audit/finance/refunds` | `FinanceRefundDTO`；返回登记结果 |
| 公司留存汇总 | `GET /api/v1/distribution/audit/finance/company-shares/summary` | 日期和租户；返回汇总 |
| 风险规则与预警 | `GET/POST /api/v1/distribution/audit/finance/risk-rules`、`GET /api/v1/distribution/audit/finance/risk-alerts` | 规则或筛选；返回规则/预警 |
| 会员端可见设置 | `GET /api/v1/distribution/audit/settings`、`PUT /api/v1/distribution/audit/settings/visibility`、`POST /api/v1/distribution/audit/settings/permissions`、`DELETE /api/v1/distribution/audit/settings/permissions/{id}` | 可见项或会员授权；返回配置结果 |
| 提现列表与统计 | `GET /api/v1/distribution/withdraw/list`、`GET /api/v1/distribution/withdraw/stats` | `WithdrawQueryDTO`；返回分页/统计 |
| 提现详情与快捷查询 | `GET /api/v1/distribution/withdraw/{id}`、`GET /api/v1/distribution/withdraw/agent/{agentId}`、`GET /api/v1/distribution/withdraw/pending-audit`、`GET /api/v1/distribution/withdraw/all` | 提现或代理 ID；返回详情/列表 |
| 提现审核 | `POST /api/v1/distribution/withdraw/audit` | 提现 ID、审核状态、原因；返回结果 |
| 确认打款 | `POST /api/v1/distribution/withdraw/confirm-pay/{id}` | `payNo` | 返回结果 |
| 操作日志 | `GET /api/v1/distribution/operation-logs` | 管理员、模块、方法、结果、时间、分页；返回日志分页 |
| 日志保留配置 | `GET /api/v1/distribution/operation-logs/retention` | 无 | 返回保留周期和清理状态 |

后台“单笔提前结算”和“批量提前结算”接口当前明确返回失败；奖金必须在确认收货满既定等待期且无在途售后后由系统结算。

只读查询使用 GET，不得为了传筛选条件改成 POST；操作日志不能仅以 HTTP 方法粗略判断业务是否写入。

### 19. 租户配置、秒杀活动、复购和 ERP

| 方法与地址 | 主要入参 | `data` 出参 |
| --- | --- | --- |
| `GET /api/v1/distribution/tenant/list` | 租户筛选 | 租户列表 |
| `POST /api/v1/distribution/tenant` | 商城名称、品牌、客服、经营资料、规则等 | 租户配置 |
| `GET /api/v1/distribution/tenant/legal-templates` | 无 | 协议模板 |
| `PUT /api/v1/distribution/tenant/{id}/status` | `status` | `boolean` |
| `GET /api/v1/distribution/tenant/{id}/rule-versions` | 租户 ID | 规则版本列表 |
| `GET /api/v1/distribution/tenant/{id}/display-config` | 租户 ID | 视觉配置 |
| `POST /api/v1/distribution/tenant/display-config` | 首页模块、主题、导航、服务开关 | 视觉配置 |
| `GET /api/v1/distribution/tenant/{id}/config-versions` | 租户 ID | 配置历史版本 |
| `POST /api/v1/distribution/tenant/{id}/config-versions/{versionId}/restore` | 配置版本 ID | 恢复后的配置 |
| `GET/PUT /api/v1/distribution/bonus-config/display/{tenantId}` | 奖金展示配置 | 配置结果 |
| `GET/POST/DELETE /api/v1/distribution/bonus-config/pv/products[/{id}]` | 商品 PV 配置 | 配置或列表 |
| `PUT /api/v1/distribution/bonus-config/pv/products/{id}/status` | `status` | `boolean` |
| `GET /api/v1/distribution/bonus-config/pv/orders/{orderId}` | 订单 ID | 订单 PV 明细 |
| `GET /api/v1/distribution/bonus-config/snapshots/orders/{orderId}` | 订单 ID | 奖金计算快照列表 |
| `POST /api/v1/distribution/bonus-config/simulate` | `BonusSimulationDTO` | 奖金模拟结果 |
| `GET /api/v1/shop/admin/flash-sales` | 状态、商品、时间、分页 | 秒杀活动分页 |
| `POST/PUT /api/v1/shop/admin/flash-sales[/{id}]` | `FlashSaleActivitySaveDTO` | 秒杀活动 |
| `PUT /api/v1/shop/admin/flash-sales/{id}/status` | `status` | `boolean` |
| `GET /api/v1/distribution/erp/integrations` | `tenantId` | ERP 配置列表 |
| `POST /api/v1/distribution/erp/integrations` | ERP 类型、地址、鉴权和启用状态 | ERP 配置脱敏结果 |
| `GET /api/v1/distribution/erp/tasks` | 状态、订单、分页 | ERP 任务分页 |
| `POST /api/v1/distribution/erp/tasks/{id}/retry` | 任务 ID | 重试结果 |

租户保存和视觉配置保存会生成配置版本，支持审计和恢复。密钥类 ERP 配置返回时必须脱敏或不回传原值。未确定客户 ERP 时保持关闭；不得用演示地址模拟正式对接。

`FlashSaleActivitySaveDTO` 关键字段：`activityName`、`productId`、可选 `skuId`、`flashPrice>=0.01`、`flashPv>=0`、`totalStock>=1`、`perUserLimit>=1`、`startTime`、未来的 `endTime`、`status`。

## 五、核心业务逻辑

### 20. 普通订单状态链路

```text
选品 -> 服务端限购预检 -> 地址/运费试算 -> 提交订单
-> 锁库存并创建待付款
-> 余额或第三方支付
-> 待发货
-> 部分/全部发货
-> 确认收货
-> 完成、评价、售后窗口和奖金等待期
```

- 待付款订单超时由定时任务关闭并释放库存。
- 下单、支付、取消、发货、收货和退款均需检查当前状态，禁止跨状态直接更新。
- 订单业务类型 `NORMAL`、`FLASH_SALE`、`REPURCHASE` 全链路保留，退款和库存回补必须按原业务类型执行。
- 客户下单备注和后台客服备注为两个字段；客服备注永不返回会员端。

### 21. 库存与限购

- 页面库存仅用于展示，服务端下单事务为最终判断。
- 普通订单校验商品状态、SKU 状态、实物库存、安全库存和会员限购。
- 会员限购按同一商品跨 SKU 汇总，并计入未关闭的历史订单占用。
- 秒杀除实物库存外，还校验活动总库存和会员活动限购。
- 待付款取消/超时释放库存；已支付退货按实际退回数量回补，但秒杀同场资格不重新开放。

### 22. 售后、退款和发货互斥

- 处理中售后从待发货工作队列排除，人工发货、ERP 发货和批量发货均拒绝。
- 售后取消或驳回后，订单按原履约状态恢复。
- 部分退款后只允许继续发剩余未退款数量；全额退款关闭订单。
- 已取消、已拒绝的历史售后不覆盖订单当前状态，也不显示在后台全部订单的商品摘要中。
- 退款金额基于订单实付和累计已退款数据计算，客户端输入只能作为申请意图。

### 23. 物流与轨迹

- 发货保存物流公司、物流单号、发货数量和时间。
- 没有物流服务商时，会员端只展示系统真实持有的信息，不虚构“运输中”。
- 接入真实物流后，应新增承运商代码映射、订阅/查询任务、轨迹表、签名校验、重试、去重和会员端节点展示。
- 真实快递签收扫描与“会员确认收货”是两个事件。当前售后 `RECEIVE_TIME` 使用订单确认收货时间；客户要求以承运商签收为准时，必须在客户物流方案中明确改造和兜底规则。

### 24. 秒杀高并发

1. 网关/应用层按活动和会员限流。
2. Redis Lua 原子检查活动、会员重复购买和缓存库存。
3. 数据库使用条件更新扣减活动库存，防止缓存与数据库不一致导致超卖。
4. 在同一事务中扣减商品/SKU 实物库存并创建订单。
5. 唯一约束防止同一会员绕过限购重复占位。
6. 任一步失败时按补偿逻辑释放已占资源。
7. Redis 故障时降级到本机限流和数据库原子扣减，不跳过数据库校验。

压测至少覆盖：同一会员重复点击、多人抢最后一件、Redis 短暂不可用、数据库锁等待、订单创建超时、取消释放库存和支付回调重复。

### 25. 复购结算扩展点

- 复购商品使用独立渠道、价格、PV、限购和 SKU 覆盖配置。
- 复购只支持独立直接结算，不进入普通购物车，不允许混单。
- 准入策略支持完成首单会员、代理及以上、全部注册会员。
- `NONE` 不产生奖金；`NORMAL` 沿用普通奖金；`CUSTOM` 进入客户定制扩展点。
- 新增客户结算模式时，应实现独立策略标识、配置版本、订单快照、计算服务、冲销逻辑、审计视图和模拟/回归用例，禁止直接改写历史普通订单规则。

### 26. 奖金、业绩和资金

- 奖金计算任务按订单和规则版本保存快照，避免后续配置变化改写历史计算。
- 结算依赖确认收货等待期且无在途售后；退款需冲销或取消对应未结算奖金。
- 余额账户变更必须产生唯一业务流水；金额与账户更新在同一事务和行锁中完成。
- 支付密码和后台账号均有失败次数与锁定机制；人工解锁不修改原密码。
- 财务总览必须能由订单、支付、退款、奖金、余额流水和提现记录逐层追溯。

### 27. ERP 集成

- 订单支付成功后创建 ERP 推送任务，不在主下单事务中长时间等待外部系统。
- 任务保存请求摘要、次数、下次重试时间、状态和脱敏错误。
- ERP 回传发货必须校验配置身份、签名、订单、数量和重复回调。
- 重试采用退避策略；超过阈值进入人工处理，不无限快速重试。
- 不同客户 ERP 使用适配器隔离，共用标准订单、商品、收货人和发货模型。

## 六、关键配置与数据迁移

### 28. 生产配置

关键环境变量分类：

| 分类 | 变量示例 | 维护要求 |
| --- | --- | --- |
| 数据库 | `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`、`DB_SSL_MODE` | 密码只保存在服务器私有配置；远程连接启用 TLS |
| Redis | `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE` | 秒杀、幂等和分布式任务依赖；监控连接和延迟 |
| 会话 | `ADMIN_SESSION_HOURS` | 默认 12 小时；修改需安全评估 |
| 商品媒体 | `SHOP_MEDIA_STORAGE_DIR`、图片尺寸和质量配置 | 上传目录纳入备份并限制文件类型与大小 |
| 订单 | `ORDER_PENDING_TIMEOUT_MINUTES`、扫描间隔 | 默认 30 分钟关闭待付款订单 |
| 大额验证 | `PAYMENT_LARGE_AMOUNT_VERIFY_ENABLED`、`PAYMENT_LARGE_AMOUNT_VERIFY_THRESHOLD` | 与客户资金风控规则一致 |
| 短信 | 提供商开关、AccessKey、签名、模板 | 不提交仓库，不在日志输出验证码 |
| 支付 | 支付宝/微信应用、商户、私钥、公钥、回调地址 | 发布前用真实沙箱或小额测试验证签名与回调 |

### 29. 数据库迁移

- 基线与升级 SQL 位于 `document/sql/`。
- 受控增量迁移位于 `document/db/migrations/`。
- 当前功能基座重点迁移：租户配置版本与操作日志索引、售后期限配置、秒杀与复购基座、订单客服备注。
- 新迁移文件一经在任何共享环境登记，禁止修改原内容；只能新增更高版本迁移。
- 发布前执行迁移计划校验和完整备份，发布后核对迁移登记、表结构、索引和核心业务计数。

### 30. 配置版本与客户定制

- 商城名称、Logo、客服、经营主体、协议、视觉和业务开关均从租户配置读取，禁止重新写死“灵启”或某客户名称。
- 租户配置修改保留历史版本，可按版本恢复。
- 客户特有奖金、复购结算、物流和 ERP 通过策略/适配器扩展；公共主流程只保存稳定业务类型和快照。
- 客户交付前必须形成客户确认的规则版本，包含售后起算、有效期、支付、发货、奖金、复购、提现和退款责任边界。

## 七、开发、测试与发布维护

### 31. 本地构建与测试

后端：

```bash
mvn test
mvn clean package
```

商城会员端：

```bash
cd mall-shop-web
npm install
npm test
npm run build
```

管理后台：

```bash
cd mall-distribution-admin
npm install
npm test
npm run build
```

前端提交前至少执行对应项目完整回归和生产构建；订单、资金、售后、奖金、秒杀、复购或权限变更必须增加服务端测试。

### 32. 接口变更流程

1. 先明确业务状态、权限、幂等和失败补偿。
2. 更新 DTO 校验和 OpenAPI 注解。
3. v1 内只新增可选字段，不删除或改变既有字段语义。
4. 不兼容变更使用 `/api/v2` 并保留迁移期。
5. 更新前端统一 API 客户端，不在页面组件分散写地址。
6. 增加成功、参数错误、未登录、无权限、重复提交和并发冲突测试。
7. 本地启动后检查 Swagger UI，并重新导出 OpenAPI。
8. 同步更新本手册、操作手册、发版报告和当日日志。

### 33. 发布前检查

1. 读取 `document/RELEASE_REPORT.md`，核对线上、本地和远程最高版本。
2. 确认工作区、分支和 Git 提交准确，不覆盖另一台电脑的新提交。
3. 检查提交内容不含 `.env`、密码、密钥、验证码、客户私有配置和数据库备份。
4. 执行后端、商城、后台完整测试和生产构建。
5. 生成带 Git 提交和构建编号的两端 `version.json`。
6. 备份数据库、程序、上传文件、Nginx、systemd、证书和私有配置。
7. 校验备份包哈希和内容清单。
8. 执行幂等数据库迁移并核对登记。
9. 原子切换后端和静态资源。
10. 验证版本、核心页面、接口、服务、数据库、Redis、日志和核心计数。
11. 失败时按发布脚本自动回滚，保留失败现场、备份和双方 Git 记录。
12. 发布成功后更新发版报告，不重复发布同一构建。

### 34. 日常健康检查

| 检查项 | 期望结果 |
| --- | --- |
| 商城首页与后台登录页 | HTTP 200，资源文件可加载 |
| 商城首页、商品接口 | `code=200`，租户和商品数据正确 |
| 后台未登录身份接口 | 返回 401，不泄露管理员资料 |
| 后端 Actuator 健康 | `UP` |
| Nginx、MySQL、Redis、后端服务 | active |
| 订单、奖金、ERP、超时关闭定时任务 | 正常持锁执行，无重复处理 |
| 支付和短信回调 | 签名通过、延迟和失败量在正常范围 |
| 备份任务 | 最近一次成功且抽样恢复可用 |
| 证书 | 未临近过期，续期链路可访问 |
| 错误日志 | 无新增持续性 ERROR、敏感信息和重复业务异常 |

### 35. 常见故障定位

| 现象 | 优先检查 |
| --- | --- |
| 前端白屏或菜单点击无反应 | `version.json`、入口资源是否匹配、旧分块缓存、浏览器控制台 |
| 登录后立即退出 | Cookie Domain/Path/SameSite/Secure、服务器时间、单会话是否被新设备顶替 |
| 订单重复或一直处理中 | 请求键、订单状态、数据库事务、Redis 幂等占位、支付回调 |
| 库存不一致 | 活动库存、SKU 库存、取消/超时/退款回补记录和事务日志 |
| 售后入口不正确 | 租户起算模式、有效天数、订单时间、确认收货时间和服务端截止时间 |
| 已发货但没有物流轨迹 | 是否只录入公司和单号、物流服务是否接入、承运商映射和订阅任务 |
| 奖金未计算或未结算 | 计算任务、Redis 分布式锁、订单收货时间、等待期、在途售后、规则版本 |
| ERP 没有订单或发货未回传 | 集成开关、任务状态、鉴权/签名、适配器日志、重试次数 |
| 余额或财务数据争议 | 业务编号、账户流水、订单支付/退款、奖金与提现记录、操作日志 |

## 八、维护边界

### 36. 禁止事项

- 禁止在未备份和未核对版本的情况下直接覆盖线上程序。
- 禁止通过修改数据库结果代替修复订单、售后、奖金或资金状态机。
- 禁止删除测试或业务数据来“让报表看起来正确”；测试环境清理也要先确认范围并备份。
- 禁止把客户密钥、支付私钥、短信 AccessKey、数据库密码和真实验证码提交 Git。
- 禁止前端自算最终价格、退款额、奖金、售后截止时间或库存。
- 禁止秒杀、转账、支付、提现、退款、发货只依赖前端按钮防重复。
- 禁止客户定制规则未确认、未测试时开启对应正式入口。

### 37. 文档维护责任

- 页面操作改变时更新《用户操作手册》。
- 接口、DTO、返回模型或核心逻辑改变时更新本手册和 OpenAPI。
- 客户规则改变时保存新的规则/配置版本，不覆盖历史订单快照。
- 每次版本变更更新 `VERSION`、`document/RELEASE_REPORT.md` 和 `document/DAILY_LOG.md`。
- 线上状态只能以实时版本文件、运行包、Git 身份、数据库和服务检查为准，不能只看旧日志或聊天记录。
