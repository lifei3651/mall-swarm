# 1.0.158 收口验证版服务器 S 回执

执行日期：2026-09-23（Asia/Shanghai）

授权范围：用户明确授权对固定的 1.0.158 执行备份、隔离迁移演练及受控服务器部署。**不包含微信上传、体验版、提审、正式版或真实资金交易。**

## 固定身份与发布前门禁

- 产品提交 `fcadf4099861a0a5359b12761cbf232c160635a7`；源码树 `cf898394b8465b8e2b4602c085ecb43c13b326b1`；构建号 `20260923-closure-1.0.158`。
- 统一候选包 `146869427` 字节，SHA-256 `6426676a6fc0814cedee9c412110e95e3e12a533747201d2d64c8b42cdc8bd3e`，与正式主机暂存的精确包一致；私有独立留档及实际下载回读见 `RETENTION_RECEIPT.md`。
- 本地完整正式准入通过：唯一仓库/远程同步、候选内部逐文件身份、迁移集合、独立留档回执、正式主机/四服务/健康/Nginx 配置及公网基线均通过。
- 正式主机 `VM-4-6-rockylinux` 现场复核为 1.0.154，旧 JAR SHA-256 `786aec477acb1deaf71c058bfbd55e9d57a9953c31bafa859ae2c9b43695dd96`、数据库迁移 40/40、四服务 active、health UP；服务器精确候选包 SHA-256 与本机一致。
- 正式主机原无 `node` 命令，包内授权发布路径需要它核验留档。先停止推进；从 [Node.js 官方 v22.23.2 发布目录](https://nodejs.org/download/release/v22.23.2/)将 Linux x64 二进制下载到独立临时目录，以官方 `SHASUMS256.txt` 验证，通过后仅把该临时目录加入本次脚本的 `PATH`。没有系统安装、修改冻结包、服务配置或常驻端口；发布完成后临时运行时已删除。
- 包内原版 `verify-artifact-retention.mjs` 在正式主机返回 `retention-verified`；包内原版后端 `--preflight-only` 返回 `previous=1.0.154 target=1.0.158 migrations=40:40`。管理后台、公开商城和团队 H5 的包内原版只读预检均通过。

## 受控执行与回滚点

按包内原版脚本依次执行后端、管理后台、公开商城 H5、团队 H5 的 `--authorize-release`，四项均以成功回执退出。后端脚本先完成正式库完整备份，在隔离恢复库运行第 41 条迁移的首次/重跑与业务快照、字段和回填校验；通过后才停止服务、迁移正式库并切换固定 JAR。隔离库已删除，正式迁移为 41/41。

| 部分 | 发布前完整备份 | 发布后完整备份 | 回滚目录 |
|---|---|---|---|
| 后端/数据库 | `/opt/lingqimall/backups/full/20260923_142159` | `/opt/lingqimall/backups/full/20260923_142252` | `/opt/lingqimall/backups/closure-backend-158.chpkvw` |
| 管理后台 | `/opt/lingqimall/backups/full/20260923_142347` | `/opt/lingqimall/backups/full/20260923_142359` | `/opt/lingqimall/backups/admin-158.J5HwfW` |
| 公开商城 H5 | `/opt/lingqimall/backups/full/20260923_142431` | `/opt/lingqimall/backups/full/20260923_142443` | `/opt/lingqimall/backups/public-h5-158.TyiY6S` |
| 团队 H5 | `/opt/lingqimall/backups/full/20260923_142511` | `/opt/lingqimall/backups/full/20260923_142523` | `/opt/lingqimall/backups/team-h5-158.KbCWyO` |

八个完整备份的 `SHA256SUMS`、数据库 gzip 和文件归档均由独立只读命令再次检查通过；四个回滚目录中对应原版文件/目录均存在。没有执行回滚。

## 发布后独立只读复核

- 正式主机 `VERSION=1.0.158`，后端 JAR SHA-256 `cbd249426c1dfcdad4de7dbf65f9780486b47a9807e35ae3eb27fb8252f1ae1f`；迁移历史 41/41 成功，`dms_shop_order_item.service_tags` 为 JSON，适用商品的历史快照缺口为 0；没有遗留 `m158v_%` 隔离库。
- 后端、Nginx、MySQL、Redis 四项服务均 active，后端健康接口为 `UP`。公开商城首页接口 JSON 业务码 200；未登录订单接口 HTTP 401、JSON 业务码 401 且无业务数据。
- 三套公网 `version.json` 分别显示管理后台、公开商城、团队 H5 的 `1.0.158 / fcadf4099861a0a5359b12761cbf232c160635a7 / 20260923-closure-1.0.158`。包内脚本还逐站检查网页入口与资源、静态文件树、其他站点及配置未变。
- 从本次后端发布开始到最终复核，预设关键故障日志模式命中 0。Nginx 语法检查通过；原有证书无 OCSP responder 的 `ssl_stapling` 警告仍存在，不作为此次发布成功或失败的新能力证据。
- 业务监控采集端点 `/actuator/prometheus` 的发布后只读复核仍为 HTTP 404；Prometheus/Alertmanager 采集与告警闭环继续列为待收口，不因服务器 S 成功而标记完成。

**结论：1.0.158 的服务器 S 已完成并独立复核。微信平台开发版仍为 1.0.154，1.0.158 未上传、未绑定体验版、未提审或正式发布；真机与真实支付/余额/退款/售后/发货/物流/奖金/资金 R 均未验证，P0 未清零。**
