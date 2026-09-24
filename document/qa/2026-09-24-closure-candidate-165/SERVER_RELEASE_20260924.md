# 1.0.165 收口验证版服务器 S 回执

执行日期：2026-09-24（Asia/Shanghai）。仅为受控验证版服务器部署；不代表微信体验版、审核、正式版或真机交易通过。

## 固定身份及准入

- 产品提交 `60a6e5e3fe1255bace2156f0f59089c9a341db0f`，源码树 `7e4df7d0dd8c0dd242db073f8cb21ec203a44baf`，构建号 `20260924-closure-1.0.165`。
- 统一候选包 `146871320` 字节，SHA-256 `66b528b8df81ac30fee3b15d6a09647015dc8ba62ac2f0e3a41d662b23300652`；正式主机 `/tmp/lingqimall-closure-165.IaEcoL/candidate.tar.gz` 哈希一致。私有不可变留档与实际下载回读由 `artifact-retention.json` 证明，本地完整发布准入和正式主机包内原版 `retention-verified` 均通过。
- 发布前为 `1.0.158`、旧 JAR SHA-256 `cbd249426c1dfcdad4de7dbf65f9780486b47a9807e35ae3eb27fb8252f1ae1f`，正式库迁移 `41/41`、四服务 active。新包不增加迁移，发布脚本只校验 `41/41`，不重跑任何正式 SQL。
- 正式主机仍无系统级 Node。使用 Node.js 官方 v22.23.2 Linux x64 临时运行时，并以官方 `SHASUMS256.txt` 校验成功；运行时始终位于候选解包目录之外，仅用于包内留档验签。部署后临时运行时已删除，未系统安装。

## 备份、隔离演练与切换

包内原版脚本依次部署后端、管理后台、公开商城 H5、团队 H5，四项均返回 `release-success`。后端发布前完整备份恢复到独立 `m165v_*` 数据库，验证迁移集合、`service_tags` 结构及回填、业务数量与余额快照未变；演练库已删除。正式库只读核对前后迁移历史始终 `41/41`。没有发起真实退款。

| 部分 | 发布前完整备份 | 发布后完整备份 | 回滚目录 |
|---|---|---|---|
| 后端/数据库 | `/opt/lingqimall/backups/full/20260924_114840` | `/opt/lingqimall/backups/full/20260924_114930` | `/opt/lingqimall/backups/closure-backend-165.hfQVt4` |
| 管理后台 | `/opt/lingqimall/backups/full/20260924_115036` | `/opt/lingqimall/backups/full/20260924_115048` | `/opt/lingqimall/backups/admin-165.iuC54K` |
| 公开商城 H5 | `/opt/lingqimall/backups/full/20260924_115119` | `/opt/lingqimall/backups/full/20260924_115130` | `/opt/lingqimall/backups/public-h5-165.gRbVW3` |
| 团队 H5 | `/opt/lingqimall/backups/full/20260924_115201` | `/opt/lingqimall/backups/full/20260924_115212` | `/opt/lingqimall/backups/team-h5-165.8h1yzQ` |

八份备份的 `SHA256SUMS`、数据库 gzip 和文件归档均由独立只读命令复核；四个回滚目录存在。没有执行回滚。

## 切换后的独立只读复核

- 主机 `VERSION=1.0.165`，JAR SHA-256 `934d345dee88ea7b9d12f8aeff5998e97c1f8d447caff5945e41d25cafbbd7ef`，与候选清单一致；数据库迁移 `41/41`，演练库残留 0。
- 后端、Nginx、MySQL、Redis 均 active，health 为 `UP`；公网管理后台、公开商城与团队 H5 的 `version.json` 均为 `1.0.165 / 60a6e5e3fe1255bace2156f0f59089c9a341db0f / 20260924-closure-1.0.165`，各站首页和入口资源由包内脚本与本地文件逐哈希复核。公开商城两入口首页接口业务码 200；未登录订单接口 HTTP/JSON 均为 401。
- 自部署起预设关键故障日志模式命中 0。Nginx 仍有既有证书无 OCSP responder 的 `ssl_stapling` 警告，不计作本次新故障，也不以它声称告警闭环完成。

**结论：服务器 S 已完成并复核。微信开发版、体验版、真机 R 分开记录；P0 未清零，不提交审核或正式发布。**
