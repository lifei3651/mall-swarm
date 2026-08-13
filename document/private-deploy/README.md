# 私有化独立部署交付包

本目录面向“一个客户一台服务器 / 一套数据库 / 一套配置”的交付方式，不做多客户共库 SaaS。
每个客户都必须拥有独立的域名、数据库、运行环境、密钥和备份目录；客户之间不复制会员、订单、余额或奖金数据。

## 交付前准备

1. 在客户服务器安装 Docker 和 Docker Compose。
2. 执行 `./mvnw package -DskipTests -Ddocker.skip=true` 生成后端 Jar。
3. 执行 `cd mall-distribution-admin && npm ci && npm run build` 生成后台前端静态文件。
4. 将 `mall-distribution-admin/dist` 内容放到 `document/private-deploy/html`。
5. 复制 `customer.env.example` 为 `.env`，为客户生成独立强密码和域名配置。
6. 只把客户自己的支付、短信和域名参数填入 `.env`；不要把真实密钥写进 Git、镜像、前端或工单截图。

`.env` 只供 Compose 解析。模板通过每个服务的 `environment` 白名单分发变量：支付宝私钥和短信
AccessKey 只进入 `mall-distribution`，搜索、监控、网关等容器无法读取。禁止给应用服务重新增加
`env_file: .env`，否则会把整套客户密钥再次注入所有容器。

## 启动

```bash
cd document/private-deploy
docker compose --env-file .env -f docker-compose.private.yml up -d --build
```

## 初始化

首次启动 MySQL 会自动执行：

- `document/sql/*.sql`
- `mall-distribution/document/sql/*.sql`
- `document/private-deploy/initdb/*.sql`

`initdb/99_customer_init.sql` 只放客户默认资料。支付商户、短信、OSS、奖金规则等按客户交付单追加。
奖金制度、奖金比例、会员模型和 PV 口径不由本模板固定；它们必须在客户规则确认后，以该客户专属交付包落地。

## 访问

- Nginx: `http://服务器IP/`

模板只向公网发布 Nginx 的 80/443；MySQL 仅绑定宿主机 `127.0.0.1:3306` 供备份使用。
Redis、Nacos、RabbitMQ、MongoDB、Elasticsearch、网关、监控及业务后端只允许 Compose
内部网络访问。运维请使用 `docker compose exec <服务名> ...`，不要临时向公网映射内部端口。

## 首次交付必做

1. 修改 `.env` 中所有 `change_me_*`。
2. 首次登录后修改后台默认账号密码。
3. 配置真实域名和 HTTPS 证书，把证书放到 `certs/` 后更新 Nginx server 配置。
4. 配置数据库备份定时任务。
5. 在 `.env` 中配置支付、短信、媒体存储等客户专属参数；物流、售后和视觉资料通过后台与客户交付单配置。
6. 支付宝回调地址必须使用客户自己的 HTTPS 域名，并确保公网可访问：
   - `https://客户域名/api/pay/alipay/notify`
   - `https://客户域名/api/pay/alipay/return`
7. 手机网站支付必须使用已签约的 `alipay.trade.wap.pay` 能力；应用密钥、公钥只注入服务器运行环境，不进入代码仓库。
8. 短信正式环境必须保持 `SMS_PROVIDER_ENABLED=true` 且关闭测试验证码；每个业务类型都要填写已审核的短信模板 CODE。

## 备份

在服务器上执行：

```bash
cd document/private-deploy
DB_HOST=127.0.0.1 DB_USER=root DB_PASSWORD=你的密码 DB_NAME=mall ./scripts/backup.sh
```

建议加到 crontab，每天凌晨备份一次，并同步到对象存储或异地服务器。

## 恢复

```bash
cd document/private-deploy
DB_HOST=127.0.0.1 DB_USER=root DB_PASSWORD=你的密码 DB_NAME=mall ./scripts/restore.sh ./backups/mysql/mall_yyyyMMdd_HHmmss.sql.gz
```

## 验收清单

- 后端全模块打包通过。
- 后台前端 `npm run build` 通过。
- `docker compose ps` 所有服务为 running/healthy。
- 网关、后台、分销服务、监控页面可访问。
- 新建商品、下单、取消订单、售后申请、后台发货流程可跑通。
- 支付和奖金按客户项目单独验收。

## 客户交付边界

- 本阶段只确认独立部署、域名、支付、短信、媒体存储、备份和审计等基础能力。
- 奖金模型一定按客户重新设计；不要因为模板中出现“奖金”字样就直接启用既有制度。
- 客户提交新资料时，只更新该客户的交付配置或后台资料，不覆盖其他客户，也不删除历史订单、余额、会员和流水。
- 任何配置变更都先备份、再变更、后验收；支付回调、短信发送和余额变动必须保留可追溯日志。
