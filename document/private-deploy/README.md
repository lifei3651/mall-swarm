# 私有化独立部署交付包

本目录面向“一个客户一台服务器 / 一套数据库 / 一套配置”的交付方式，不做多客户共库 SaaS。

## 交付前准备

1. 在客户服务器安装 Docker 和 Docker Compose。
2. 执行 `./mvnw package -DskipTests -Ddocker.skip=true` 生成后端 Jar。
3. 执行 `cd mall-distribution-admin && npm ci && npm run build` 生成后台前端静态文件。
4. 将 `mall-distribution-admin/dist` 内容放到 `document/private-deploy/html`。
5. 复制 `customer.env.example` 为 `.env`，为客户生成独立强密码和域名配置。

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

## 访问

- Nginx: `http://服务器IP/`
- 网关直连: `http://服务器IP:8201/`
- 分销服务直连: `http://服务器IP:8086/`
- 监控: `http://服务器IP:8101/`
- RabbitMQ 管理端: `http://服务器IP:15672/`
- Nacos: `http://服务器IP:8848/nacos`

生产环境建议只开放 80/443，其他端口通过安全组或防火墙限制到运维 IP。

## 首次交付必做

1. 修改 `.env` 中所有 `change_me_*`。
2. 首次登录后修改后台默认账号密码。
3. 配置真实域名和 HTTPS 证书，把证书放到 `certs/` 后更新 Nginx server 配置。
4. 配置数据库备份定时任务。
5. 配置支付、短信、OSS、物流等客户专属参数。

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
