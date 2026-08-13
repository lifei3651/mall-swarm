# 客户私有化安全部署包

本目录是未来客户部署的唯一入口。部署人员不需要记住端口、密钥和检查命令，但必须使用
`scripts/deploy.sh`；禁止绕过该入口直接执行 `docker compose up`。

当前商城基座只部署四个必要服务：Nginx、商城后端、MySQL、Redis。旧模板引用的网关、搜索、
监控、MongoDB、RabbitMQ、Elasticsearch和Nacos不是当前仓库的可运行组件，已全部移除，避免
交付时开放无用服务或引用不存在的 Dockerfile。

## 安全边界已经写死

- 公网只发布 Nginx `80/443`。
- MySQL、Redis和后端均不映射宿主机端口，只能在 Docker 内部网络访问。
- 数据网络设置为 Docker `internal`，Nginx不能直接读取数据库和Redis。
- 后端强制 `prod`，强制关闭模拟支付、固定验证码和Nacos客户端。
- 后端使用非 root 用户、`no-new-privileges`和只读根文件系统运行；只有上传卷和临时目录可写。
- `.env` 必须为 `600`，支付、短信、数据库和会话密钥只进入确实需要它们的容器。
- 生产静态资源禁止 source map，商城与后台构建版本必须等于仓库根 `VERSION`。
- 数据库升级前自动备份；迁移按版本号、SHA-256和当前部署机的单机锁登记，重复或冲突立即停止。
- MySQL、Redis和Nginx使用经过测试的明确版本标签；升级镜像版本必须先在隔离环境重跑全部流程，不能临时改成 `latest`。
- 云安全组必须登记证据，SSH禁止 `0.0.0.0/0` 或 `::/0`，但最终仍需在客户云控制台真实执行。

## 标准部署流程

### 1. 生成生产构建

```bash
cd document/private-deploy
./scripts/deploy.sh build
```

该命令执行后端完整测试与打包、商城测试与构建、后台测试与构建，再把两个前端放入忽略 Git 的
`html/`。发现测试失败、source map或版本不一致会停止。

### 2. 为客户生成独立密钥

```bash
./scripts/deploy.sh prepare \
  --domain mall.customer.com \
  --admin-domain mall.customer.com \
  --ssh-cidr 203.0.113.10/32 \
  --project customer_code \
  --customer-name 客户公司名称 \
  --brand 客户商城名称
```

程序使用 OpenSSL 生成独立的 MySQL、Redis和会话密钥，并创建权限为 `600` 的 `.env`。
如果 `.env` 已存在会拒绝覆盖，防止客户密钥丢失。

### 3. 配置证书、支付和短信

- 将客户域名证书保存为 `certs/cert.pem`，私钥保存为 `certs/key.pem`并设为 `600`。
- 客户未开通支付宝或短信时保持对应 `ENABLED=false`、资料留空。
- 启用支付宝时必须填写同一应用的APPID、应用私钥、支付宝公钥和客户HTTPS回调地址。
- 启用短信时必须填写客户自己的AccessKey、签名及全部已审核模板编号。
- 真实密钥不进入Git、镜像、前端、聊天截图或工单正文。

### 4. 在云控制台设置安全组并登记证据

云安全组属于客户云账号，仓库无法替客户点击控制台。必须在控制台只公开：

- `80/tcp`、`443/tcp`：公网。
- `22/tcp`：仅固定运维IP或VPN网段；数据库维护通过SSH进入服务器后使用容器命令，不开放3306。
- 禁止公网开放 `3306`、`6379`、`8086`及所有临时调试端口。

完成后登记截图文件名或工单编号：

```bash
./scripts/deploy.sh firewall \
  --ssh-cidr 203.0.113.10/32 \
  --evidence cloud-sg-ticket-20260813
```

### 5. 强制预检

```bash
./scripts/deploy.sh check
```

该步骤会展开最终 Compose 配置并检查端口、内部网络、特权模式、Docker socket、密钥容器边界、
生产模式、测试开关、域名、CORS、云安全组确认、TLS有效期、构建产物和文件权限。任何一项失败都
不会继续。

### 6. 部署或升级

```bash
./scripts/deploy.sh apply
```

已有数据库时先生成并校验升级前全库备份；发现历史数据卷但数据库无法启动时直接停止。随后只
启动MySQL和Redis，执行版本化迁移，再构建并切换商城后端和Nginx。部署完成后自动执行安全验收。

### 7. 独立复验

```bash
./scripts/deploy.sh verify
```

复验内容包括所有必要容器、真实监听端口、数据库与Redis未映射宿主机、内部后端健康、HTTPS证书以及公网
Actuator、Swagger和接口文档返回404。结果保存到权限为 `600` 的 `reports/`，记录版本、Git提交、
云安全组证据和安全边界，不记录任何密码或私钥。

## 日常备份与恢复

升级前备份由统一部署入口自动完成。日常备份仍可执行：

```bash
./scripts/backup.sh
```

恢复属于破坏性操作，必须先停止业务、保留当前备份并明确目标文件：

```bash
./scripts/restore.sh --confirm ./backups/mysql/指定备份.sql.gz
```

## 仍需按客户确认的业务

品牌资料、经营资质、物流服务商、ERP、售后期限、支付商户、短信、秒杀、复购和奖金制度必须按
客户确认配置或定制。部署安全流程不会替客户决定业务制度，也不会自动执行真实支付、退款或短信。
